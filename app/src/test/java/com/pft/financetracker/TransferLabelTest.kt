package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.parser.FlowClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

/** Money moved to a card bill reads as a card bill under Transfers, not as "Credit (SBI)" under Income. */
class TransferLabelTest {
    private val sbiCard = "Dear Cardmember, Payment of Rs 15,000.00 has been credited to your SBI Card ending 1234"

    @Test fun aTransferWithNoBetterCategoryIsATransfer() {
        assertEquals(Category.TRANSFER, FlowClassifier.categoryFor(Flow.TRANSFER, Category.INCOME))
        assertEquals(Category.TRANSFER, FlowClassifier.categoryFor(Flow.TRANSFER, Category.OTHER))
        assertEquals(Category.INVESTMENT, FlowClassifier.categoryFor(Flow.TRANSFER, Category.INVESTMENT))
        assertEquals(Category.INCOME, FlowClassifier.categoryFor(Flow.INCOME, Category.INCOME))
    }

    @Test fun aCardBillWithNoPayeeIsNamedForWhatItIs() {
        assertEquals("Card bill (SBI)", FlowClassifier.nameFor(Flow.TRANSFER, sbiCard, "Credit (SBI)", "SBI"))
        // A real payee name stays.
        assertEquals("Cred", FlowClassifier.nameFor(Flow.TRANSFER, sbiCard, "Cred", "SBI"))
        // Not a card bill: unchanged.
        assertEquals("Credit (SBI)", FlowClassifier.nameFor(Flow.INCOME, "Rs 500 credited to a/c XX1234", "Credit (SBI)", "SBI"))
    }
}
