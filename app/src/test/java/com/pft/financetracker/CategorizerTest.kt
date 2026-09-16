package com.pft.financetracker

import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {
    @Test fun food() = assertEquals(Category.FOOD, Categorizer.categorize("Swiggy", TransactionType.DEBIT))
    @Test fun shopping() = assertEquals(Category.SHOPPING, Categorizer.categorize("AMAZON PAY INDIA", TransactionType.DEBIT))
    @Test fun transport() = assertEquals(Category.TRANSPORT, Categorizer.categorize("Uber India", TransactionType.DEBIT))
    @Test fun billsLongestWins() = assertEquals(Category.BILLS, Categorizer.categorize("Tata Power Delhi", TransactionType.DEBIT))
    @Test fun entertainment() = assertEquals(Category.ENTERTAINMENT, Categorizer.categorize("NETFLIX.COM", TransactionType.DEBIT))
    @Test fun creditIsIncome() = assertEquals(Category.INCOME, Categorizer.categorize("Acme Corp Salary", TransactionType.CREDIT))
    @Test fun unknownIsOther() = assertEquals(Category.OTHER, Categorizer.categorize("Ramesh Kumar", TransactionType.DEBIT))
    @Test fun shortKeywordNeedsWordBoundary() = assertEquals(Category.OTHER, Categorizer.categorize("Vegasoft Solutions", TransactionType.DEBIT))
}
