package com.pft.financetracker.data.local

import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    amount = amount,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.DEBIT),
    merchant = merchant,
    category = Category.fromName(category),
    timestamp = timestamp,
    bankName = bankName,
    accountRef = accountRef,
    source = runCatching { Transaction.Source.valueOf(source) }.getOrDefault(Transaction.Source.MANUAL),
    note = note,
    smsHash = smsHash,
    confidence = confidence,
    needsReview = needsReview,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amount = amount,
    type = type.name,
    merchant = merchant,
    category = category.name,
    timestamp = timestamp,
    bankName = bankName,
    accountRef = accountRef,
    source = source.name,
    note = note,
    smsHash = smsHash,
    confidence = confidence,
    needsReview = needsReview,
)

fun BudgetEntity.toDomain() = Budget(Category.fromName(category), monthlyLimit)
fun Budget.toEntity() = BudgetEntity(category.name, monthlyLimit)
