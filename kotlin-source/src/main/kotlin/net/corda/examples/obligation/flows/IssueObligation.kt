package net.corda.examples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.examples.obligation.Obligation
import net.corda.examples.obligation.ObligationContract
import net.corda.examples.obligation.ObligationContract.Companion.OBLIGATION_CONTRACT_ID
import org.eclipse.jetty.security.IdentityService
import java.util.*

object IssueObligation {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val amount: Amount<Currency>,
                    private val lender: Party,
                    private val anonymous: Boolean = true,
                    private val remark : String? = null) : ObligationBaseFlow() {

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

            // We create a composite key that requires signatures from ourselves
            // and one of the other parties (each weight is one and the threshold is 2)
            val compositePubKey = CompositeKey.Builder()
                    .addKey(ourIdentity.owningKey, weight = 1)
                    .addKey(lender.owningKey, weight = 1)
                    .build(2)

            val compositeParty = AnonymousParty(compositePubKey)
            val compositeKey = compositePubKey as CompositeKey
            val leafParties = compositeKey.leafKeys
            val hasMyKey = leafParties.mapNotNull { serviceHub.identityService.partyFromKey(it) }.isNotEmpty()

            val compositeObligation = Obligation(amount, compositeParty, compositeParty, remark = remark)
            val ourSigningKey = compositeObligation.borrower.owningKey

            // Step 2. Building.
            progressTracker.currentStep = BUILDING
            val utx = TransactionBuilder(firstNotary)
                    .addOutputState(compositeObligation, OBLIGATION_CONTRACT_ID)
                    .addCommand(ObligationContract.Commands.Issue(), compositeObligation.participants.map { it.owningKey })
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            // Step 3. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(utx, ourSigningKey)

            // Step 4. Get the counter-party signature.
            progressTracker.currentStep = COLLECTING
            // We gather the signatures. Note that we cannot use
            // `CollectSignaturesFlow` because:
            // * The `CompositeKey` does not correspond to a specific
            //   counterparty
            // * The counterparty may refuse to sign
            val sessions = initiateFlow(lender)
            // We filter out any responses that are not
            // `TransactionSignature`s (i.e. refusals to sign).
            val signatures = sessions.sendAndReceive<TransactionSignature>(ptx).unwrap { it }
            val fullStx = ptx.withAdditionalSignatures(listOf(signatures))

            // Step 5. Finalise the transaction.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityCompositeFlow(fullStx, FINALISING.childProgressTracker()))
        }

        @Suspendable
        private fun createAnonymousObligation(): Obligation {
            val txKeys = subFlow(SwapIdentitiesFlow(lender))

            check(txKeys.size == 2) { "Something went wrong when generating confidential identities." }

            val anonymousMe = txKeys[ourIdentity] ?: throw FlowException("Couldn't create our conf. identity.")
            val anonymousLender = txKeys[lender] ?: throw FlowException("Couldn't create lender's conf. identity.")

            return Obligation(amount, anonymousLender, anonymousMe, remark = remark)
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val otherFlow: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {
            val partStx = otherFlow.receive<SignedTransaction>().unwrap { it}
            val sig = serviceHub.createSignature(partStx)
            otherFlow.send(sig)
        }
    }
}
