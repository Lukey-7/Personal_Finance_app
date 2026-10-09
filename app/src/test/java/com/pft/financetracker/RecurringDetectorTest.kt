package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.recurring.Period
import com.pft.financetracker.domain.recurring.RecurringBook
import com.pft.financetracker.domain.recurring.RecurringDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurringDetectorTest {
    private val day = 86_400_000L
    private val now = 1_760_000_000_000L
    private var nextId = 1L

    private fun chargeAt(
        merchant: String, paise: Long, at: Long, cat: Category = Category.ENTERTAINMENT, flow: Flow = Flow.EXPENSE,
        kind: CounterpartyKind? = null,
    ) = Transaction(id = nextId++, amountPaise = paise, type = TransactionType.DEBIT, merchant = merchant, category = cat, timestamp = at,
        bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = flow, counterpartyKind = kind)

    private fun charge(merchant: String, paise: Long, daysAgo: Int, cat: Category = Category.ENTERTAINMENT, flow: Flow = Flow.EXPENSE, kind: CounterpartyKind? = null) =
        chargeAt(merchant, paise, now - daysAgo * day, cat, flow, kind)

    private fun detect(vararg t: Transaction) = RecurringDetector.detect(t.toList(), now)

    @Test fun netflixEveryMonthIsMonthly() {
        val r = detect(charge("NETFLIX.COM", 64_900, 75), charge("Netflix.com 8823", 64_900, 45), charge("NETFLIX COM", 64_900, 15)).single()
        assertEquals(Period.MONTHLY, r.period)
        assertEquals(64_900L, r.amountPaise)
        assertEquals(RecurringDetector.nextAfter(now - 15 * day, Period.MONTHLY), r.nextExpectedAt)
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
        assertTrue(r.active)
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

    @Test fun theSameServiceByUpiAndByCardIsOneSubscription() {
        val r = detect(charge("netflix.upi@icici", 64_900, 75), charge("NETFLIX.COM", 64_900, 45), charge("Netflix Entertainment Services India Pvt Ltd", 64_900, 15))
        assertEquals(1, r.size)
        assertEquals(3, r.single().transactionIds.size)
    }

    @Test fun differentProductsOfOneCompanyStaySeparate() {
        assertEquals(RecurringDetector.merchantKey("spotify@ybl"), RecurringDetector.merchantKey("SPOTIFY"))
        assertTrue(RecurringDetector.merchantKey("Amazon Prime") != RecurringDetector.merchantKey("Amazon Pay"))
        assertNotEquals(RecurringDetector.merchantKey("YouTube Premium"), RecurringDetector.merchantKey("Google Play"))
        assertNotEquals(RecurringDetector.merchantKey("Google One"), RecurringDetector.merchantKey("Google Play"))
    }

    // Precision: what is not a subscription.

    @Test fun paymentsToAPersonAreNotSubscriptions() {
        val maid = listOf(65, 35, 5).map { charge("Sunita", 3_00_000, it, Category.OTHER, kind = CounterpartyKind.PERSON) }
        val friend = listOf(40, 10).map { charge("Rahul Sharma", 50_000, it, Category.OTHER, kind = CounterpartyKind.PERSON) }
        assertTrue(RecurringDetector.detect(maid + friend, now).isEmpty())
    }

    @Test fun anAutoPayToAPersonIsStillListed() {
        val r = detect(charge("UPI AutoPay Mandate Ravi Kumar", 1_00_000, 4, Category.OTHER, kind = CounterpartyKind.PERSON))
        assertEquals(1, r.size)
    }

    @Test fun unnamedPaymentsAndPaymentAppHandlesAreNotPooled() {
        val unnamed = listOf(65, 35, 5).map { charge("Payment (HDFC Bank)", 50_000, it, Category.OTHER) }
        val gpay = listOf(64, 34, 4).map { charge("gpay-11223344@okbizaxis", 20_000, it, Category.OTHER) }
        val bharatpe = listOf(63, 33, 3).map { charge("bharatpe.9000012345@fbpe", 15_000, it, Category.OTHER) }
        assertTrue(RecurringDetector.detect(unnamed + gpay + bharatpe, now).isEmpty())
    }

    @Test fun loansAndInsuranceBelongInBills() {
        val emi = listOf(65, 35, 5).map { charge("NACH DR BAJAJ FINANCE", 12_34_500, it, Category.BILLS) }
        val policy = listOf(370, 5).map { charge("HDFC Life Insurance", 25_00_000, it, Category.BILLS) }
        assertTrue(RecurringDetector.detect(emi + policy, now).isEmpty())
    }

    @Test fun twoEqualEverydayChargesAreAHabitNotASubscription() {
        assertTrue(detect(charge("Fresh Dairy", 45_000, 40, Category.FOOD), charge("Fresh Dairy", 45_000, 10, Category.FOOD)).isEmpty())
    }

    @Test fun twoChargesOfDifferentAmountsNeedAKnownBrand() {
        assertTrue(detect(charge("Acme Cloud", 1_00_000, 40), charge("Acme Cloud", 1_06_000, 10)).isEmpty())
        // Billed in dollars, so the rupee amount moves a little each month.
        val r = detect(charge("OPENAI *CHATGPT SUBSCR", 1_68_000, 40, Category.OTHER), charge("OpenAI ChatGPT", 1_76_000, 10, Category.OTHER)).single()
        assertEquals(Period.MONTHLY, r.period)
    }

    // Robust grouping.

    @Test fun anExtraPurchaseFromTheSameMerchantDoesNotHideTheSubscription() {
        val r = detect(
            charge("Google Play", 13_000, 75), charge("Google Play", 13_000, 45), charge("Google Play", 13_000, 15),
            charge("GOOGLE PLAY", 9_900, 50), charge("GOOGLE *Google Play", 45_000, 20),
        ).single()
        assertEquals(Period.MONTHLY, r.period)
        assertEquals(13_000L, r.amountPaise)
        assertEquals(3, r.transactionIds.size)
    }

    @Test fun primeStaysYearlyBesideRentalsAndShopping() {
        val r = detect(
            charge("Amazon Prime", 1_49_900, 370), charge("Amazon Prime", 1_49_900, 5),
            charge("Amazon Prime Video", 11_900, 200), charge("Amazon Prime Video", 11_900, 60),
            charge("Amazon", 2_34_000, 90, Category.SHOPPING), charge("Amazon", 59_900, 61, Category.SHOPPING), charge("Amazon", 1_29_900, 30, Category.SHOPPING),
        )
        val prime = r.single()
        assertEquals("rec:amazonprime", prime.key)
        assertEquals(Period.YEARLY, prime.period)
        assertEquals(1_49_900L, prime.amountPaise)
    }

    @Test fun aStrayChargeOfTheSamePriceDoesNotBreakTheRhythm() {
        // A second screen bought for a week, say: one more charge at the same price that fits no rhythm.
        val r = detect(charge("Netflix", 64_900, 75), charge("Netflix", 64_900, 45), charge("Netflix", 64_900, 38), charge("Netflix", 64_900, 15)).single()
        assertEquals(Period.MONTHLY, r.period)
        assertEquals(3, r.transactionIds.size)
    }

    @Test fun aPriceRiseThatSticksKeepsTheSeries() {
        val r = detect(
            charge("Netflix", 49_900, 165), charge("Netflix", 49_900, 135), charge("Netflix", 49_900, 105),
            charge("Netflix", 64_900, 75), charge("Netflix", 64_900, 45), charge("Netflix", 64_900, 15),
        ).single()
        assertEquals(Period.MONTHLY, r.period)
        assertEquals(6, r.transactionIds.size)
        assertEquals(64_900L, r.amountPaise)
        assertEquals(49_900L, r.priceRise!!.fromPaise)
        assertEquals(64_900L, r.priceRise!!.toPaise)
        assertTrue(r.active)
    }

    @Test fun hotstarUnderItsManyNamesIsOneSubscription() {
        val r = detect(charge("Disney+ Hotstar", 29_900, 65), charge("JIOHOTSTAR", 29_900, 35), charge("NOVI DIGITAL ENTERTAINMENT PVT LTD", 29_900, 5)).single()
        assertEquals("rec:hotstar", r.key)
        assertEquals(3, r.transactionIds.size)
    }

    @Test fun brandAliasesShareAKey() {
        assertEquals(RecurringDetector.merchantKey("Spotify"), RecurringDetector.merchantKey("SPOTIFYAB"))
        assertEquals(RecurringDetector.merchantKey("Amazon Prime"), RecurringDetector.merchantKey("AMAZONPRIMEMEMBE"))
        assertEquals(RecurringDetector.merchantKey("Apple"), RecurringDetector.merchantKey("APPLE.COM/BILL"))
        assertEquals(RecurringDetector.merchantKey("Apple"), RecurringDetector.merchantKey("iCloud"))
        assertEquals(RecurringDetector.merchantKey("Vodafone Idea"), RecurringDetector.merchantKey("Vi"))
        assertEquals(RecurringDetector.merchantKey("YouTube Premium"), RecurringDetector.merchantKey("GOOGLE YOUTUBE"))
    }

    // Cadence and cost.

    @Test fun fortnightlyIsNotWeeklyAtDoubleTheCost() {
        val r = detect(charge("Cult Fit", 50_000, 42), charge("Cult Fit", 50_000, 28), charge("Cult Fit", 50_000, 14), charge("Cult Fit", 50_000, 0)).single()
        assertEquals(Period.FORTNIGHTLY, r.period)
        assertEquals(50_000L * 26, r.yearlyPaise)
        assertEquals(50_000L * 26 / 12, r.monthlyPaise)
    }

    @Test fun sixtyDayGapsAreNotMonthly() {
        assertTrue(detect(charge("Acme Cloud", 29_900, 125), charge("Acme Cloud", 29_900, 65), charge("Acme Cloud", 29_900, 5)).isEmpty())
    }

    @Test fun halfYearlyIsNotQuarterly() {
        val r = detect(charge("Acme Cloud", 1_20_000, 370), charge("Acme Cloud", 1_20_000, 188), charge("Acme Cloud", 1_20_000, 6)).single()
        assertEquals(Period.HALF_YEARLY, r.period)
        assertEquals(2_40_000L, r.yearlyPaise)
    }

    @Test fun anAnnualAutoPaySeenOnceIsNotCostedTwelveTimes() {
        val r = detect(charge("UPI AutoPay Amazon Prime", 1_49_900, 5)).single()
        assertEquals(Period.UNKNOWN, r.period)
        assertEquals(0L, r.monthlyPaise)
        assertEquals(0L, r.yearlyPaise)
        val book = RecurringBook.of(listOf(r), emptyList())
        assertEquals(0L, book.monthlyPaise)
        assertEquals(0L, book.yearlyPaise)
        assertFalse(book.shown.single().counted)
        assertTrue(book.shown.single().current)
    }

    @Test fun aSingleAutoPayGoesQuietWhenItNeverRepeats() {
        assertFalse(detect(charge("UPI AutoPay Acme Cloud", 50_000, 150)).single().active)
    }

    // Dates.

    @Test fun theNextDateFollowsTheCalendar() {
        val zone = ZoneId.systemDefault()
        fun at(month: Int, dayOfMonth: Int) = ZonedDateTime.of(2026, month, dayOfMonth, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val list = listOf(chargeAt("Netflix", 64_900, at(1, 5)), chargeAt("Netflix", 64_900, at(2, 5)), chargeAt("Netflix", 64_900, at(3, 5)))
        val r = RecurringDetector.detect(list, at(3, 10)).single()
        // 5 Apr, 31 days after 5 Mar: a fixed 30-day step would say 4 Apr and drift a day each long month.
        assertEquals(at(4, 5), r.nextExpectedAt)
        assertFalse(r.isLate(at(3, 10)))
        assertTrue(r.isLate(at(4, 8)))
    }
}
