package com.pft.financetracker.data.local

import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.BillItem
import com.pft.financetracker.domain.split.Person
import com.pft.financetracker.domain.split.Split
import com.pft.financetracker.domain.split.SplitMode
import com.pft.financetracker.domain.split.SplitShare

fun TransactionEntity.toDomain(): Transaction {
    val t = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.DEBIT)
    return Transaction(
        id = id,
        amountPaise = amountPaise,
        type = t,
        merchant = merchant,
        category = Category.fromName(category),
        timestamp = timestamp,
        bankName = bankName,
        accountRef = accountRef,
        source = runCatching { Transaction.Source.valueOf(source) }.getOrDefault(Transaction.Source.MANUAL),
        flow = Flow.fromName(flow) ?: if (t == TransactionType.CREDIT) Flow.INCOME else Flow.EXPENSE,
        note = note,
        smsHash = smsHash,
        refNumber = refNumber,
        confidence = confidence,
        needsReview = needsReview,
        originalAmountPaise = originalAmountPaise,
        userEdited = userEdited,
    )
}

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amountPaise = amountPaise,
    type = type.name,
    merchant = merchant,
    category = category.name,
    timestamp = timestamp,
    bankName = bankName,
    accountRef = accountRef,
    source = source.name,
    flow = flow.name,
    note = note,
    smsHash = smsHash,
    refNumber = refNumber,
    confidence = confidence,
    needsReview = needsReview,
    originalAmountPaise = originalAmountPaise,
    userEdited = userEdited,
)

fun BudgetEntity.toDomain() = Budget(Category.fromName(category), monthlyLimitPaise)
fun Budget.toEntity() = BudgetEntity(category.name, monthlyLimitPaise)

fun assembleSplits(splits: List<SplitEntity>, people: List<SplitPersonEntity>, shares: List<SplitShareEntity>): List<Split> {
    val peopleBy = people.groupBy { it.splitId }
    val sharesBy = shares.groupBy { it.splitId }
    return splits.map { s ->
        Split(
            id = s.id,
            title = s.title,
            totalPaise = s.totalPaise,
            date = s.date,
            mode = runCatching { SplitMode.valueOf(s.mode) }.getOrDefault(SplitMode.EQUAL),
            payerIndex = s.payerIndex,
            people = (peopleBy[s.id] ?: emptyList()).sortedBy { it.personIndex }.map { Person(it.id, it.name, it.isMe) },
            shares = (sharesBy[s.id] ?: emptyList()).sortedBy { it.personIndex }.map { SplitShare(it.id, it.personIndex, it.amountPaise, it.settledPaise) },
            linkedTransactionId = s.linkedTransactionId,
            note = s.note,
            createdAt = s.createdAt,
        )
    }
}

fun SplitItemEntity.toDomain() = BillItem(id, name, quantity, pricePaise, assignedTo.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet())
fun BillItem.toEntity(splitId: Long) = SplitItemEntity(0, splitId, name, quantity, pricePaise, assignedTo.joinToString(","))
