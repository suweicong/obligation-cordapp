package net.corda.examples.obligation.flows

import net.corda.core.utilities.getOrThrow
import net.corda.examples.obligation.Obligation
import net.corda.finance.POUNDS
import net.corda.testing.internal.chooseIdentity
import org.junit.Test
import kotlin.test.assertEquals

class ManyObligationsTests : ObligationTests() {

    @Test
    fun `Issue non-anonymous obligation successfully`() {
        val obligations = mutableListOf<Obligation>()

        val maxN = 202
        for(index in 1..202){
            obligations.add(Obligation(100.POUNDS, b.info.chooseIdentity(), a.info.chooseIdentity()))
        }

        // Step 1: Issue Many obligations
        val flow = IssueManyObligations.Initiator(obligations.toList())
        val result = a.startFlow(flow).getOrThrow()

        network.waitQuiescent()

        val outputStates = result.toLedgerTransaction(a.services).outputsOfType<Obligation>()
        println("outputStates.size ${outputStates.size}")
        assert(outputStates.size == maxN)

        val obligationsUUID = outputStates.map { it.linearId }

        // Step 2: Do action on many obligations
        val flow2 = ActionManyObligations.Initiator(obligationsUUID)
        val result2 = a.startFlow(flow2).getOrThrow()

        network.waitQuiescent()

        val inputs = result2.toLedgerTransaction(a.services).inputsOfType<Obligation>()
        val outputs = result2.toLedgerTransaction(a.services).outputsOfType<Obligation>()
        println("inputs.size ${inputs.size}")
        println("outputs.size ${outputs.size}")
        assert(inputs.size == outputs.size)
    }
}
