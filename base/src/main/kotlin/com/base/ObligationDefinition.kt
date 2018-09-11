package com.base

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import java.util.*


@CordaSerializable
interface ObligationDefinition : LinearState {
    val amount: Amount<Currency>
    val lender: AbstractParty
    val borrower: AbstractParty
    val remark : String?
    val paid: Amount<Currency>

    fun pay(amountToPay: Amount<Currency>) : ObligationDefinition
    fun withNewLender(newLender: AbstractParty) : ObligationDefinition
    fun withoutLender() : ObligationDefinition
}