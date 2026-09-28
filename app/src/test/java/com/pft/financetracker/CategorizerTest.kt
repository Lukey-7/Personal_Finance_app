package com.pft.financetracker

import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.screens.dashboard.reviewLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    @Test fun cashbackIsNotAtm() = assertEquals(Category.INCOME, Categorizer.categorize("CASHBACK RECEIVED", TransactionType.CREDIT))
    @Test fun cashBackTwoWordsIsNotAtm() = assertEquals(Category.INCOME, Categorizer.categorize("Cash back credited", TransactionType.CREDIT))
    @Test fun cashbackStatementRowIsRefund() {
        val row = StatementInterpreter.toRow(0L, 5000, TransactionType.CREDIT, "CASHBACK RECEIVED", null, null, null, 0)
        assertNotEquals(Category.ATM, row.category)
        assertEquals(Flow.REFUND, row.flow)
    }
    @Test fun atmWithdrawal() = assertEquals(Category.ATM, Categorizer.categorize("ATM WDL HDFC BANK", TransactionType.DEBIT))
    @Test fun cashWithdrawal() = assertEquals(Category.ATM, Categorizer.categorize("CASH WITHDRAWAL SELF", TransactionType.DEBIT))
    @Test fun plainCash() = assertEquals(Category.ATM, Categorizer.categorize("Cash", TransactionType.DEBIT))
    @Test fun reviewBannerIsSourceNeutral() {
        assertEquals("1 item needs review", reviewLine(1))
        assertEquals("3 items need review", reviewLine(3))
    }
}
