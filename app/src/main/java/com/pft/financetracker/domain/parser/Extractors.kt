package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
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
        "not_moved" to Regex(
            """\b(?:not|never)\s+(?:been\s+)?(?:debited|credited|deducted|charged|processed)\b|\bwasn'?t\s+(?:debited|charged)\b""" +
                // "No amount has been debited", "no money was debited", "no amount is deducted from your account".
                """|\bno\s+(?:amount|money|funds?)\s+(?:has\s+been|have\s+been|had\s+been|was|were|is|got)\s+(?:debited|deducted|charged)\b""" +
                // A failed payment whose money "will be refunded within 5 days": nothing to count now.
                """|\b(?:failed|declined|unsuccessful)\b.{0,80}?\bwill\s+be\s+(?:refunded|reversed)\s+within\b""",
            RegexOption.IGNORE_CASE,
        ),
        "otp" to Regex("""\b(otp|one[\s-]?time[\s-]?password|verification code|passcode)\b""", RegexOption.IGNORE_CASE),
        "promo" to Regex("""\b(offer|cashback up ?to|apply now|pre-?approved|avail now|hurry|limited period|click|t&c|tnc apply|congratulations|win|voucher|coupon|discount|sale|exclusive|upgrade to|get up ?to)\b""", RegexOption.IGNORE_CASE),
        "future" to Regex("""\b(will be (debited|credited|deducted|refunded|reversed)|autopay (?:will|is) (?:scheduled|due)|is scheduled (?:for|on))\b""", RegexOption.IGNORE_CASE),
        "future_soft" to Regex("""\b(due on|is due|payment due|reminder|scheduled|upcoming)\b""", RegexOption.IGNORE_CASE),
        "failed" to Regex("""\b(failed|declined|unsuccessful|could not be processed|not processed|reversed due|cancelled|rejected)\b""", RegexOption.IGNORE_CASE),
        "request" to Regex("""\b(has requested|payment request|collect request|requesting|requested money)\b""", RegexOption.IGNORE_CASE),
        "balance_only" to Regex("""^\s*(your|the)?\s*(a/?c|account|available|avl|closing)\s*(bal|balance)""", RegexOption.IGNORE_CASE),
        "statement" to Regex("""\b(statement (is|has been) (generated|ready)|e-?statement|total amount due|minimum amount due|min\.? due)\b""", RegexOption.IGNORE_CASE),
        "login" to Regex("""\b(logged in|login|log-in|password changed|pin changed|registered successfully|kyc)\b""", RegexOption.IGNORE_CASE),
    )

    /** Strong hints that the message is transactional. */
    val transactionHints = Regex(
        """\b(debited|credited|debit|spent|paid|payment|purchase|txn|transaction|withdrawn|transferred|received|sent|deposited|upi|a/c|acct|card)\b""",
        RegexOption.IGNORE_CASE
    )

    /** A verb that only appears once money has actually moved. */
    val completedVerb = Regex(
        """\b(debited|credited|spent|withdrawn|deducted|paid|received|transferred|refunded|reversed)\b""" +
            // "Sent Rs.500 to ...", "Rs 500 sent to ...": only next to an amount, so "OTP sent to your mobile" never counts.
            """|\bsent(?=\s+(?:rs\.?|inr|₹)\s*\d)|(?:\brs\.?|\binr|₹)\s*[\d,]+(?:\.\d{1,2})?\s+(?:has\s+been\s+|was\s+)?sent\b""" +
            // "after debit of INR 500"
            """|(?<!\bof\s)\bdebit(?=\s+of\s+(?:rs\.?|inr|₹)\s*\d)""",
        RegexOption.IGNORE_CASE,
    )

    /** "will be debited", "to be refunded", "if amount debited": the verb describes something that has not happened. */
    private val notYet = Regex("""\b(?:will|shall|would|to\s+be|if|in\s+case)\b[^.;,]{0,20}$""", RegexOption.IGNORE_CASE)

    /** Only "money did not move" is final. Every other rule yields to a message that reports a completed movement. */
    private val hardRules = setOf("not_moved")

    fun hasCompletedMove(body: String): Boolean =
        completedVerb.findAll(body).any { m -> !notYet.containsMatchIn(body.substring(maxOf(0, m.range.first - 30), m.range.first)) } &&
            AmountExtractor.candidates(body).any { !it.isBalance }

    fun ignoreReason(body: String): String? {
        val completed = hasCompletedMove(body)
        for ((reason, rx) in ignoreRules) {
            if (rx.containsMatchIn(body)) {
                // Real transactions carry OTP footers, "failed ... reversed", "cancelled order" refunds, promo and
                // login wording. Never drop a message that clearly reports money moving; let scoring decide.
                if (reason !in hardRules && completed) continue
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
    private val debitWeak = listOf("paid", "purchase", "payment", "sent", "used for", "towards", "bill payment")
    private val debitWeakRx = listOf(Regex("""\bat\s+[a-z]""", RegexOption.IGNORE_CASE))
    private val creditStrong = listOf("credited", "received from", "deposited", "refund", "refunded", "cashback of", "reversed", "credited to", "has been added", "received in", "you received", "salary")
    private val creditWeak = listOf("received", "cashback", "added", "credit", "transfer from")
    private val creditWeakRx = listOf(Regex("""\bfrom\s+[a-z]""", RegexOption.IGNORE_CASE))

    data class Result(val type: TransactionType?, val score: Int)

    /**
     * The first verb that reports money moving is about the customer's own account; later mentions describe the
     * other side ("Acct debited ...; DAKSHIN CAFE credited", "Rs.500 Dr. ... Cr. to x@ybl", "You paid Rs 200 ...
     * Cashback of Rs 20 credited"). Counting keywords let those later mentions outvote the real direction.
     */
    private val primaryVerb = Regex(
        """\b(debited|spent|withdrawn|deducted|paid|sent|charged|transferred|credited|received|deposited|refunded|refund|(?<!\bof\s)debit(?=\s+of\b))\b|(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?\s*(dr|cr)\b""",
        RegexOption.IGNORE_CASE
    )
    private val toCounterparty = Regex("""^\s*to\s+(?:the\s+)?(?:beneficiary|payee|[\w.\-]+@[a-z]+)""", RegexOption.IGNORE_CASE)
    private val intoOwnAccount = Regex("""^\s*(?:to|into|in)\s+(?:your|ur)\b""", RegexOption.IGNORE_CASE)
    /** "transferred from RAHUL to your a/c": someone else's money arriving. Not "from your a/c XX1 to RAHUL". */
    private val fromOtherIntoOwn = Regex("""^\s*from\s+(?!your\b|ur\b|a/?c\b|acct\b|account\b)[^.;]{1,40}?\s+(?:to|into|in)\s+(?:your|ur)\b""", RegexOption.IGNORE_CASE)
    /**
     * "paid you", "paid to you", "sent you Rs 500", "sent Rs.500 to you"; not "paid to your card", which is the customer
     * paying ("your" never matches "you\b").
     */
    private val toYou = Regex("""^\s*(?:(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?\s+)?(?:to\s+)?you\b""", RegexOption.IGNORE_CASE)

    fun primaryDirection(body: String): TransactionType? {
        val m = primaryVerb.find(body) ?: return null
        val word = (m.groups[1]?.value ?: m.groups[2]?.value ?: return null).lowercase(Locale.ROOT)
        val after = body.substring(m.range.last + 1, minOf(body.length, m.range.last + 61))
        return when (word) {
            "paid", "sent" -> if (toYou.containsMatchIn(after)) TransactionType.CREDIT else TransactionType.DEBIT
            "debited", "spent", "withdrawn", "deducted", "charged", "dr", "debit" -> TransactionType.DEBIT
            "transferred" -> if (intoOwnAccount.containsMatchIn(after) || fromOtherIntoOwn.containsMatchIn(after)) TransactionType.CREDIT else TransactionType.DEBIT
            "credited" -> if (toCounterparty.containsMatchIn(after)) TransactionType.DEBIT else TransactionType.CREDIT
            else -> TransactionType.CREDIT // received, deposited, refunded, refund, cr
        }
    }

    fun detect(body: String): Result {
        val b = body.lowercase(Locale.ROOT)
        var debit = 0
        var credit = 0
        debitStrong.forEach { if (b.contains(it)) debit += 3 }
        debitWeak.forEach { if (b.contains(it)) debit += 1 }
        creditStrong.forEach { if (b.contains(it)) credit += 3 }
        creditWeak.forEach { if (b.contains(it)) credit += 1 }
        debitWeakRx.forEach { if (it.containsMatchIn(b)) debit += 1 }
        creditWeakRx.forEach { if (it.containsMatchIn(b)) credit += 1 }

        // "credit card" is a noun phrase, not a credit event.
        if (b.contains("credit card") || b.contains("credit-card")) credit -= 1
        // "debit card" likewise, but a "debit card" message is almost always a debit.
        if (b.contains("debit card")) debit += 1
        // Payment RECEIVED into a credit card is a credit to the card; keep credit.
        if (b.contains("payment received") || b.contains("payment of") && b.contains("received")) credit += 2

        primaryDirection(body)?.let { dir ->
            val margin = if (dir == TransactionType.DEBIT) debit - credit else credit - debit
            return Result(dir, maxOf(3, margin))
        }
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
        Regex("""(?<![a-z])(?:inr|rs\.?|₹|rupees?)\s*:?\s*$NUM""", RegexOption.IGNORE_CASE),
        // "500 Rs", "500/-". The number must stand alone: "On 05-10-26 Rs 500" is not Rs 26.
        Regex("""(?<![\d.,/:\-])$NUM\s*(?:inr\b|rs\b\.?|rupees?\b|/-)""", RegexOption.IGNORE_CASE),
        Regex("""(?:amount|amt)\s*(?:of)?\s*:?\s*$NUM""", RegexOption.IGNORE_CASE),
        // A bare number right after the verb: "debited by 20.0", "is debited for 500.00". Never a date ("on 05-10-26").
        Regex("""\b(?:debited|credited|spent|paid|deducted|withdrawn|transferred|received|charged)\s+(?:for|by|of|with)\s+$NUM(?![\d.,]*[\-/:]\d)(?!\d)""", RegexOption.IGNORE_CASE),
    )

    /** Words that, if they directly precede an amount, mean it is a balance/limit rather than the txn amount. */
    private val balanceContext = Regex(
        """(bal|balance|avl|available|limit|lmt|outstanding|o/s|total due|min due|clr bal)(?:\s+(?:is|was|of|now|as on [\w\-/]+))?\W{0,12}$""",
        RegexOption.IGNORE_CASE,
    )

    /** A number glued to a card/account mask ("XX1234 Rs 750", "A/c 1234 Rs 750") is the account, not the amount. */
    private val accountLead = Regex("""(?:[x*]|(?:a/?c|acct|account|card)(?:\s*(?:no\.?|number))?\s*[:#\-]?\s*)$""", RegexOption.IGNORE_CASE)

    data class Candidate(val value: Double, val index: Int, val isBalance: Boolean)

    fun candidates(body: String): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (rx in patterns) {
            for (m in rx.findAll(body)) {
                // Judge the digits themselves (group 1), not the currency word in front of them.
                val numStart = m.groups[1]!!.range.first
                if (accountLead.containsMatchIn(body.substring(maxOf(0, numStart - 20), numStart))) continue
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
/**
 * Sender IDs ("headers"): an optional two-letter operator/circle prefix, the sender's core, and since TRAI's 2025
 * rules an optional category suffix: S service, T transactional, P promotional, G government. Phones show them as
 * "VM-HDFCBK-S", "HDFCBK-S" or "VM-HDFCBK".
 */
object SenderId {
    private val suffixes = setOf('S', 'T', 'P', 'G')

    private fun parts(sender: String): List<String> {
        var p = sender.trim().uppercase(Locale.ROOT).split('-').filter { it.isNotEmpty() }
        if (p.size >= 2 && p.last().length == 1 && p.last()[0] in suffixes) p = p.dropLast(1)
        if (p.size >= 2 && p.first().length == 2) p = p.drop(1)
        return p
    }

    fun core(sender: String): String = parts(sender).joinToString("-")

    fun suffix(sender: String): Char? {
        val last = sender.trim().uppercase(Locale.ROOT).split('-').filter { it.isNotEmpty() }
        return if (last.size >= 2 && last.last().length == 1 && last.last()[0] in suffixes) last.last()[0] else null
    }
}

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
        // Sender IDs look like "VM-HDFCBK", "AD-ICICIB-S", "HDFCBK-T": compare on the core only.
        val senderCore = SenderId.core(sender).lowercase(Locale.ROOT)
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
    private const val STOP = """(?=\s+(?:on|dt|dated|ref|refno|ref no|upi ref|txn|txnid|txn id|via|using|from|avl|available|bal|balance|info|is|has\b|have\b|was\b|failed\b|not\s+you\b|not\s+u\b|(?:to|for)\s+(?:your|ur)\b|\.|,|-|\(|;|\||$)|\s*$|\.\s|,)"""

    /** Rails and filler that follow "by", "for" or "from" but never name the other party: "by NEFT from ACME CORP". */
    private const val JUNK = """(?:neft|imps|rtgs|upi|transfer|transaction|txn)\b"""

    val patterns: List<Regex> = listOf(
        // A person paying you: "Rahul has sent Rs.500 to you", "Rahul paid you Rs 500".
        Regex("""^\s*(?:dear\s+\w+,?\s+)?([A-Za-z][A-Za-z .]{1,40}?)\s+(?:has\s+)?(?:sent|paid)\s+(?:(?:to\s+)?you\b|(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?\s+to\s+you\b)""", RegexOption.IGNORE_CASE),
        // ICICI: "Acct XX123 debited for Rs 240.00 on 28-Mar-24; DAKSHIN CAFE credited." The payee precedes "credited".
        Regex(""";\s*([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)\s+credited\b"""),
        // UPI VPA e.g. "to VPA merchant@okaxis", "paid to swiggy@ybl"
        Regex("""(?:to|for|at|vpa|payee)\s*:?\s*(?:vpa\s*)?([a-z0-9._\-]+@[a-z]{2,})""", RegexOption.IGNORE_CASE),
        // Axis and others: "UPI/P2M/428612345678/ZOMATO LTD Not you? ...", "UPI/P2A/428612345679/RAHUL SHARMA".
        Regex("""\bUPI/(?:P2[AM]|P2PM|[A-Z]{2,4})/(?:\d{6,22})/([A-Za-z][A-Za-z0-9 .&'\-]{1,40}?)(?=\s+not\s+you\b|\s+not\s+u\b|\s*[/?|;,]|\.\s|\.$|\s+on\b|\s+ref\b|\s+sms\b|\s+call\b|\s+avl\b|\s*$)""", RegexOption.IGNORE_CASE),
        // "UPI/P2M/123456/Merchant Name" or "UPI-Merchant Name-..."
        Regex("""UPI[/:\-]\s*(?:P2[AM][/\-])?(?:\d{6,}[/\-])?(?!P2[AM]\b)([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)(?=[/\-]|\s+on|\s+ref|\s+not\s+you\b|$)"""),
        // "Info: MERCHANT" / "Info- MERCHANT"
        Regex("""\bInfo\s*[:\-]\s*([A-Za-z0-9][A-Za-z0-9 .&'\-]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // "at MERCHANT on", "at MERCHANT."
        Regex("""\bat\s+([A-Za-z0-9][A-Za-z0-9 .&'\-*]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // "to MERCHANT on", "paid to MERCHANT", "sent to MERCHANT", "transferred to MERCHANT"
        Regex("""\b(?:paid |sent |transferred |credited )?to\s+(?!your|ur|a/c|account|card|you\b)([A-Za-z0-9][A-Za-z0-9 .&'\-*]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // "towards MERCHANT", "for MERCHANT"
        // "your ..." is the customer's own account, never a payee: "towards your HDFC Credit Card XX3344".
        Regex("""\b(?:towards|for)\s+(?!rs|inr|₹|a/c|account|card|your|ur\b|my\b|$JUNK)([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
        // Credits: "from MERCHANT", "by MERCHANT", "received from"
        Regex("""\b(?:from|by)\s+(?!your|ur|a/c|account|card|rs\.?\s*\d|inr\s*\d|₹|$JUNK)([A-Za-z0-9][A-Za-z0-9 .&'\-*@]{2,40}?)$STOP""", RegexOption.IGNORE_CASE),
    )

    private val noise = Regex("""\b(upi|imps|neft|rtgs|pos|ecom|txn|ref|no|id|payment|via|the|mr|ms|mrs)\b""", RegexOption.IGNORE_CASE)

    fun extract(body: String, type: TransactionType?): String? {
        val ordered = if (type == TransactionType.CREDIT) patterns.sortedByDescending { it.pattern.contains("from|by") } else patterns
        for (rx in ordered) {
            // A pattern's first hit can clean down to filler ("by NEFT"); its next hit may still name the payee.
            for (m in rx.findAll(body)) {
                val cleaned = clean(m.groupValues[1])
                if (cleaned.length >= 3 && !cleaned.all { it.isDigit() } && cleaned.lowercase(Locale.ROOT) !in fillers) return cleaned
            }
        }
        return null
    }

    /** What is left of "for NEFT transaction" or "by transfer": never a merchant. */
    private val fillers = setOf("transaction", "transfer", "fund transfer", "funds transfer", "transfer from", "transaction via", "account", "bank", "credit", "debit")

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
    private val dateFormats = listOf(
        "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy", "dd-MMM-yy", "dd-MMM-yyyy", "ddMMMyy", "ddMMMyyyy",
        "dd MMM yy", "dd MMM yyyy", "dd.MM.yy", "dd.MM.yyyy", "yyyy-MM-dd", "MMM dd yyyy",
    )
    /**
     * Date and time first: SimpleDateFormat stops reading once its pattern is satisfied, so a date-only pattern would
     * happily read "12-10-24 13:45:12" and drop the time.
     */
    private val dateTimeFormats = dateFormats.flatMap { listOf("$it HH:mm:ss", "$it HH:mm") }
    private val dateRx = Regex(
        """\b(\d{4}-\d{2}-\d{2}(?:[ T:]+\d{1,2}:\d{2}(?::\d{2})?)?|\d{1,2}[-/. ]?(?:\d{1,2}|[A-Za-z]{3})[-/. ]?\d{2,4}(?:[ ,T]+\d{1,2}:\d{2}(?::\d{2})?)?|[A-Za-z]{3}\s\d{1,2},\s\d{4}(?:[ ,]+\d{1,2}:\d{2}(?::\d{2})?)?)\b"""
    )

    /** "12-10-24, 13:45:12", "2026-10-05:14:22:10", "2026-10-05T14:22" all become "<date> <time>". */
    private fun tidy(raw: String): String = raw.trim()
        .replace(Regex("""^(\d{4}-\d{2}-\d{2})[T:]"""), "$1 ")
        .replace(Regex("""^([A-Za-z]{3}\s\d{1,2}),"""), "$1")
        .replace(Regex("""(?<=\d)T(?=\d)"""), " ")
        .replace(Regex("""\s*,\s*|\s+"""), " ")

    fun extract(body: String, fallback: Long): Long {
        val now = System.currentTimeMillis()
        for (m in dateRx.findAll(body)) {
            val text = tidy(m.value)
            val formats = if (text.contains(':')) dateTimeFormats + dateFormats else dateFormats
            for (f in formats) {
                val sdf = SimpleDateFormat(f, Locale.ENGLISH).apply { isLenient = false }
                val d = runCatching { sdf.parse(text) }.getOrNull() ?: continue
                var t = d.time
                // Reject absurd years (two-digit-year parsing oddities) and future dates.
                if (t !in (now - 10L * 365 * 24 * 3600 * 1000)..(now + 24 * 3600 * 1000)) continue
                // Body dates rarely carry a time, and midnight breaks ordering and the duplicate window (and looks like a
                // v1.0.0 row). Same day as the SMS: the SMS's own time. An earlier day: that day at the SMS's time of day.
                if (!f.contains("HH")) t = if (sameDay(t, fallback)) fallback else withTimeOf(t, fallback)
                return t
            }
        }
        return fallback
    }

    private fun withTimeOf(day: Long, time: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = time }
        return java.util.Calendar.getInstance().apply {
            timeInMillis = day
            set(java.util.Calendar.HOUR_OF_DAY, c.get(java.util.Calendar.HOUR_OF_DAY))
            set(java.util.Calendar.MINUTE, c.get(java.util.Calendar.MINUTE))
            set(java.util.Calendar.SECOND, c.get(java.util.Calendar.SECOND))
            set(java.util.Calendar.MILLISECOND, c.get(java.util.Calendar.MILLISECOND))
        }.timeInMillis
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }
}

// ---------------------------------------------------------------------------------------------
// Layer 7: bank / UPI reference number. Two SMS with the same reference are the same payment.
// ---------------------------------------------------------------------------------------------
object RefExtractor {
    val patterns: List<Regex> = listOf(
        // Axis and others: the RRN sits between the UPI kind and the payee, "UPI/P2M/428612345678/ZOMATO LTD".
        Regex("""\bUPI/(?:P2[AM]|P2PM)/(\d{9,22})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:upi|imps|neft|rtgs)?\s*(?:ref(?:erence)?(?:\s*no\.?|\s*number|\s*id)?|rrn|txn\s*id|transaction\s*id|utr|upi)\s*[:#.\-]?\s*([A-Za-z0-9]{6,22})\b""", RegexOption.IGNORE_CASE),
    )

    fun extract(body: String): String? {
        for (rx in patterns) {
            for (m in rx.findAll(body)) {
                val v = m.groupValues[1]
                // Must be mostly digits; words like "Number" are not a reference.
                if (v.count { it.isDigit() } >= 6) return v.uppercase(Locale.ROOT)
            }
        }
        return null
    }

    /**
     * One spelling of a reference for comparing two sources: banks pad statement refs with zeros ("000427712345678") or
     * prefix them. Null for placeholders ("0000000000", "NA", "-") that would make unrelated payments look the same.
     */
    fun normalize(ref: String?): String? {
        val s = ref?.uppercase(Locale.ROOT)?.filter { it.isLetterOrDigit() } ?: return null
        if (s.count { it.isDigit() } < 4) return null
        val v = if (s.all { it.isDigit() }) s.trimStart('0') else s
        return v.takeIf { it.length >= 4 }
    }

    /** Whether two references name the same payment. Long numeric refs compare on their last 12 digits (the UPI RRN). */
    fun same(a: String?, b: String?): Boolean {
        val x = normalize(a) ?: return false
        val y = normalize(b) ?: return false
        if (x == y) return true
        val xs = x.filter { it.isDigit() }
        val ys = y.filter { it.isDigit() }
        return xs.length >= 12 && ys.length >= 12 && xs.takeLast(12) == ys.takeLast(12)
    }
}

// ---------------------------------------------------------------------------------------------
// Layer 8: what the transaction means for your money (see [Flow]). Decides what counts as "spend".
// ---------------------------------------------------------------------------------------------
object FlowClassifier {
    /** A credit card named as such, or "card ending 1234" / "Card XX1234"; never a debit card. */
    private const val CARD = """(?:credit\s*card|(?<!debit\s)card\s*(?:ending|no\b|number|[x*]{2,}|\d{4}))"""
    private const val AMT = """(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?"""
    private val cardBillPayment = Regex(
        """\b(payment (?:of .{0,20})?(?:received|credited) (?:towards|to|for) your .{0,20}(?:credit )?card|credit card (?:bill )?payment|cc (?:bill )?payment|card bill|bill ?desk.{0,30}card|towards your (?:credit )?card|payment to (?:your )?(?:credit )?card|cred club|cred\.club)""" +
            // Card issuer receipts: "Payment of Rs 15,000.00 has been credited to your SBI Card ending 1234", "Payment of INR
            // 15,000.00 received on your Axis Bank Credit Card XX1234", "... received for Axis Bank Credit Card no. XX1234".
            """|\bpayment\s+(?:of\s+$AMT\s+)?(?:has\s+been\s+|is\s+|was\s+)?(?:received|credited)\s+(?:towards|to|for|on|in|into|against)\s+(?:your\s+)?(?:[\w.&]+\s+){0,4}?$CARD""" +
            // "We have received payment of Rs.15,000.00 towards your SBI Credit Card ending with 1234"
            """|\b(?:received|credited)\s+(?:a\s+|the\s+|your\s+)?payment\s+of\s+$AMT\s+(?:towards|to|for|on|in|against)\s+(?:your\s+)?(?:[\w.&]+\s+){0,4}?$CARD""" +
            // The bank side: "Payment of Rs 15,000 to your Credit Card XX4455 was successful", "Rs 5,000 paid to your HDFC
            // Bank Credit Card", a CRED or ccpay UPI handle paying a card.
            """|\bpayment\s+of\s+$AMT\s+(?:towards|to)\s+your\s+(?:[\w.&]+\s+){0,4}?$CARD""" +
            """|\bpaid\s+(?:to|towards)\s+your\s+(?:[\w.&]+\s+){0,4}?$CARD""" +
            // "paid to CRED for your HDFC Bank Credit Card", but not "via CRED Pay using your credit card at Swiggy".
            """|\bccpay\b|\bcred\b.{0,20}?\s(?:for|towards)\s+(?:your\s+)?(?:[\w.&]+\s+){0,4}?$CARD""",
        RegexOption.IGNORE_CASE,
    )
    private val selfTransfer = Regex("""\b(self[\s-]?transfer|own account|to self|own a/?c|added to (?:your )?wallet|add(?:ed)? money|wallet (?:top[\s-]?up|load))\b""", RegexOption.IGNORE_CASE)
    private val investment = Regex("""\b(mutual fund|sip|zerodha|groww|upstox|kuvera|smallcase|icclearing|indian clearing|cdsl|nsdl|ppf|nps|fixed deposit|fd|rd|etmoney|angel one|5paisa|nse|bse)\b""", RegexOption.IGNORE_CASE)

    /** Bank footers sell deposits and funds ("Earn 7% on FD", "Invest in ...", "T&C apply"). They say nothing about this payment. */
    private val marketing = Regex("""\b(?:earn|t\s*&\s*c|tnc|apply\s+now|offers?|download|know\s+more|visit|click|open\s+(?:an?\s+)?(?:fd|rd|account)|book\s+(?:an?\s+)?(?:fd|rd))\b""", RegexOption.IGNORE_CASE)
    /** "Invest in ..." / "Invest now" only as a whole sentence: "debited to invest in a fund" is the payment itself. */
    private val investPitch = Regex("""^\s*invest\s+(?:in|now|today)\b""", RegexOption.IGNORE_CASE)
    private val sentences = Regex("""(?<=[.!?|])\s+""")

    /**
     * The body without its marketing: a sentence that reports no money moving and sells something is dropped, and a
     * sentence that reports the payment is cut where the sales pitch starts. What the investment words may look at.
     */
    fun transactionClause(body: String): String = body.split(sentences).mapNotNull { s ->
        if (investPitch.containsMatchIn(s)) return@mapNotNull null
        val cut = marketing.find(s)?.range?.first ?: return@mapNotNull s
        s.substring(0, cut).takeIf { TextFilters.completedVerb.containsMatchIn(it) }
    }.joinToString(" ")
    private val refund = Regex("""\b(refund|refunded|reversal|reversed|cashback|charge ?back)\b""", RegexOption.IGNORE_CASE)
    private val income = Regex("""\b(salary|payroll|interest|dividend|bonus|stipend|pension)\b""", RegexOption.IGNORE_CASE)
    private val cash = Regex("""\b(atm|cash (?:wdl|withdrawal|withdrawn)|cwdr)\b""", RegexOption.IGNORE_CASE)

    /**
     * [body] is the SMS text when available (pass "" for manual entries). [category] is the rule-based
     * category, used as a secondary hint (e.g. INVESTMENT category => INVESTMENT flow).
     */
    fun classify(type: TransactionType, body: String, merchant: String, category: Category): Flow {
        val clean = SmsText.normalize(body)
        val text = "$clean $merchant"
        // Investment words are read from the payment itself, not from a footer selling FDs.
        val invested = investment.containsMatchIn("${transactionClause(clean)} $merchant")
        return when (type) {
            TransactionType.DEBIT -> when {
                cardBillPayment.containsMatchIn(text) -> Flow.TRANSFER
                selfTransfer.containsMatchIn(text) -> Flow.TRANSFER
                cash.containsMatchIn(text) || category == Category.ATM -> Flow.CASH
                invested || category == Category.INVESTMENT -> Flow.INVESTMENT
                category == Category.TRANSFER -> Flow.TRANSFER
                else -> Flow.EXPENSE
            }
            TransactionType.CREDIT -> when {
                cardBillPayment.containsMatchIn(text) -> Flow.TRANSFER
                refund.containsMatchIn(text) -> Flow.REFUND
                selfTransfer.containsMatchIn(text) -> Flow.TRANSFER
                invested -> Flow.INVESTMENT
                income.containsMatchIn(text) -> Flow.INCOME
                else -> Flow.INCOME
            }
        }
    }
}
