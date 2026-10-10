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

        // ---- Task 4: direction ----
        CorpusCase("icici_upi_payee_credited", "AX-ICICIB", "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Mar-24; DAKSHIN CAFE credited. UPI:408812345678. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.", Expect.Saved(DEBIT, 24_000, Flow.EXPENSE, "dakshin", "408812345678")),
        CorpusCase("bob_dr_cr_abbrev", "VK-BOBTXN", "Rs.500 Dr. from A/C XXXXXX1234 and Cr. to swiggy@ybl. Ref:422312345678. AvlBal:Rs10000.00", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "swiggy")),
        CorpusCase("paid_with_cashback", "VM-AMZNPY", "You paid Rs 200 to Blinkit using Amazon Pay. Cashback of Rs 20 credited to your balance.", Expect.Saved(DEBIT, 20_000, Flow.EXPENSE, "blinkit")),
        CorpusCase("card_bill_payment_is_credit", "AX-ICICIB", "Payment of Rs 15,000.00 received towards your ICICI Bank Credit Card XX4455. Thank you.", Expect.Saved(CREDIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("credited_to_beneficiary", "VM-SBIINB", "INR 500.00 credited to beneficiary A/c XX9999 (JOHN) from your A/c XX1234 via IMPS. Ref 422312345655", Expect.Saved(DEBIT, 50_000)),
        CorpusCase("transferred_into_your_account", "VM-SBIINB", "Rs 2,000 transferred to your a/c XX1234 from RAHUL via IMPS. Ref 422312345666", Expect.Saved(CREDIT, 200_000)),
        CorpusCase("someone_paid_you", "VM-PHONPE", "Rahul paid you Rs 500 on PhonePe. UPI Ref 422312345677", Expect.Saved(CREDIT, 50_000)),
        CorpusCase("paid_to_you_by", "VM-PAYTMB", "Rs 500 paid to you by Rahul Sharma via UPI. Ref 422312345676", Expect.Saved(CREDIT, 50_000)),
        CorpusCase("paid_to_your_card_is_not_income", "VM-PAYTMB", "Rs 5,000 paid to your HDFC Bank Credit Card XX4455 from Paytm. Ref 422312345675", Expect.Saved(DEBIT, 500_000)),

        // ---- Task 5: amount ----
        CorpusCase("masked_acct_then_rs", "VM-KOTAKB", "A/c XX1234 Rs 750.00 debited to Swiggy on 12-08-24. UPI Ref 422312345678", Expect.Saved(DEBIT, 75_000, Flow.EXPENSE, "swiggy")),
        CorpusCase("plain_acct_then_rs", "VM-KOTAKB", "A/c 1234 Rs 750.00 debited to Swiggy. UPI Ref 422312345679", Expect.Saved(DEBIT, 75_000)),
        CorpusCase("card_word_before_rs", "VM-HDFCBK", "Rs.500 paid to card XX1234 towards Amazon. Ref 422312345680", Expect.Saved(DEBIT, 50_000)),

        // ---- Task 6: ignore rules ----
        CorpusCase("otp_footer_on_debit", "VM-HDFCBK", "Rs.500.00 debited from a/c **1234 to VPA zomato@hdfcbank (UPI Ref No 422312345678). Never share your OTP with anyone.", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "zomato")),
        CorpusCase("balance_leads", "VM-AXISBK", "Avl Bal Rs 10,000.00 after Rs 500.00 debited from A/c XX1234 at Zepto. Ref 422312345678", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE)),
        CorpusCase("refund_cancelled_order", "AD-HDFCBK", "Refund of Rs 499.00 for your cancelled order has been credited to your A/c XX1234. Ref 998877665544", Expect.Saved(CREDIT, 49_900, Flow.REFUND)),
        CorpusCase("reversal_of_failed_txn", "JD-SBIINB", "Your a/c XX1234 is credited with Rs 500.00 towards reversal of failed UPI txn Ref 123456789012 -SBI", Expect.Saved(CREDIT, 50_000, Flow.REFUND)),
        CorpusCase("debited_then_failed", "VM-HDFCBK", "Rs 500.00 debited from A/c XX1234 to swiggy@ybl but the txn failed. Amount will be reversed in 48 hrs. Ref 422312345677", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE)),
        CorpusCase("cashback_will_be_credited", "VM-PAYTMB", "Paid Rs 300 to Zomato via UPI. Cashback will be credited in 3 days.", Expect.Saved(DEBIT, 30_000, Flow.EXPENSE)),
        CorpusCase("not_debited", "VM-HDFCBK", "UPI txn of Rs 500 to Swiggy failed. Your account has not been debited.", Expect.Ignored),
        CorpusCase("failed_if_debited", "VM-HDFCBK", "UPI txn of Rs 500 failed. If amount debited, it will be reversed within 48 hrs.", Expect.Ignored),
        CorpusCase("refund_promised", "VM-HDFCBK", "Txn of Rs 500 failed. Amount will be refunded in 5-7 days.", Expect.Ignored),

        // ---- v1.5 parser review ----
        // A bare amount after the verb is the payment; a balance never is.
        CorpusCase("bare_amount_not_balance", "VM-HDFCBK", "Your a/c XX1234 is debited for 500.00 on 05-10-26 to SWIGGY. Avl Bal Rs.10,000.00", Expect.Saved(DEBIT, 50_000, merchantContains = "swiggy")),
        CorpusCase("only_balance_goes_to_review", "VM-HDFCBK", "Your a/c XX1234 is debited. Avl Bal Rs.10,000.00", Expect.Review),
        // Card bill payments are transfers on both sides, never income or spend.
        CorpusCase("sbi_card_payment_credited", "AD-SBICRD", "Dear Cardmember, Payment of Rs 15,000.00 has been credited to your SBI Card ending 1234", Expect.Saved(CREDIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("sbi_card_received_payment", "AD-SBICRD", "We have received payment of Rs.15,000.00 towards your SBI Credit Card ending with 1234", Expect.Saved(CREDIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("axis_card_payment_received_on", "AX-AXISBK", "Thank you! Payment of INR 15,000.00 received on your Axis Bank Credit Card XX1234", Expect.Saved(CREDIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("axis_card_payment_received_for", "AX-AXISBK", "Payment of INR 15000.00 received for Axis Bank Credit Card no. XX1234", Expect.Saved(CREDIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("card_payment_over_a_lakh", "VM-HDFCBK", "Payment of Rs 1,50,000.00 has been credited to your HDFC Bank Credit Card XX4455", Expect.Saved(CREDIT, 15_000_000, Flow.TRANSFER)),
        CorpusCase("bank_debit_to_ccpay", "VM-ICICIB", "Rs.15,000 debited from A/c XX1234 to ccpay.4375XXXX@icici", Expect.Saved(DEBIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("bank_payment_to_your_card", "VM-HDFCBK", "Payment of Rs 15,000 to your Credit Card XX4455 was successful", Expect.Saved(DEBIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("billdesk_card_payment", "VM-HDFCBK", "Rs 15,000.00 debited from A/c XX1234 to BillDesk for HDFC Credit Card bill. Ref 427712345690", Expect.Saved(DEBIT, 1_500_000, Flow.TRANSFER)),
        CorpusCase("cred_card_payment", "VM-HDFCBK", "Rs 15,000 paid to CRED for your HDFC Bank Credit Card XX4455 bill. Ref 427712345691", Expect.Saved(DEBIT, 1_500_000, Flow.TRANSFER)),
        // Axis UPI alerts: payee and RRN from "UPI/P2M/<rrn>/<name>", stopping at "Not you".
        CorpusCase("axis_upi_p2m", "AX-AXISBK", "INR 120.00 debited A/c no. XX1234 12-10-24, 13:45:12 UPI/P2M/428612345678/ZOMATO LTD Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank", Expect.Saved(DEBIT, 12_000, Flow.EXPENSE, "zomato", "428612345678")),
        CorpusCase("axis_upi_p2a_credit", "AX-AXISBK", "INR 5,000.00 credited to A/c no. XX1234 12-10-24, 13:45:12 UPI/P2A/428612345679/RAHUL SHARMA Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank", Expect.Saved(CREDIT, 500_000, merchantContains = "rahul", ref = "428612345679")),
        // A failed payment that says no money moved.
        CorpusCase("failed_no_amount_debited", "VM-HDFCBK", "UPI txn of Rs 500 to SWIGGY has failed. No amount has been debited from your account.", Expect.Ignored),
        CorpusCase("failed_no_money_debited", "VM-HDFCBK", "Payment of Rs 500 to SWIGGY failed. No money was debited from your account.", Expect.Ignored),
        // SBI's UPI shape: bare amount, compact date, "trf to".
        CorpusCase("sbi_upi_debited_by", "VM-SBIUPI", "Dear UPI user A/C X1234 debited by 20.0 on date 03Oct24 trf to SWIGGY Refno 427712345678. If not u? call 1800111109. -SBI", Expect.Saved(DEBIT, 2_000, merchantContains = "swiggy", ref = "427712345678")),
        // "Sent" is a completed movement: a payee called BIG SALE MART is not a promotion.
        CorpusCase("sent_to_sale_named_payee", "VM-HDFCBK", "Sent Rs.500.00 From HDFC Bank A/C *1234 To BIG SALE MART On 05/10/26 Ref 427712345678", Expect.Saved(DEBIT, 50_000, merchantContains = "big sale", ref = "427712345678")),
        // Money arriving, worded with "sent ... to you" or "transferred from X to your".
        CorpusCase("sent_to_you", "VM-PAYTMB", "Rahul has sent Rs.500 to you on Paytm. UPI Ref 427712345678", Expect.Saved(CREDIT, 50_000, merchantContains = "rahul", ref = "427712345678")),
        CorpusCase("transferred_from_someone_to_your", "VM-SBIINB", "Rs 2,000 transferred from RAHUL to your a/c XX1234 via IMPS", Expect.Saved(CREDIT, 200_000, merchantContains = "rahul")),
        // A date before "Rs" is not the amount.
        CorpusCase("date_before_rs", "VM-HDFCBK", "On 05-10-26 Rs 500 debited from A/c XX1234 to SWIGGY. Ref 427712345678", Expect.Saved(DEBIT, 50_000, merchantContains = "swiggy")),
        // "balance is INR 10,000" is a balance.
        CorpusCase("balance_is_before_debit", "VM-HDFCBK", "Your available balance is INR 10,000 after debit of INR 500 at SWIGGY", Expect.Saved(DEBIT, 50_000, merchantContains = "swiggy")),
        // Payment rails are not merchants.
        CorpusCase("neft_from_company", "VM-HDFCBK", "Rs 25,000.00 credited to your A/c XX1234 by NEFT from ACME CORP. Ref 427712345692", Expect.Saved(CREDIT, 2_500_000, merchantContains = "acme")),
        // A footer selling FDs does not make a food order an investment.
        CorpusCase("fd_footer_is_not_investment", "VM-HDFCBK", "Rs 500.00 debited from A/c XX1234 at SWIGGY. Earn 7% on FD. T&C apply", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "swiggy")),
        // No-break spaces and the old rupee sign.
        CorpusCase("no_break_spaces", "VM-HDFCBK", "Rs.\u00A0500.00 debited from a/c\u00A0**1234 to VPA swiggy@ybl (UPI Ref No\u202F427712345678)", Expect.Saved(DEBIT, 50_000, Flow.EXPENSE, "swiggy", "427712345678")),
        CorpusCase("old_rupee_sign", "VM-HDFCBK", "\u20A8500.00 debited from a/c **1234 to VPA swiggy@ybl (UPI Ref No 427712345678)", Expect.Saved(DEBIT, 50_000, merchantContains = "swiggy")),
    )
}
