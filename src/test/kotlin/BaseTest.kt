
import flows.v1.IssueObligation
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import java.util.*

/**
 * A base class to reduce the boilerplate when writing obligation flow tests.
 */
abstract class BaseTest {
    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                listOf("com.base",
                        "contract.states.v1",
                        "contract.states.v2",
                        "flows.v1",
                        "flows.v2",
                        "net.corda.finance.schemas"),
                threadPerNode = true)

        a = network.createNode()
        b = network.createNode()
        c = network.createNode()
        val nodes = listOf(a, b, c)

    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    protected fun issueObligationV1(borrower: StartedMockNode,
                                  lender: StartedMockNode,
                                  amount: Amount<Currency>,
                                  anonymous: Boolean = true,
                                  remark : String? = null
    ): net.corda.core.transactions.SignedTransaction {
        val lenderIdentity = lender.info.chooseIdentity()
        val flow = flows.v1.IssueObligation.Initiator(amount, lenderIdentity, anonymous, remark)
        return borrower.startFlow(flow).getOrThrow()
    }

    protected fun issueObligationV2(borrower: StartedMockNode,
                                    lender: StartedMockNode,
                                    amount: Amount<Currency>,
                                    anonymous: Boolean = true,
                                    remark : String? = null
    ): net.corda.core.transactions.SignedTransaction {
        val lenderIdentity = lender.info.chooseIdentity()
        val flow = flows.v2.IssueObligation.Initiator(amount, lenderIdentity, anonymous, remark)
        return borrower.startFlow(flow).getOrThrow()
    }
}