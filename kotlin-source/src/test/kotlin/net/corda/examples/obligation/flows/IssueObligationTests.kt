package net.corda.examples.obligation.flows

import net.corda.core.node.services.queryBy
import net.corda.examples.obligation.Obligation
import net.corda.finance.POUNDS
import org.junit.Test
import com.andreapivetta.kolor.yellow

class IssueObligationTests : ObligationTests() {

    @Test
    fun `Issue non-anonymous obligation successfully with string`() {
        try {
            val stx = issueObligation(a, b, 1000.POUNDS, anonymous = false, something = "validString")
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
            val stx = issueObligation(a, b, 1000.POUNDS, anonymous = false, something = null)
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
