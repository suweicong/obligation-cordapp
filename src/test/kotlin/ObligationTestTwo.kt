import com.base.ObligationDefinition
import flows.v2.QueryFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import org.junit.Test

class TestTwoMixtureOfStates : BaseTest() {

    @Test
    fun `Create Obligation`() {
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)
        network.waitQuiescent()


        var firstRound: List<StateAndRef<ObligationDefinition>> = emptyList()

        // 1. Get the state and ref from the vault.
        a.transaction {
            val results = a.services.vaultService.queryBy<ObligationDefinition>()
            firstRound = results.states

            println("firstRound ${results.states.size}")
            println("firstRound ${results.totalStatesAvailable}")
            println("firstRound $results")
        }

        // 2. Query the state ref with page number/size and sorting.
        val flow = QueryFlow(firstRound.map { it.ref }, 1, 10)
        val flowResult = a.startFlow(flow).getOrThrow()

        // I should get 10 results here, but you can see there are duplicate metadata in the vault.page
        println("flowResult ${flowResult.states.size}")
        println("flowResult ${flowResult.totalStatesAvailable}")
        println("flowResult $flowResult")
    }
}