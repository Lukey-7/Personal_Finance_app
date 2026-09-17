package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

/**
 * Layered, bank-agnostic extractors. Each layer is a small object with its own list of patterns so
 * new edge cases can be added by appending a pattern, without touching the orchestration in [SmsParser].
 */

// ---------------------------------------------------------------------------------------------
// Layer 0: pre-filters. Decide early whether a message is even a transaction.
// ---------------------------------------------------------------------------------------------
object TextFilters {
    /** If ANY of these match, the message is not a completed transaction. Order = first reason wins. */
    val ignoreRules: List<Pair<String, Regex>> = listOf(
        "otp" to Regex("""\b(otp|one[\s-]?time[\s-]?password|verification code|passcode)\b""", RegexOption.IGNORE_CASE),
        "promo" to Regex("""\b(offer|cashback up ?to|apply now|pre-?approved|avail now|hurry|limited period|click|t&c|tnc apply|congratulations|win|voucher|coupon|discount|sale|exclusive|upgrade to|get up ?to)\b""", RegexOption.IGNORE_CASE),
        "future" to Regex("""\b(will be (debited|credited|deducted)|due on|is due|payment due|reminder|scheduled|autopay (?:will|is)|upcoming)\b""", RegexOption.IGNORE_CASE),
        "failed" to Regex("""\b(failed|declined|unsuccessful|could not be processed|not processed|reversed due|cancelled|rejected)\b""", RegexOption.IGNORE_CASE),
        "request" to Regex("""\b(has requested|payment request|collect request|requesting|requested money)\b""", RegexOption.IGNORE_CASE),
        "balance_only" to Regex("""^\s*(your|the)?\s*(a/?c|account|available|avl|closing)\s*(bal|balance)""", RegexOption.IGNORE_CASE),
        "statement" to Regex("""\b(statement (is|has been) (generated|ready)|e-?statement|total amount due|minimum amount due|min\.? due)\b""", RegexOption.IGNORE_CASE),
        "login" to Regex("""\b(logged in|login|log-in|password changed|pin changed|registered successfully|kyc)\b""", RegexOption.IGNORE_CASE),
    )

    /** Strong hints that the message is transactional. */
    val transactionHints = Regex(
        """\b(debited|credited|spent|paid|payment|purchase|txn|transaction|withdrawn|transferred|received|sent|deposited|upi|a/c|acct|card)\b""",
        RegexOption.IGNORE_CASE
    )

