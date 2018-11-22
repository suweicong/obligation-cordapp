package flows.v2

import co.paralleluniverse.fibers.Suspendable
import com.base.ObligationDefinition
import contract.states.v2.ObligationSchemaV2
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute

@InitiatingFlow
@StartableByRPC
class QueryFlow(val stateRefs: List<StateRef>,
                val pageNumber: Int,
                val pageSize: Int) : FlowLogic<Vault.Page<ObligationDefinition>>() {
    @Suspendable
    override fun call(): Vault.Page<ObligationDefinition> {

        logger.info("stateRefs: $stateRefs")
        logger.info("pageNumber: $pageNumber")
        logger.info("pageSize: $pageSize")


        val sortAttribute = SortAttribute.Custom(ObligationSchemaV2.ObligationEntity::class.java, "amount")
        val sort = Sort.Direction.ASC
        val sorting = Sort(listOf(Sort.SortColumn(sortAttribute, sort)))
        val vaultCriteriaForReturn = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, stateRefs = stateRefs)
        val pageSpec = PageSpecification(pageNumber = pageNumber, pageSize = pageSize)

        // TODO: Putting sort in here will cause the duplicate metadata on 3.3 but works fine one 3.2
        val result = serviceHub.vaultService.queryBy<ObligationDefinition>(vaultCriteriaForReturn, pageSpec, sorting)

        logger.info("queryFlow result ${result.states.size}")
        logger.info("queryFlow result totalStatesAvailable ${result.totalStatesAvailable}")
        logger.info("queryFlow result $result")

        return result
    }


}