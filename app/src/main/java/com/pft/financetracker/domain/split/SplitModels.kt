package com.pft.financetracker.domain.split

import com.pft.financetracker.domain.model.Money

enum class SplitMode(val label: String) {
    EQUAL("Equal"),
    SHARES("Shares"),
    CUSTOM("Custom amounts"),
    BY_ITEM("By item"),
}

data class Person(val id: Long = 0, val name: String, val isMe: Boolean = false)

/** One line on the bill. [assignedTo] are person indices (into the people list) sharing this item. */
data class BillItem(
    val id: Long = 0,
    val name: String,
    val quantity: Int = 1,
    val pricePaise: Long,
    val assignedTo: Set<Int> = emptySet(),
)

/** Extra amounts on top of the items. Positive tax/tip/service; discount is subtracted. */
data class BillExtras(
    val taxPaise: Long = 0,
    val servicePaise: Long = 0,
    val tipPaise: Long = 0,
    val discountPaise: Long = 0,
) {
    val netPaise: Long get() = taxPaise + servicePaise + tipPaise - discountPaise
}

data class PersonShare(val personIndex: Int, val amountPaise: Long) {
    val amount: Double get() = Money.toRupees(amountPaise)
}

data class SplitResult(val totalPaise: Long, val shares: List<PersonShare>) {
    init { require(shares.sumOf { it.amountPaise } == totalPaise) { "shares must add up to total" } }
}

data class Split(
    val id: Long = 0,
    val title: String,
    val totalPaise: Long,
    val date: Long,
    val mode: SplitMode,
    val payerIndex: Int,
    val people: List<Person>,
    val shares: List<SplitShare>,
    val linkedTransactionId: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val myIndex: Int get() = people.indexOfFirst { it.isMe }
    val myShare: SplitShare? get() = shares.firstOrNull { it.personIndex == myIndex }
    val iPaid: Boolean get() = payerIndex == myIndex
    val outstandingPaise: Long get() = shares.filter { it.personIndex != payerIndex }.sumOf { it.amountPaise - it.settledPaise }
    val settled: Boolean get() = outstandingPaise <= 0
}

data class SplitShare(val id: Long = 0, val personIndex: Int, val amountPaise: Long, val settledPaise: Long = 0) {
    val remainingPaise: Long get() = amountPaise - settledPaise
}

/** Aggregated "who owes whom" across every open split. Positive = they owe you; negative = you owe them. */
data class Balance(val name: String, val netPaise: Long)
