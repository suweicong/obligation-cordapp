package net.corda.examples.obligation.flows

import com.andreapivetta.kolor.yellow
import net.corda.core.node.services.queryBy
import net.corda.examples.obligation.Obligation
import net.corda.finance.POUNDS
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit


class RedeemObligationTests : ObligationTests() {

    @Test
    fun `Redeem a HTLC obligation with correct secret, then succeed`() {

        val future = Instant.now().plus(1L, ChronoUnit.DAYS)
        try {
            val stx = issueObligation(
                    a,
                    b,
                    1000.POUNDS,
                    anonymous = false,
                    remark = "validString",
                    releaseTime = future,
                    secret = "correctSecret")

            network.waitQuiescent()

            val linearId = stx.tx.outputsOfType<Obligation>().single().linearId

            // Self issue cash.
            selfIssueCash(a, 1500.POUNDS)
            network.waitQuiescent()

            val redeemStx = redeemObligation(linearId, b, false, "correctSecret")

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
    fun `Redeem a HTLC obligation with incorrect secret, then fails`() {

        val future = Instant.now().plus(1L, ChronoUnit.DAYS)
        try {
            val stx = issueObligation(
                    a,
                    b,
                    1000.POUNDS,
                    anonymous = false,
                    remark = "validString",
                    releaseTime = future,
                    secret = "wrongSecret")

            network.waitQuiescent()

            val linearId = stx.tx.outputsOfType<Obligation>().single().linearId

            // Self issue cash.
            selfIssueCash(a, 1500.POUNDS)
            network.waitQuiescent()

            redeemObligation(linearId, b, false, "wrongSecret")
            network.waitQuiescent()

        } catch (ex: Exception) {
            assert(ex.message!!.contains("Secret must be correct"))

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
    fun `Redeem a HTLC obligation with releaseTime exceeded, then fails`() {

        val future = Instant.now().minus(1L, ChronoUnit.DAYS)
        try {
            val stx = issueObligation(
                    a,
                    b,
                    1000.POUNDS,
                    anonymous = false,
                    remark = "validString",
                    releaseTime = future,
                    secret = "correctSecret")

            network.waitQuiescent()

            val linearId = stx.tx.outputsOfType<Obligation>().single().linearId

            // Self issue cash.
            selfIssueCash(a, 1500.POUNDS)
            network.waitQuiescent()

        } catch (ex: Exception) {
            assert(ex.message!!.contains("Time must be before releaseTime"))
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