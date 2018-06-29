package net.corda.examples.obligation

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
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

data class Obligation(val amount: Amount<Currency>,
                      val lender: AbstractParty,
                      val borrower: AbstractParty,
                      val something : String? = null,
                      val paid: Amount<Currency> = Amount(0, amount.token),
                      override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {

    override val participants: List<AbstractParty> get() = listOf(lender, borrower)

    fun pay(amountToPay: Amount<Currency>) = copy(paid = paid + amountToPay)
    fun withNewLender(newLender: AbstractParty) = copy(lender = newLender)
    fun withoutLender() = copy(lender = NullKeys.NULL_PARTY)

    override fun toString(): String {
        val lenderString = (lender as? Party)?.name?.organisation ?: lender.owningKey.toBase58String()
        val borrowerString = (borrower as? Party)?.name?.organisation ?: borrower.owningKey.toBase58String()
        return "Obligation($linearId): $borrowerString owes $lenderString $amount and has paid $paid so far."
    }
    /** Object Relational Mapping support. */
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ObligationSchemaV1)

    /** Object Relational Mapping support. */
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        when (schema) {
            is ObligationSchemaV1 -> {
                return ObligationSchemaV1.ObligationEntity(
                        linearId = linearId.id.toString(),
                        something = something)
            }
        /** Additional schema mappings would be added here */
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}


object ObligationSchema

@CordaSerializable
object ObligationSchemaV1 : MappedSchema(schemaFamily = ObligationSchema.javaClass,
        version = 1,
        mappedTypes = listOf(ObligationEntity::class.java)) {

    @Entity
    @Table(name = "credit_note_states")
    class ObligationEntity(

            @Column(name = "linear_id")
            var linearId: String? = null,

            @Column(name = "something", nullable = false)
            var something: String? = null

    ) : PersistentState()
}

