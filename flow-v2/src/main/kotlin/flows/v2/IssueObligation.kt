package flows.v2

import co.paralleluniverse.fibers.Suspendable
import contract.states.v2.ObligationContractV2
import contract.states.v2.ObligationContractV2.Companion.OBLIGATION_CONTRACT_ID
import contract.states.v2.ObligationV2
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.seconds
import java.util.*

object IssueObligation {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val amount: Amount<Currency>,
                    private val lender: Party,
                    private val anonymous: Boolean = true,
                    private val remark: String? = null) : ObligationBaseFlow() {

        companion object {
            object INITIALISING : Step("Performing initial steps.")
            object BUILDING : Step("Building and verifying transaction.")
            object SIGNING : Step("Signing transaction.")
            object COLLECTING : Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Step 1. Initialisation.
            progressTracker.currentStep = INITIALISING
            val obligation = if (anonymous) createAnonymousObligation() else ObligationV2(amount, lender, ourIdentity, remark)
            val ourSigningKey = obligation.borrower.owningKey

            // Step 2. Building.
            progressTracker.currentStep = BUILDING
            val utx = TransactionBuilder(firstNotary)
                    .addOutputState(obligation, OBLIGATION_CONTRACT_ID)
                    .addCommand(ObligationContractV2.Commands.Issue(), obligation.participants.map { it.owningKey })
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            // Step 3. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(utx, ourSigningKey)

            // Step 4. Get the counter-party signature.
            progressTracker.currentStep = COLLECTING
            val lenderFlow = initiateFlow(lender)
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(lenderFlow),
                    listOf(ourSigningKey),
                    COLLECTING.childProgressTracker())
            )

            // Step 5. Finalise the transaction.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        }

        @Suspendable
        private fun createAnonymousObligation(): ObligationV2 {
            val txKeys = subFlow(SwapIdentitiesFlow(lender))

            check(txKeys.size == 2) { "Something went wrong when generating confidential identities." }

            val anonymousMe = txKeys[ourIdentity] ?: throw FlowException("Couldn't create our conf. identity.")
            val anonymousLender = txKeys[lender] ?: throw FlowException("Couldn't create lender's conf. identity.")

            return ObligationV2(amount, anonymousLender, anonymousMe, remark)
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val stx = subFlow(SignTxFlowNoChecking(otherFlow))
            return waitForLedgerCommit(stx.id)
        }
    }
}
