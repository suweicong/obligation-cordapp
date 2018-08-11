package net.corda.examples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.seconds
import net.corda.examples.obligation.Obligation
import net.corda.examples.obligation.ObligationContract
import net.corda.examples.obligation.ObligationContract.Companion.OBLIGATION_CONTRACT_ID

object IssueManyObligations {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val obligations: List<Obligation>) : ObligationBaseFlow() {

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
            val ourSigningKey = ourIdentity.owningKey

            // Step 2. Building.
            progressTracker.currentStep = BUILDING
            val utx = TransactionBuilder(firstNotary)
                    .withItems(*obligations.map { StateAndContract(it, OBLIGATION_CONTRACT_ID) }.toTypedArray())
                    .addCommand(ObligationContract.Commands.Nothing(), obligations.flatMap {  it.participants.map { it.owningKey } })

            // Step 3. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(utx, ourSigningKey)

            // Step 4. Get the counter-party signature.
            progressTracker.currentStep = COLLECTING
            val lenderFlow = initiateFlow(resolveIdentity(obligations.first().lender))
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