    fun ignoreReason(body: String): String? {
        for ((reason, rx) in ignoreRules) {
            if (rx.containsMatchIn(body)) {
                // Some promo words also appear in real transactions ("cashback credited"). Only drop for promo
                // if there is no strong transaction verb.
                if (reason == "promo" && Regex("""\b(debited|credited|spent|withdrawn)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)) continue
                return reason
            }
        }
        return null
    }

    fun looksTransactional(body: String) = transactionHints.containsMatchIn(body)
}

// ---------------------------------------------------------------------------------------------
// Layer 1: transaction type (debit / credit). Keyword scoring, not a fixed template.
// ---------------------------------------------------------------------------------------------
object TypeDetector {
    private val debitStrong = listOf("debited", "spent", "withdrawn", "deducted", "paid to", "sent to", "purchase of", "payment of", "txn of", "charged", "paid from", "paid via", "paid using", "made a payment", "you paid", "paid rs", "paid inr", "was paid")
    private val debitWeak = listOf("paid", "purchase", "payment", "sent", "spent", "used for", "at ", "towards", "bill payment")
    private val creditStrong = listOf("credited", "received from", "deposited", "refund", "refunded", "cashback of", "reversed", "credited to", "has been added", "received in", "you received", "salary")
    private val creditWeak = listOf("received", "cashback", "added", "credit", "transfer from", "from ")

    data class Result(val type: TransactionType?, val score: Int)

    fun detect(body: String): Result {
        val b = body.lowercase(Locale.ROOT)
        var debit = 0
        var credit = 0
        debitStrong.forEach { if (b.contains(it)) debit += 3 }
        debitWeak.forEach { if (b.contains(it)) debit += 1 }
        creditStrong.forEach { if (b.contains(it)) credit += 3 }
        creditWeak.forEach { if (b.contains(it)) credit += 1 }

        // "credit card" is a noun phrase, not a credit event.
        if (b.contains("credit card") || b.contains("credit-card")) credit -= 1
        // "debit card" likewise, but a "debit card" message is almost always a debit.
        if (b.contains("debit card")) debit += 1
        // Payment RECEIVED into a credit card is a credit to the card; keep credit.
        if (b.contains("payment received") || b.contains("payment of") && b.contains("received")) credit += 2

        return when {
            debit == 0 && credit == 0 -> Result(null, 0)
            debit > credit -> Result(TransactionType.DEBIT, debit - credit)
            credit > debit -> Result(TransactionType.CREDIT, credit - debit)
            else -> Result(null, 0)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Layer 2: amount. Handles Rs / Rs. / INR / ₹ before or after, Indian comma grouping, decimals.
// ---------------------------------------------------------------------------------------------
object AmountExtractor {
    private const val NUM = """(\d+(?:,\d{2,3})*(?:\.\d{1,2})?)"""
    val patterns: List<Regex> = listOf(
        Regex("""(?:inr|rs\.?|₹|rupees?)\s*:?\s*$NUM""", RegexOption.IGNORE_CASE),
        Regex("""$NUM\s*(?:inr|rs\.?|rupees?|/-)""", RegexOption.IGNORE_CASE),
        Regex("""(?:amount|amt)\s*(?:of)?\s*:?\s*$NUM""", RegexOption.IGNORE_CASE),
    )

    /** Words that, if they directly precede an amount, mean it is a balance/limit rather than the txn amount. */
    private val balanceContext = Regex("""(bal|balance|avl|available|limit|lmt|outstanding|o/s|total due|min due|clr bal)\W{0,12}$""", RegexOption.IGNORE_CASE)

    data class Candidate(val value: Double, val index: Int, val isBalance: Boolean)

    fun candidates(body: String): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (rx in patterns) {
            for (m in rx.findAll(body)) {
                val raw = m.groupValues[1].replace(",", "")
                val value = raw.toDoubleOrNull() ?: continue
                if (value <= 0.0 || value > 10_000_000.0) continue
                val prefix = body.substring(maxOf(0, m.range.first - 30), m.range.first)
                val isBal = balanceContext.containsMatchIn(prefix)
                if (out.none { it.index == m.range.first }) out += Candidate(value, m.range.first, isBal)
            }
        }
        return out.sortedBy { it.index }
    }

    /** Best guess: first non-balance amount; else first amount. */
    fun extract(body: String): Double? {
        val c = candidates(body)
        return (c.firstOrNull { !it.isBalance } ?: c.firstOrNull())?.value
    }
}

// ---------------------------------------------------------------------------------------------
// Layer 3: account / card reference (last few digits only).
// ---------------------------------------------------------------------------------------------
object AccountExtractor {
    val patterns: List<Regex> = listOf(
        Regex("""(?:a/c|ac|acct|account|card|cc)\s*(?:no\.?|number|#)?\s*(?:ending(?: with| in)?|end(?:ing)?|xx+|x+|\*+|-)?\s*[xX*]*(\d{3,6})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b[xX*]{2,}(\d{3,6})\b"""),
        Regex("""(?:ending|ending with|ending in)\s*(\d{3,6})\b""", RegexOption.IGNORE_CASE),
    )

    fun extract(body: String): String? {
        for (rx in patterns) {
            val m = rx.find(body) ?: continue
            val digits = m.groupValues[1]
            if (digits.length in 3..6) return digits.takeLast(4)
        }
        return null
    }
}

// ---------------------------------------------------------------------------------------------
// Layer 4: bank / issuer name from sender ID or body. Extensible list, not a hard requirement.
// ---------------------------------------------------------------------------------------------
object BankExtractor {
    /** Map of substrings (lowercase) found in sender IDs / bodies to display names. Append freely. */
    val knownIssuers: List<Pair<String, String>> = listOf(
        "hdfc" to "HDFC Bank", "icici" to "ICICI Bank", "sbi" to "SBI", "sbin" to "SBI", "axis" to "Axis Bank",
        "kotak" to "Kotak", "yesbnk" to "Yes Bank", "yes bank" to "Yes Bank", "indus" to "IndusInd", "idfc" to "IDFC First",
        "pnb" to "PNB", "punjab national" to "PNB", "bob" to "Bank of Baroda", "baroda" to "Bank of Baroda",
        "canbnk" to "Canara Bank", "canara" to "Canara Bank", "unionb" to "Union Bank", "union bank" to "Union Bank",
        "boi" to "Bank of India", "iob" to "IOB", "cbi" to "Central Bank", "federal" to "Federal Bank", "fedbnk" to "Federal Bank",
        "rbl" to "RBL Bank", "au bank" to "AU Bank", "aubank" to "AU Bank", "bandhan" to "Bandhan", "karur" to "KVB",
        "kvb" to "KVB", "citi" to "Citi", "hsbc" to "HSBC", "sc bank" to "Standard Chartered", "scb" to "Standard Chartered",
        "dbs" to "DBS", "amex" to "Amex", "american express" to "Amex", "onecard" to "OneCard", "slice" to "Slice",
        "paytm" to "Paytm", "pytm" to "Paytm", "phonepe" to "PhonePe", "phonpe" to "PhonePe", "gpay" to "Google Pay",
        "google pay" to "Google Pay", "amazon pay" to "Amazon Pay", "amznpy" to "Amazon Pay", "cred" to "CRED",
        "jupiter" to "Jupiter", "fi money" to "Fi", "niyo" to "Niyo", "airtel" to "Airtel Payments Bank",
        "jio" to "Jio Payments Bank", "ippb" to "India Post Payments Bank", "postbank" to "India Post Payments Bank",
        "ujjivan" to "Ujjivan", "equitas" to "Equitas", "dcb" to "DCB Bank", "south indian" to "South Indian Bank",
        "sib" to "South Indian Bank", "tmb" to "TMB", "uco" to "UCO Bank", "indian bank" to "Indian Bank", "indbnk" to "Indian Bank",
    )

    private val genericBank = Regex("""\b([A-Z][A-Za-z&]+(?:\s[A-Z][A-Za-z&]+)?\s(?:Bank|Payments Bank))\b""")

    fun extract(sender: String, body: String): String? {
        val s = sender.lowercase(Locale.ROOT)
        // Sender IDs look like "VM-HDFCBK", "AD-ICICIB-S", "JK-SBIINB". Strip the operator prefix.
        val senderCore = s.substringAfter('-', s).substringBefore('-')
        knownIssuers.firstOrNull { senderCore.contains(it.first) }?.let { return it.second }
        val b = body.lowercase(Locale.ROOT)
        knownIssuers.firstOrNull { b.contains(it.first) }?.let { return it.second }
        genericBank.find(body)?.let { return it.groupValues[1] }
        return if (sender.any { it.isLetter() }) senderCore.uppercase(Locale.ROOT) else null
    }
}

// ---------------------------------------------------------------------------------------------
// Layer 5: merchant / payee. Ordered list of patterns; first hit wins. Append new shapes at the right priority.
// ---------------------------------------------------------------------------------------------
object MerchantExtractor {
    private const val STOP = """(?=\s+(?:on|dt|dated|ref|refno|ref no|upi ref|txn|txnid|txn id|via|using|from|avl|available|bal|balance|info|is|\.|,|-|\(|;|\||$)|\s*$|\.\s|,)"""

    val patterns: List<Regex> = listOf(
        // UPI VPA e.g. "to VPA merchant@okaxis", "paid to swiggy@ybl"
        Regex("""(?:to|for|at|vpa|payee)\s*:?\s*(?:vpa\s*)?([a-z0-9._\-]+@[a-z]{2,})""", RegexOption.IGNORE_CASE),
        // "UPI/P2M/123456/Merchant Name" or "UPI-Merchant Name-..."
        Regex("""UPI[/:\-]\s*(?:P2[AM][/\-])?(?:\d{6,}[/\-])?([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)(?=[/\-]|\s+on|\s+ref|$)"""),
        // "Info: MERCHANT" / "Info- MERCHANT"
        Regex("""\bInfo\s*[:\-]\s*([A-Za-z0-9][A-Za-z0-9 .&'\-]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // "at MERCHANT on", "at MERCHANT."
        Regex("""\bat\s+([A-Za-z0-9][A-Za-z0-9 .&'\-*]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // "to MERCHANT on", "paid to MERCHANT", "sent to MERCHANT", "transferred to MERCHANT"
        Regex("""\b(?:paid |sent |transferred |credited )?to\s+(?!your|ur|a/c|account|card)([A-Za-z0-9][A-Za-z0-9 .&'\-*]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // "towards MERCHANT", "for MERCHANT"
        Regex("""\b(?:towards|for)\s+(?!rs|inr|₹|a/c|account|card)([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // Credits: "from MERCHANT", "by MERCHANT", "received from"
        Regex("""\b(?:from|by)\s+(?!your|ur|a/c|account|card|rs\.?\s*\d|inr\s*\d|₹)([A-Za-z0-9][A-Za-z0-9 .&'\-*@]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
    )

    private val noise = Regex("""\b(upi|imps|neft|rtgs|pos|ecom|txn|ref|no|id|payment|via|the|mr|ms|mrs)\b""", RegexOption.IGNORE_CASE)

    fun extract(body: String, type: TransactionType?): String? {
        val ordered = if (type == TransactionType.CREDIT) patterns.sortedByDescending { it.pattern.contains("from|by") } else patterns
        for (rx in ordered) {
            val m = rx.find(body) ?: continue
            val cleaned = clean(m.groupValues[1])
            if (cleaned.length >= 3 && !cleaned.all { it.isDigit() }) return cleaned
        }
        return null
    }

    fun clean(raw: String): String {
        var s = raw.trim().trimEnd('.', ',', '-', ':', ';')
        if (s.contains('@')) {
            // VPA: keep the handle part, humanize it: "swiggy.upi@axisbank" -> "swiggy"
            s = s.substringBefore('@').substringBefore('.').replace(Regex("""[._\-]+"""), " ")
                .split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        s = s.replace(Regex("""\s{2,}"""), " ")
        s = noise.replace(s, "").replace(Regex("""\s{2,}"""), " ").trim()
        if (s.length > 40) s = s.take(40).trim()
        return s.split(" ").joinToString(" ") { w -> if (w.length > 2 && w.all { it.isUpperCase() || !it.isLetter() }) w.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() } else w }
    }
}

// ---------------------------------------------------------------------------------------------
// Layer 6: date inside the body (fallback: SMS received timestamp).
// ---------------------------------------------------------------------------------------------
object DateExtractor {
    private val formats = listOf(
        "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy", "dd-MMM-yy", "dd-MMM-yyyy", "ddMMMyy", "ddMMMyyyy",
        "dd MMM yy", "dd MMM yyyy", "dd.MM.yy", "dd.MM.yyyy", "yyyy-MM-dd", "MMM dd, yyyy", "dd-MMM-yy HH:mm", "dd/MM/yy HH:mm",
    )
    private val dateRx = Regex("""\b(\d{1,2}[-/. ]?(?:\d{1,2}|[A-Za-z]{3})[-/. ]?\d{2,4}(?:[ ,T]+\d{1,2}:\d{2}(?::\d{2})?)?|\d{4}-\d{2}-\d{2}|[A-Za-z]{3}\s\d{1,2},\s\d{4})\b""")

    fun extract(body: String, fallback: Long): Long {
        val now = System.currentTimeMillis()
        for (m in dateRx.findAll(body)) {
            val text = m.value.trim()
            for (f in formats) {
                val sdf = SimpleDateFormat(f, Locale.ENGLISH).apply { isLenient = false }
                val d = runCatching { sdf.parse(text) }.getOrNull() ?: continue
                val t = d.time
                // Reject absurd years (two-digit-year parsing oddities) and future dates.
                if (t in (now - 10L * 365 * 24 * 3600 * 1000)..(now + 24 * 3600 * 1000)) return t
            }
        }
        return fallback
    }
}
