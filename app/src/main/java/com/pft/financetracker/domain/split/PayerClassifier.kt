package com.pft.financetracker.domain.split

import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.TransactionType
import java.util.Locale

/**
 * Is the other side of a transaction a person or an organisation? Only money from a person can be a friend paying
 * back a share, so this is the first gate of split detection. It reads the text the bank wrote (an SMS body or a
 * statement narration) plus the merchant name the parser extracted. No bank templates: the signals are the ones
 * every Indian bank and UPI app shares.
 *
 * Organisation: salary / refund / interest wording, company suffixes (Pvt Ltd, Technologies, ...), UPI merchant
 * markers (P2M, merchant QR handles, payment gateways), or a merchant the categorizer recognises.
 * Person: UPI person-to-person markers (P2A, P2P), a mobile-number VPA, a personal-handle VPA, or a name that looks
 * like one to three plain words ("RAHUL SHARMA").
 */
object PayerClassifier {
    /** What the money is (checked against the whole text): these are never a friend paying back. */
    private val natureWords = Regex(
        """\b(salary|payroll|sal\s+for|stipend|pension|interest|int\.?\s*pd|dividend|refund|reversal|reversed|cash ?back|charge ?back|nach|ach|ecs|emi)\b""",
        RegexOption.IGNORE_CASE,
    )
    /**
     * Who it is (checked against the counterparty name only: every SMS mentions the customer's own bank, so
     * "bank" in the body says nothing about the sender).
     */
    private val businessWords = Regex(
        """\b(pvt|private|ltd|limited|llp|inc|corp|corporation|company|co\.|technologies|technology|tech|solutions|services|systems|""" +
            """enterprises|industries|infotech|software|labs|ventures|retail|foods|restaurant|hotel|hospitality|stores?|mart|traders|""" +
            """agency|associates|consultancy|bank|insurance|mutual fund|securities|finance|financial|capital|broking|clearing|""" +
            """govt|government|municipal|electricity|university|college|school|hospital|pharma|pharmacy|loan|""" +
            """medicals?|medicos?|chemists?|druggists?|clinic|diagnostics?|motors|automobiles?|autos|electronics|electricals|""" +
            """jewell?ers|jewell?ery|sweets|bakers|bakery|caterers|travels|tours|garments|textiles|fashions?|boutique|salon|""" +
            """kirana|provisions?|supermarket|hardware|opticals?|furnishings?|furniture|constructions?|builders|realty)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantMarkers = Regex("""\b(p2m|pos|ecom|mandate|autopay|billdesk|razorpay|cashfree|payu|ccavenue|paytm ?qr|bharatpe|pinelabs|instamojo|juspay)\b""", RegexOption.IGNORE_CASE)
    private val personMarkers = Regex("""\b(p2a|p2p|upi/cr|upi/dr|by transfer|received from|sent by|transferred to your|from phone|payment from ph)\b""", RegexOption.IGNORE_CASE)

    private val vpa = Regex("""([a-z0-9][a-z0-9._\-]{1,60})@([a-z][a-z0-9]{1,20})""", RegexOption.IGNORE_CASE)
    /** Handles that consumer UPI apps hand to individuals. Merchant QR codes use distinct local parts, checked below. */
    private val personalHandles = setOf(
        "okaxis", "okhdfcbank", "okhdfc", "okicici", "oksbi", "ybl", "ibl", "axl", "apl", "upi", "paytm", "ptyes", "ptaxis", "pthdfc", "ptsbi",
        "axisbank", "icici", "hdfcbank", "sbi", "kotak", "kbl", "fbl", "idfcbank", "yesbank", "rbl", "indus", "freecharge", "jupiteraxis",
        "fam", "slc", "timecosmos", "waaxis", "wahdfcbank", "waicici", "wasbi", "abfspay", "pingpay", "nyes", "barodampay", "pnb", "boi",
        "unionbank", "cnrb", "aubank", "dbs", "hsbc", "sc", "citi", "idbi", "cbin", "ikwik", "mahb", "ezeepay", "superyes", "naviaxis",
    )
    /** Local parts that belong to merchant QRs / collect accounts rather than people. */
    private val merchantLocal = Regex("""^(q\d{5,}|paytmqr\w*|paytm-\d+|bharatpe\w*|mab\.\w+|\d{4,}\.\w+|.*(?:merchant|store|shop|qr|pos|payments?|pay|biz|ltd|pvt|technolog)\w*)$""", RegexOption.IGNORE_CASE)
    private val mobile = Regex("""^(?:91)?[6-9]\d{9}$""")

    /** Words that say nothing about who someone is, including the placeholders used for unnamed senders. */
    private val stop = setOf("upi", "imps", "neft", "rtgs", "mr", "mrs", "ms", "dr", "shri", "smt", "kumar", "transfer", "payment", "from", "to", "by", "the", "via",
        "credit", "debit", "friend", "received")
    private val longWord = Regex("""[a-z]{3,}""")

    fun classify(text: String, merchant: String, type: TransactionType): CounterpartyKind {
        val t = "$text $merchant"
        if (natureWords.containsMatchIn(t) || merchantMarkers.containsMatchIn(t) || businessWords.containsMatchIn(merchant)) return CounterpartyKind.ORGANISATION
        // A merchant the keyword rules recognise (Swiggy, Uber, Airtel, ...) is a business, whatever else the text says.
        val cat = Categorizer.categorize(merchant, type)
        if (cat != Category.OTHER && cat != Category.INCOME && cat != Category.TRANSFER) return CounterpartyKind.ORGANISATION

        vpa.findAll(t).forEach { m ->
            val local = m.groupValues[1].lowercase(Locale.ROOT)
            val handle = m.groupValues[2].lowercase(Locale.ROOT)
            if (merchantLocal.matches(local) || businessWords.containsMatchIn(local.replace(Regex("""[._\-]"""), " "))) return CounterpartyKind.ORGANISATION
            if (mobile.matches(local.replace(Regex("""[^0-9]"""), "")) && local.count { it.isDigit() } >= 10) return CounterpartyKind.PERSON
            if (handle in personalHandles && local.count { it.isLetter() } >= 3) return CounterpartyKind.PERSON
        }
        if (personMarkers.containsMatchIn(t) && looksLikeName(merchant)) return CounterpartyKind.PERSON
        if (looksLikeName(merchant)) return CounterpartyKind.PERSON
        return CounterpartyKind.UNKNOWN
    }

    /** "Rahul", "RAHUL SHARMA", "Priya K Nair": one to four alphabetic words, no digits, no business words. */
    fun looksLikeName(s: String): Boolean {
        val words = s.trim().split(Regex("""[\s.]+""")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 4) return false
        if (words.any { w -> w.any { it.isDigit() } || !w.all { it.isLetter() } }) return false
        if (words.all { it.length <= 1 }) return false
        val lower = words.map { it.lowercase(Locale.ROOT) }
        if (lower.first() in setOf("payment", "credit", "debit", "transfer", "cash", "atm", "bank", "salary", "refund", "interest")) return false
        return !businessWords.containsMatchIn(s) && !natureWords.containsMatchIn(s)
    }

    /**
     * A stable key for "the same sender" across alerts: the humanised name's meaningful words, so "RAHUL SHARMA" from
     * a statement and "Rahul" from an SMS are recognised as one person when needed (see [sameParty]).
     */
    fun partyTokens(name: String): Set<String> = name.lowercase(Locale.ROOT)
        .split(nonLetters).filter { it.length >= 3 && it !in stop }.toSet()
    private val nonLetters = Regex("""[^a-z]+""")

    /**
     * "The same sender" for grouping transfers: the name's meaningful words, or, when it has none ("AK", a bare phone
     * number), the name itself. A generic placeholder ("Credit", "UPI") is never one person: it falls back to the row.
     */
    fun partyKey(name: String, rowId: Long): String {
        partyTokens(name).sorted().joinToString(" ").takeIf { it.isNotBlank() }?.let { return it }
        val lower = name.lowercase(Locale.ROOT)
        // Only short names and numbers ("AK", "9876543210") stand for one person as written. A name made only of
        // stop words ("Credit", "Mr Kumar") could be anyone: each row is its own sender.
        if (longWord.containsMatchIn(lower)) return "#$rowId"
        val raw = lower.filter { it.isLetterOrDigit() }
        return if (raw.isEmpty()) "#$rowId" else "=$raw"
    }

    /** Two names plausibly describe the same person or business: they share a meaningful word. */
    fun sameParty(a: String, b: String): Boolean {
        val ta = partyTokens(a); val tb = partyTokens(b)
        return ta.isNotEmpty() && tb.isNotEmpty() && ta.intersect(tb).isNotEmpty()
    }
}
