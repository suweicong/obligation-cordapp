package net.corda.examples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.seconds
import net.corda.examples.obligation.Obligation
import net.corda.examples.obligation.ObligationContract
import net.corda.examples.obligation.ObligationContract.Companion.OBLIGATION_CONTRACT_ID

object ActionManyObligations {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val obligationsUUID: List<UniqueIdentifier>) : ObligationBaseFlow() {

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

            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                    null,
                    obligationsUUID,
                    Vault.StateStatus.UNCONSUMED,
                    null)

            val obligationsIn = serviceHub.vaultService.queryBy<Obligation>(
                    queryCriteria,
                    paging = PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE)).states

            logger.info("obligationsIn.size ${obligationsIn.size}")

            // Step 2. Building.
            progressTracker.currentStep = BUILDING
            val utx = TransactionBuilder(firstNotary)
                    .withItems(*obligationsIn.toTypedArray())
                    .withItems(*obligationsIn.map { StateAndContract(it.state.data, OBLIGATION_CONTRACT_ID) }.toTypedArray())
                    .addCommand(ObligationContract.Commands.Nothing(), obligationsIn.flatMap { it.state.data.participants.map { it.owningKey } })

            // Step 3. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(utx, ourSigningKey)

            // Step 4. Get the counter-party signature.
            progressTracker.currentStep = COLLECTING
            val lenderFlow = initiateFlow(resolveIdentity(obligationsIn.first().state.data.lender))

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
}

@InitiatedBy(ActionManyObligations.Initiator::class)
class Responder(private val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val stx = subFlow(SignTxFlowNoChecking(otherFlow))
        return waitForLedgerCommit(stx.id)
    }
}
