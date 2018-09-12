package net.corda.examples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.core.crypto.sha256
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.*
import net.corda.examples.obligation.Obligation
import net.corda.examples.obligation.ObligationContract
import net.corda.examples.obligation.ObligationContract.Companion.OBLIGATION_CONTRACT_ID
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance


/**
 * This flow assumes that the borrower (locker) owes the lender (lockee) some money.
 * In contrast to SettleObligation where borrower initiates the settlement of cash to the lender,
 * But here, the lender can prematurely redeem the obligation (like a voucher) to get cash from the borrower early
 * AS LONG AS, lender can provide the secret before the releaseTime
 *
 * Assuming no malicious termination, they both end the flow being in possession of a valid, signed transaction
 * that represents an atomic asset swap.
 *
 * Note that it's the *lender/lockee* who initiates contact with the borrower (locker), not vice-versa as you might imagine.
 */


@CordaSerializable
data class SecretInfo(
        val obligation: StateAndRef<Obligation>,
        val secret: OpaqueBytes)


object RedeemObligationFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val linearId: UniqueIdentifier,
                    private val secret: String,
                    private val anonymous: Boolean = true) : ObligationBaseFlow() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object AWAITING_PROPOSAL : ProgressTracker.Step("Awaiting transaction proposal")
            object VERIFYING_AND_SIGNING : ProgressTracker.Step("Verifying and signing transaction proposal") {
                override fun childProgressTracker() = SignTransactionFlow.tracker()
            }

            fun tracker() = ProgressTracker(AWAITING_PROPOSAL, VERIFYING_AND_SIGNING)
        }


        @Suspendable
        override fun call(): SignedTransaction {
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = AWAITING_PROPOSAL
            val inputObligation = getObligationByLinearId(linearId)
            val secretBytes = OpaqueBytes(secret.toByteArray())
            val hello = SecretInfo(inputObligation, secretBytes)

            val target = serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputObligation.state.data.borrower)
            val otherSideSession = initiateFlow(target)
            otherSideSession.send(hello)

            // Verify and sign the transaction.
            progressTracker.currentStep = VERIFYING_AND_SIGNING

            // Sync identities to ensure we know all of the identities involved in the transaction we're about to
            // be asked to sign
            subFlow(IdentitySyncFlow.Receive(otherSideSession))

            val signTransactionFlow = object : SignTransactionFlow(otherSideSession, VERIFYING_AND_SIGNING.childProgressTracker()) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // Verify that we know who all the participants in the transaction are
                    val states: Iterable<ContractState> = serviceHub.loadStates(stx.tx.inputs.toSet()).map { it.state.data } + stx.tx.outputs.map { it.data }
                    states.forEach { state ->
                        state.participants.forEach { anon ->
                            require(serviceHub.identityService.wellKnownPartyFromAnonymous(anon) != null) {
                                "Transaction state $state involves unknown participant $anon"
                            }
                        }
                    }

                    val command = stx.tx.commands.single { it.value is ObligationContract.Commands.Redeem }.value as ObligationContract.Commands.Redeem
                    require(command.secret == secretBytes) { "Secret must be what we proposed." }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return waitForLedgerCommit(txId)
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(private val otherFlow: FlowSession) : ObligationBaseFlow() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object PREPARATION : ProgressTracker.Step("Obtaining IOU from vault.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(PREPARATION, BUILDING, SIGNING, COLLECTING, FINALISING)
        }


        @Suspendable
        override fun call(): SignedTransaction {

            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = PREPARATION
            val hello = otherFlow.receive<SecretInfo>().unwrap { it }
            val inputObligationStateRef = hello.obligation
            val latestObligationStateRef = getObligationByLinearId(inputObligationStateRef.state.data.linearId)
            val latestObligationState = latestObligationStateRef.state.data

            require(inputObligationStateRef == latestObligationStateRef) { "Propose input obligation must be unconsumed." }

            // Stage 2. Resolve the lender and borrower identity if the obligation is anonymous.
            val borrowerIdentity = resolveIdentity(latestObligationState.borrower)
            val lenderIdentity = resolveIdentity(latestObligationState.lender)

            // Stage 3. This flow can only be initiated by the current lender.
            check(lenderIdentity == otherFlow.counterparty) {
                throw FlowException("Redeem Obligation flow must be initiated by the lender.")
            }

            // Stage 4. Check we have enough cash to settle the requested amount.
            val amount = latestObligationState.amount
            val paid = latestObligationState.paid
            val cashBalance = serviceHub.getCashBalance(amount.token)
            val amountLeftToSettle = amount - paid

            check(cashBalance.quantity > 0L) {
                throw FlowException("Borrower has no ${amount.token} to settle.")
            }
            check(cashBalance >= amount) {
                throw FlowException("Borrower has only $cashBalance but needs $amount to settle.")
            }
            check(amountLeftToSettle >= amount) {
                throw FlowException("There's only $amountLeftToSettle left to settle but you pledged $amount.")
            }

            // Stage 5. Create a settle command.
            val redeemCommand = Command(
                    ObligationContract.Commands.Redeem(hello.secret),
                    inputObligationStateRef.state.data.participants.map { it.owningKey })

            // Stage 6. Create a transaction builder. Add the settle command and input obligation.
            progressTracker.currentStep = BUILDING
            val timeWindow = TimeWindow.withTolerance(serviceHub.clock.instant(), 30.seconds)
            val builder = TransactionBuilder(firstNotary)
                    .addInputState(latestObligationStateRef)
                    .addCommand(redeemCommand)
                    .setTimeWindow(timeWindow)

            // Stage 7. Get some cash from the vault and add a spend to our transaction builder.
            // We pay cash to the lenders obligation key.
            val lenderPaymentKey = latestObligationState.lender
            val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, builder, amount, lenderPaymentKey)


            // Stage 8. Only add an output obligation state if the obligation has not been fully settled.
            val amountRemaining = amountLeftToSettle - amount
            if (amountRemaining > Amount.zero(amount.token)) {
                val outputObligation = latestObligationState.pay(amount)
                builder.addOutputState(outputObligation, OBLIGATION_CONTRACT_ID)
            }

            // Stage 9. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, cashSigningKeys + latestObligationState.borrower.owningKey)


            // Stage 10. Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            subFlow(IdentitySyncFlow.Send(otherFlow, ptx.tx))
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(otherFlow),
                    cashSigningKeys + latestObligationState.borrower.owningKey,
                    COLLECTING.childProgressTracker()))

            // Stage 11. Finalize the transaction.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        }
    }
}