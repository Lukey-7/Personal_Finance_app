package com.pft.financetracker

import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.model.TransactionType.DEBIT

/** What a message must turn into. [Saved.type] null means "either direction is acceptable". */
sealed class Expect {
    data class Saved(val type: TransactionType?, val paise: Long, val flow: Flow? = null, val merchantContains: String? = null, val ref: String? = null) : Expect()
    data object Ignored : Expect()
    data object Review : Expect()
}

data class CorpusCase(val name: String, val sender: String, val body: String, val expect: Expect)

/**
 * Every real-world SMS shape the parser must handle, with the outcome it must produce. A bug report about a
 * misread message becomes one more row here, so the same shape can never regress.
 */
object SmsCorpus {
    val cases: List<CorpusCase> = listOf(
        // ---- baseline: shapes that were already right on 2026-09-23 ----
        CorpusCase("hdfc_upi_debit", "VM-HDFCBK", "Rs.250.00 debited from a/c **1234 on 12-08-24 to VPA swiggy.upi@axisbank (UPI Ref No 422312345678). Not you? Call 18002586161", Expect.Saved(DEBIT, 25_000, Flow.EXPENSE, "swiggy", "422312345678")),
        CorpusCase("hrs_before_amount", "VM-CANBNK", "At 10:30 Hrs, Rs.500.00 debited from a/c XX1234 to Uber. Ref 422312345678", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "uber")),
        CorpusCase("indian_grouping", "VM-HDFCBK", "Rs.1,23,456.78 debited from a/c **1234 to VPA landlord@okaxis (UPI Ref No 422312345699).", Expect.Saved(DEBIT, 12_345_678, Flow.EXPENSE)),
        CorpusCase("rupee_symbol_space", "VM-HDFCBK", "₹ 99 debited from a/c **1234 to VPA spotify@ybl (UPI Ref No 422312345611).", Expect.Saved(DEBIT, 9_900, Flow.EXPENSE, "spotify")),
        CorpusCase("received_from_person", "VM-KOTAKB", "Received Rs.500.00 in your Kotak Bank AC X1234 from rahul@okicici on 12-08-24.UPI Ref:422312345622.", Expect.Saved(CREDIT, 50_000, Flow.INCOME, "rahul")),
        CorpusCase("atm", "VM-SBIINB", "Rs.2000 withdrawn at ATM S1AN0123 from A/c XX1234 on 12Aug24. Avl Bal Rs 8000 -SBI", Expect.Saved(DEBIT, 200_000, Flow.CASH)),
        CorpusCase("autopay_executed", "VM-HDFCBK", "Rs 649.00 debited from A/c XX1234 for UPI AutoPay to Netflix. Ref 422312345633", Expect.Saved(DEBIT, 64_900, Flow.EXPENSE, "netflix")),
        CorpusCase("salary_neft", "VM-HDFCBK", "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-AUG-24 for NEFT Cr-ACME CORP SALARY.", Expect.Saved(CREDIT, 8_500_000, Flow.INCOME)),
        CorpusCase("self_transfer", "VM-SBIINB", "Rs 5000 debited from A/c XX1234 transferred to own account XX9876. Ref 422312345644", Expect.Saved(DEBIT, 500_000, Flow.TRANSFER)),
        CorpusCase("gpay_sent_short", "VM-GPAYIN", "Sent Rs.120 to chaiwala@ybl", Expect.Saved(DEBIT, 12_000, Flow.EXPENSE, "chaiwala")),
        CorpusCase("card_bill_payment", "AX-ICICIB", "Payment of Rs 15,000.00 received towards your ICICI Bank Credit Card XX4455. Thank you.", Expect.Saved(null, 1_500_000, Flow.TRANSFER)),
        CorpusCase("declined", "VM-HDFCBK", "Txn of Rs 500 on HDFC Card XX1234 at Amazon declined due to insufficient balance.", Expect.Ignored),
        CorpusCase("mandate_setup", "VM-HDFCBK", "UPI AutoPay mandate of Rs 649 for Netflix registered successfully on A/c XX1234.", Expect.Ignored),
        CorpusCase("pure_otp", "VM-HDFCBK", "123456 is your OTP for txn of Rs.5000 at Flipkart. Do not share.", Expect.Ignored),
        CorpusCase("future_debit", "VM-HDFCBK", "Rs.999 will be debited from your a/c on 20-09-26 for Netflix autopay.", Expect.Ignored),
        CorpusCase("pure_promo", "BZ-OFFERS", "Get up to Rs.500 cashback on your next order! Apply now. T&C apply.", Expect.Ignored),
        CorpusCase("ambiguous_to_review", "VM-XYZBNK", "Transaction alert: Rs 300 on your account XX1234.", Expect.Review),
    )
}
