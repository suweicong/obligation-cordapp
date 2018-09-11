package contract.states.v2

import com.base.ObligationDefinition
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.toBase58String
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

data class ObligationV2(override val amount: Amount<Currency>,
                        override val lender: AbstractParty,
                        override val borrower: AbstractParty,
                        override val remark : String? = null,
                        override val paid: Amount<Currency> = Amount(0, amount.token),
                      override val linearId: UniqueIdentifier = UniqueIdentifier()) : ObligationDefinition, QueryableState {

    override val participants: List<AbstractParty> get() = listOf(lender, borrower)

    override fun pay(amountToPay: Amount<Currency>) = copy(paid = paid + amountToPay)
    override fun withNewLender(newLender: AbstractParty) = copy(lender = newLender)
    override fun withoutLender() = copy(lender = NullKeys.NULL_PARTY)

    override fun toString(): String {
        val lenderString = (lender as? Party)?.name?.organisation ?: lender.owningKey.toBase58String()
        val borrowerString = (borrower as? Party)?.name?.organisation ?: borrower.owningKey.toBase58String()
        return "Obligation($linearId): $borrowerString owes $lenderString $amount and has paid $paid so far."
    }
    /** Object Relational Mapping support. */
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ObligationSchemaV2)

    /** Object Relational Mapping support. */
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        when (schema) {
            is ObligationSchemaV2 -> {
                return ObligationSchemaV2.ObligationEntity(
                        linearId = linearId.id.toString(),
                        amount = amount.quantity,
                        remark = remark)
            }
        /** Additional schema mappings would be added here */
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}


object ObligationSchema

@CordaSerializable
object ObligationSchemaV2 : MappedSchema(schemaFamily = ObligationSchema.javaClass,
        version = 1,
        mappedTypes = listOf(ObligationEntity::class.java)) {

    @Entity
    @Table(name = "obligation_v2")
    class ObligationEntity(

            @Column(name = "linear_id")
            var linearId: String? = null,

            @Column(name = "amount")
            var amount: Long? = null,

            @Column(name = "remark")
            var remark: String? = null

    ) : PersistentState()
}

