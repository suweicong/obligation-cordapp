import com.base.ObligationDefinition
import net.corda.core.node.services.queryBy
import net.corda.finance.POUNDS
import org.junit.Test

class TestTwoMixtureOfStates : BaseTest() {

    @Test
    fun `Create Obligation`() {
        issueObligationV1(a, b, 1000.POUNDS, anonymous = false, remark = null)
        issueObligationV2(a, b, 1000.POUNDS, anonymous = false, remark = null)

        a.transaction {
            val results = a.services.vaultService.queryBy<ObligationDefinition>()
            println(results)


        }

    }
}