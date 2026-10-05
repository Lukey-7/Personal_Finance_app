package com.pft.financetracker

import com.pft.financetracker.domain.bills.Amortization
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.domain.bills.Loan
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BillsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun d(s: String) = LocalDate.parse(s)
    private fun at(s: String, h: Int = 12) = d(s).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    // ---- Loan EMI ----

    @Test fun emiOfAKnownHomeLoan() {
        // ₹10 lakh at 9% for 20 years: ₹8,997.26 a month (standard EMI tables).
        assertEquals(8_99_726L, Amortization.emi(10_00_000_00L, 900, 240))
    }

    @Test fun emiWithNoInterestIsAnEvenSplit() = assertEquals(10_000_00L, Amortization.emi(1_20_000_00L, 0, 12))

    @Test fun theFirstEmiIsMostlyInterest() {
        val first = Amortization.schedule(10_00_000_00L, 900, 240).first()
        assertEquals(7_500_00L, first.interestPaise)
        assertEquals(8_99_726L - 7_500_00L, first.principalPaise)
    }

    @Test fun theScheduleEndsAtZeroAndRepaysThePrincipal() {
        val s = Amortization.schedule(5_00_000_00L, 1050, 36)
        assertEquals(0L, s.last().balancePaise)
        assertEquals(5_00_000_00L, s.sumOf { it.principalPaise })
    }

    // ---- Due dates ----

    private val airtel = Bill(id = 1, name = "Airtel postpaid", amountPaise = 79_900, dueDay = 5, keyword = "airtel", category = Category.BILLS)

    @Test fun nextMonthlyDueDate() {
        assertEquals(d("2026-10-05"), BillTracker.nextDue(airtel, d("2026-10-03")))
        assertEquals(d("2026-10-05"), BillTracker.nextDue(airtel, d("2026-10-05")))
        assertEquals(d("2026-11-05"), BillTracker.nextDue(airtel, d("2026-10-06")))
    }

    @Test fun theLastDayIsClampedInShortMonths() {
        val rent = airtel.copy(dueDay = 31)
        assertEquals(d("2027-02-28"), BillTracker.nextDue(rent, d("2027-02-01")))
        assertEquals(d("2026-09-30"), BillTracker.nextDue(rent, d("2026-09-10")))
    }

    @Test fun aQuarterlyBillFollowsItsStartMonth() {
        val insurance = airtel.copy(everyMonths = 3, startMonth = 1)   // Jan, Apr, Jul, Oct
        assertEquals(d("2027-01-05"), BillTracker.nextDue(insurance, d("2026-10-06")))
        assertEquals(d("2026-10-05"), BillTracker.nextDue(insurance, d("2026-08-20")))
    }

    @Test fun aOneOffDueDateWins() {
        val card = airtel.copy(dueDay = 0, fixedDue = d("2026-10-18"))
        assertEquals(d("2026-10-18"), BillTracker.nextDue(card, d("2026-10-03")))
        assertNull(BillTracker.nextDue(card, d("2026-10-19")))
    }

    // ---- Status ----

    private fun debit(m: String, paise: Long, on: String, id: Long = 9) = Transaction(id = id, amountPaise = paise, type = TransactionType.DEBIT, merchant = m,
        category = Category.BILLS, timestamp = at(on), bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.EXPENSE)

    @Test fun upcomingShowsDaysLeft() {
        val s = BillTracker.state(airtel, d("2026-10-02"), emptyList(), emptySet(), zone)
        assertEquals(BillState.Upcoming(d("2026-10-05"), 3), s)
    }

    @Test fun aMatchingPaymentNearTheDueDateMarksItPaid() {
        val s = BillTracker.state(airtel, d("2026-10-04"), listOf(debit("AIRTEL PAYMENTS", 79_900, "2026-10-03")), emptySet(), zone)
        assertEquals(BillState.Paid(d("2026-10-05"), 9), s)
    }

    @Test fun theSameAmountFromAnotherMerchantPaysAFixedBillWithoutAKeyword() {
        val s = BillTracker.state(airtel.copy(keyword = null), d("2026-10-04"), listOf(debit("BBPS", 79_900, "2026-10-04")), emptySet(), zone)
        assertTrue(s is BillState.Paid)
    }

    @Test fun aPaymentLongBeforeTheDueDateDoesNotCount() {
        val s = BillTracker.state(airtel, d("2026-10-04"), listOf(debit("Airtel", 79_900, "2026-09-20")), emptySet(), zone)
        assertTrue(s is BillState.Upcoming)
    }

    @Test fun aBillWithAKeywordIsNotPaidByAnotherPaymentOfTheSameAmount() {
        // Rent is ₹25,000 to NoBroker; a ₹25,000 card-bill payment the same week must not mark it paid.
        val rent = Bill(id = 3, name = "Rent", amountPaise = 25_000_00, dueDay = 5, keyword = "nobroker")
        val s = BillTracker.state(rent, d("2026-10-04"), listOf(debit("CRED CLUB", 25_000_00, "2026-10-03")), emptySet(), zone)
        assertTrue(s is BillState.Upcoming)
    }

    @Test fun unpaidAfterTheDueDateIsOverdue() {
        val s = BillTracker.state(airtel, d("2026-10-08"), emptyList(), emptySet(), zone)
        assertEquals(BillState.Overdue(d("2026-10-05"), 3), s)
    }

    @Test fun overdueStopsWhenThePaymentArrivesLate() {
        val s = BillTracker.state(airtel, d("2026-10-08"), listOf(debit("Airtel", 79_900, "2026-10-07")), emptySet(), zone)
        assertEquals(BillState.Paid(d("2026-10-05"), 9), s)
    }

    @Test fun markedPaidByHandCounts() {
        val s = BillTracker.state(airtel, d("2026-10-08"), emptyList(), setOf(d("2026-10-05")), zone)
        assertEquals(BillState.Paid(d("2026-10-05"), null), s)
    }

    @Test fun afterAPaidCycleTheNextOneIsUpcoming() {
        val s = BillTracker.state(airtel, d("2026-10-20"), listOf(debit("Airtel", 79_900, "2026-10-04")), emptySet(), zone)
        assertEquals(BillState.Upcoming(d("2026-11-05"), 16), s)
    }

    @Test fun anEmiShowsWhichInstalmentAndWhatIsLeft() {
        val car = Bill(id = 2, name = "Car loan", amountPaise = null, dueDay = 10, keyword = "hdfc loan", category = Category.BILLS,
            loan = Loan(principalPaise = 5_00_000_00L, annualRateBp = 900, tenureMonths = 36, firstDue = d("2026-01-10")))
        val p = BillTracker.loanProgress(car, d("2026-10-12"))!!
        assertEquals(10, p.paidInstalments)          // Jan..Oct
        assertEquals(36, p.totalInstalments)
        assertEquals(Amortization.schedule(5_00_000_00L, 900, 36)[9].balancePaise, p.outstandingPaise)
        assertEquals(Amortization.emi(5_00_000_00L, 900, 36), BillTracker.amountDue(car))
    }

    @Test fun reminders3And1DayBeforeForUnpaidBills() {
        val r = BillTracker.reminder(airtel, d("2026-10-02"), emptyList(), emptySet(), zone)!!
        assertEquals(listOf(3, 1), r.leadDays)
        assertEquals("bill:1", r.key)
        assertNull(BillTracker.reminder(airtel, d("2026-10-04"), listOf(debit("Airtel", 79_900, "2026-10-03")), emptySet(), zone))
    }
}
