package net.corda.examples.obligation.flows

import com.andreapivetta.kolor.yellow
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.Try
import net.corda.examples.obligation.Obligation
import net.corda.finance.POUNDS
import org.junit.Test

class IssueObligationTests : ObligationTests() {

    @Test
    fun `Issue non-anonymous obligation successfully with non null remark`() {

        // Throw null pointer
        val result = Try.on {
            issueObligation(a, b, 1000.POUNDS, anonymous = false, remark = null)

        }
        network.waitQuiescent()

        assert(result.isFailure)

        //  Can issue successfully
        val result2 = Try.on {
            issueObligation(a, b, 1000.POUNDS, anonymous = false, remark = "Valid")
            network.waitQuiescent()
        }

        assert(result2.isSuccess)

    }

    @Test
    fun `Issue non-anonymous obligation successfully with string`() {
        try {
            val stx = issueObligation(a, b, 1000.POUNDS, anonymous = false, remark = "validString")
            network.waitQuiescent()

        } finally {
            a.transaction {
                val result = a.services.vaultService.queryBy<Obligation>().states
                println("A vault query unconsumed : $result".yellow())
            }

            b.transaction {
                val result = b.services.vaultService.queryBy<Obligation>().states
                println("B vault query unconsumed : $result".yellow())
            }
        }
    }

    @Test
    fun `Issue non-anonymous obligation successfully with null`() {
        try {
            val stx = issueObligation(a, b, 1000.POUNDS, anonymous = false, remark = null)
            network.waitQuiescent()

        } finally {
            a.transaction {
                val result = a.services.vaultService.queryBy<Obligation>().states
                println("A vault query unconsumed : $result".yellow())
            }

            b.transaction {
                val result = b.services.vaultService.queryBy<Obligation>().states
                println("B vault query unconsumed : $result".yellow())
            }
        }
    }
}
