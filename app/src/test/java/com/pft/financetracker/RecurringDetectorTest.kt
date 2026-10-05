package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.recurring.Period
import com.pft.financetracker.domain.recurring.RecurringDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringDetectorTest {
    private val day = 86_400_000L
    private val now = 1_760_000_000_000L
    private var nextId = 1L

    private fun charge(merchant: String, paise: Long, daysAgo: Int, cat: Category = Category.ENTERTAINMENT, flow: Flow = Flow.EXPENSE) =
        Transaction(id = nextId++, amountPaise = paise, type = TransactionType.DEBIT, merchant = merchant, category = cat, timestamp = now - daysAgo * day,
            bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = flow)

    private fun detect(vararg t: Transaction) = RecurringDetector.detect(t.toList(), now)

    @Test fun netflixEveryMonthIsMonthly() {
        val r = detect(charge("NETFLIX.COM", 64_900, 75), charge("Netflix.com 8823", 64_900, 45), charge("NETFLIX COM", 64_900, 15)).single()
        assertEquals(Period.MONTHLY, r.period)
        assertEquals(64_900L, r.amountPaise)
        assertEquals(now + 15 * day, r.nextExpectedAt)
        assertEquals(64_900L, r.monthlyPaise)
        assertEquals(64_900L * 12, r.yearlyPaise)
        assertEquals(3, r.transactionIds.size)
        assertTrue(r.active)
    }

    @Test fun twoIdenticalMonthlyChargesAreEnough() {
        assertEquals(Period.MONTHLY, detect(charge("Spotify", 11_900, 40), charge("Spotify", 11_900, 10)).single().period)
    }

    @Test fun prime2YearlyIsYearly() {
        val r = detect(charge("Amazon Prime", 1_49_900, 370), charge("Amazon Prime", 1_49_900, 5)).single()
        assertEquals(Period.YEARLY, r.period)
        assertEquals(1_49_900L / 12, r.monthlyPaise)
    }

    @Test fun aWeeklyChargeIsWeekly() {
        val r = detect(charge("Cult Fit", 50_000, 21), charge("Cult Fit", 50_000, 14), charge("Cult Fit", 50_000, 7), charge("Cult Fit", 50_000, 0)).single()
        assertEquals(Period.WEEKLY, r.period)
    }

    @Test fun aPriceRiseIsFlagged() {
        val r = detect(charge("Netflix", 49_900, 75), charge("Netflix", 49_900, 45), charge("Netflix", 64_900, 15)).single()
        assertEquals(49_900L, r.priceRise!!.fromPaise)
        assertEquals(64_900L, r.priceRise!!.toPaise)
        assertEquals(64_900L, r.amountPaise)
    }

    @Test fun aSmallWobbleIsNotAPriceRise() {
        assertNull(detect(charge("Jio Fiber", 70_800, 62), charge("Jio Fiber", 70_900, 31), charge("Jio Fiber", 71_000, 1)).single().priceRise)
    }

    @Test fun irregularGroceryShoppingIsNotRecurring() {
        assertTrue(detect(charge("BigBasket", 1_23_400, 80, Category.FOOD), charge("BigBasket", 45_600, 61, Category.FOOD), charge("BigBasket", 2_10_000, 33, Category.FOOD),
            charge("BigBasket", 78_000, 30, Category.FOOD), charge("BigBasket", 99_900, 9, Category.FOOD)).isEmpty())
    }

    @Test fun aSkippedMonthStillCounts() {
        assertEquals(Period.MONTHLY, detect(charge("Hotstar", 29_900, 95), charge("Hotstar", 29_900, 65), charge("Hotstar", 29_900, 5)).single().period)
    }

    @Test fun aChargeThatStoppedLongAgoIsNotActive() {
        assertFalse(detect(charge("Zee5", 9_900, 200), charge("Zee5", 9_900, 170), charge("Zee5", 9_900, 140)).single().active)
    }

    @Test fun anAutoPayMandateIsListedFromItsFirstCharge() {
        val r = detect(charge("UPI AutoPay mandate GOOGLE ONE", 13_000, 3)).single()
        assertTrue(r.autopay)
        assertEquals(Period.UNKNOWN, r.period)
        assertNull(r.nextExpectedAt)
    }

    @Test fun oneOrdinaryChargeIsNothing() = assertTrue(detect(charge("Netflix", 64_900, 3)).isEmpty())

    @Test fun refundsTransfersAndIncomeAreIgnored() {
        assertTrue(detect(charge("SIP Axis MF", 5_00_000, 60, flow = Flow.INVESTMENT), charge("SIP Axis MF", 5_00_000, 30, flow = Flow.INVESTMENT), charge("SIP Axis MF", 5_00_000, 0, flow = Flow.INVESTMENT)).isEmpty())
    }

    @Test fun differentServicesStaySeparate() {
        val r = detect(charge("Netflix", 64_900, 45), charge("Netflix", 64_900, 15), charge("Spotify", 11_900, 40), charge("Spotify", 11_900, 10))
        assertEquals(setOf("Netflix", "Spotify"), r.map { it.merchant }.toSet())
        assertNotNull(r.first().key)
    }
}
