package com.pft.financetracker

import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import com.pft.financetracker.domain.parser.TemplateLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateLearnerTest {
    private val first = "Your a/c XX1234 is debited for INR 1,250.00 at ZETA MART on 03-Oct-26. Avl Bal INR 5,000.00"
    private val next = "Your a/c XX1234 is debited for INR 89.50 at CORNER SHOP on 05-Nov-26. Avl Bal INR 4,910.50"
    private val learned = TemplateLearner.learn("VM-ZETABK-S", first, 1_25_000, TransactionType.DEBIT, "ZETA MART")!!

    @Test fun aConfirmedMessageTeachesTheNextOneFromTheSameSender() {
        val hit = TemplateLearner.apply(learned, "AX-ZETABK-T", next)!!
        assertEquals(8_950L, hit.amountPaise)
        assertEquals("CORNER SHOP", hit.merchant)
        assertEquals(TransactionType.DEBIT, hit.type)
    }

    @Test fun anotherSenderIsNotMatched() = assertNull(TemplateLearner.apply(learned, "VM-OTHRBK-S", next))

    @Test fun changedWordingIsNotMatched() {
        assertNull(TemplateLearner.apply(learned, "VM-ZETABK-S", "Your a/c XX1234 will be debited for INR 89.50 at CORNER SHOP on 05-Nov-26. Avl Bal INR 4,910.50"))
        assertNull(TemplateLearner.apply(learned, "VM-ZETABK-S", "Your a/c XX1234 is credited for INR 89.50 at CORNER SHOP on 05-Nov-26. Avl Bal INR 4,910.50"))
    }

    @Test fun theAmountIsNeverTakenFromAMaskedAccountNumber() {
        // The account number happens to contain the same digits as the amount.
        val t = TemplateLearner.learn("VM-ZETABK", "A/c XX1250 debited Rs 1250 to ACME", 1_25_000, TransactionType.DEBIT, "ACME")!!
        assertFalse(t.skeleton.contains("XX{AMT}"))
        assertEquals(30_000L, TemplateLearner.apply(t, "VM-ZETABK", "A/c XX1250 debited Rs 300 to ACME")!!.amountPaise)
    }

    @Test fun nothingIsLearnedWhenTheAmountIsNotInTheMessage() =
        assertNull(TemplateLearner.learn("VM-ZETABK", "Txn done at ZETA MART", 1_25_000, TransactionType.DEBIT, "ZETA MART"))

    @Test fun aMerchantThatIsNotInTheTextIsLeftOut() {
        val t = TemplateLearner.learn("VM-ZETABK", "Debit of Rs 400.00 from a/c XX9876 on 01/10/26", 40_000, TransactionType.DEBIT, "Groceries")!!
        val hit = TemplateLearner.apply(t, "VM-ZETABK", "Debit of Rs 75.00 from a/c XX9876 on 02/10/26")!!
        assertEquals(7_500L, hit.amountPaise)
        assertNull(hit.merchant)
    }

    @Test fun theParserUsesALearnedTemplateBeforeItsOwnRules() {
        // On its own the parser cannot read this shape and sends it to review.
        val odd = "Zeta: INR 89.50 gone, CORNER SHOP, a/c XX1234, 05-Nov-26"
        assertTrue(SmsParser().parse(SmsMessage("VM-ZETABK-S", odd, 1L)) !is ParseResult.Success)
        val t = TemplateLearner.learn("VM-ZETABK-S", "Zeta: INR 1,250.00 gone, ZETA MART, a/c XX1234, 03-Oct-26", 1_25_000, TransactionType.DEBIT, "ZETA MART")
        assertNotNull(t)
        val r = SmsParser(templates = { listOf(t!!) }).parse(SmsMessage("VM-ZETABK-S", odd, 1L))
        val tx = (r as ParseResult.Success).transaction
        assertEquals(8_950L, tx.amountPaise); assertEquals("CORNER SHOP", tx.merchant); assertEquals(TransactionType.DEBIT, tx.type)
    }

    @Test fun ignoreRulesStillWinOverATemplate() {
        val t = TemplateLearner.learn("VM-ZETABK-S", "Zeta: INR 1,250.00 gone, ZETA MART, a/c XX1234, 03-Oct-26", 1_25_000, TransactionType.DEBIT, "ZETA MART")!!
        val r = SmsParser(templates = { listOf(t) }).parse(SmsMessage("VM-ZETABK-S", "Zeta: INR 1,250.00 gone, OTP 123456, a/c XX1234, 03-Oct-26", 1L))
        assertTrue(r is ParseResult.Ignored)
    }
}
