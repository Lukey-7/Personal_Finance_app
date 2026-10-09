package com.pft.financetracker.domain.tax

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.PayerClassifier
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Indian income-tax deductions (old regime) people most often pay for through their bank. [limitPaise] null: no fixed cap here. */
enum class TaxSection(val code: String, val label: String, val limitPaise: Long?) {
    S80C("80C", "Investments, insurance, tuition", 1_50_000_00),
    S80CCD1B("80CCD(1B)", "NPS (extra)", 50_000_00),
    S80D("80D", "Health insurance", 25_000_00),
    S80E("80E", "Education loan interest", null),
    S24B("24(b)", "Home loan interest", 2_00_000_00),
    S80G("80G", "Donations", null),
    HRA("HRA", "Rent paid", null);

    companion object {
        fun fromName(n: String?): TaxSection? = entries.firstOrNull { it.name == n }
    }
}

/** The Indian financial year that starts on 1 April of [startYear]. */
data class FinancialYear(val startYear: Int) {
    val start: LocalDate get() = LocalDate.of(startYear, 4, 1)
    val endInclusive: LocalDate get() = LocalDate.of(startYear + 1, 3, 31)
    val label: String get() = "FY $startYear-${(startYear + 1) % 100}"

    companion object {
        fun of(d: LocalDate) = FinancialYear(if (d.monthValue >= 4) d.year else d.year - 1)
    }
}

/**
 * One section's payments in a year. [totalPaise] is what was paid, net of any [refundedPaise] that came back.
 * [claimablePaise] is the part the headline counts: capped at the section's limit; zero for rent (the HRA exemption
 * depends on salary and city); for donations only what is known to count in full (PM CARES and the like).
 * [transactionIds] holds the payments and any refunds against them, oldest first.
 */
data class SectionTotal(
    val section: TaxSection,
    val totalPaise: Long,
    val claimablePaise: Long,
    val transactionIds: List<Long>,
    val refundedPaise: Long = 0,
)

/** Why a payment might count but can't be told apart from one that doesn't. Each needs the person to confirm. */
enum class PossibleKind(val section: TaxSection) {
    /** A mutual fund or SIP: only ELSS (tax saver) funds count under 80C. */
    ELSS(TaxSection.S80C),
    /** A home loan EMI: interest under 24(b), principal under 80C; the split is not in the SMS. */
    HOME_LOAN_EMI(TaxSection.S24B),
    /** About the same amount to the same person most months. */
    RENT(TaxSection.HRA),
    /** School or college fees: only the tuition part counts. */
    TUITION(TaxSection.S80C),
    /** Paid to an NPS record-keeper without saying NPS. */
    NPS(TaxSection.S80CCD1B),
    /** A general insurer that sells motor and travel cover as well as health. */
    INSURANCE(TaxSection.S80D),
    /** A crowdfunding site, temple or trust: counts only if approved under 80G. */
    DONATION(TaxSection.S80G),
    /** An education loan EMI: only the interest counts. */
    EDUCATION_LOAN_EMI(TaxSection.S80E),
}

/** What the rules make of one payment: a [section], and [possible] set when it needs confirming. */
data class TaxGuess(val section: TaxSection, val possible: PossibleKind? = null) {
    val sure: Boolean get() = possible == null
}

/** Payments to one payee that may count, grouped so one tap can confirm or dismiss them all. */
data class PossibleGroup(val kind: PossibleKind, val payee: String, val transactionIds: List<Long>, val totalPaise: Long) {
    val section: TaxSection get() = kind.section
}

/** A year's picture: the sections found or confirmed, and the payments that may count but need a yes or no. */
data class TaxReport(val totals: List<SectionTotal>, val possible: List<PossibleGroup>) {
    /** What may count toward deductions: each section up to its limit, rent and most donations left out. */
    val headlinePaise: Long get() = totals.sumOf { it.claimablePaise }
}

/**
 * Suggests which payments may count toward a tax deduction, from the payee's name, the note, the flow and the category.
 * For the person's records only: the rules are a starting point, any payment can be tagged or untagged by hand, and
 * nothing here is tax advice. Payee names arrive cleaned ("HDFC Life", "Licindia" from licindia@…), so the patterns
 * allow names written with or without spaces.
 */
object TaxTagger {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val eduLoanInterest = rx("""education ?loan ?int(erest)?\b|edu ?loan ?int(erest)?\b""")
    private val eduLoan = rx("""education ?loan|\bedu ?loan|student ?loan|credila|avanse|incred ?edu|propelld|auxilo""")
    private val homeLoanInterest = rx("""(home|housing) ?loan ?int(erest)?\b""")
    private val homeLoanPrincipal = rx("""(home|housing) ?loan ?principal""")
    private val homeLoan = rx(
        """(home|housing) ?loan|lic ?housing|lichfl|lic ?hfl|hdfc ?ltd|pnb ?housing|bajaj ?housing|tata ?capital ?housing|aavas|""" +
            """can ?fin ?homes|indiabulls ?housing|piramal ?(capital ?)?housing|aadhar ?housing|aptus|repco ?home|sammaan ?capital|""" +
            """godrej ?housing|icici ?hfc|home ?first|sbi ?home ?loan"""
    )
    /** "EMI debited for loan a/c …": any loan; only a large one with no other kind named is offered as a home loan. */
    private val loanEmi = rx("""\bemi\b|loan ?a/?c|loan ?account|\bloan\b""")
    private val otherLoan = rx(
        """\b(car|vehicle|auto ?loan|two ?wheeler|bike|personal|gold|consumer|durable|tractor|business|credit ?card|education|edu)\b|""" +
            """bajaj ?fin|kreditbee|moneyview|navi ?loan|lazypay|zestmoney"""
    )

    private val npsTier2 = rx("""tier ?(2|ii)\b""")
    private val nps = rx("""\bnps\b|\benps\b|e-nps|national ?pension|pfrda|atal ?pension|\bapy\b""")
    private val npsMaybe = rx("""protean|nsdl ?e-?gov|\bcra\b|kfin ?cra|karvy ?cra""")
    private val panFee = rx("""\bpan\b|pan ?card""")

    private val healthOnly = rx(
        """health ?insurance|mediclaim|star ?health|care ?health|careinsurance|care ?insurance|religare ?health|niva ?bupa|""" +
            """max ?bupa|manipal ?cigna|cigna ?ttk|aditya ?birla ?health|abhicl|activ ?health|preventive ?health|health ?policy|family ?floater"""
    )
    private val generalInsurer = rx(
        """hdfc ?ergo|icici ?lombard|tata ?aig|bajaj ?allianz|\backo\b|go ?digit|digit ?insurance|policy ?bazaar|new ?india ?assurance|""" +
            """united ?india ?insurance|oriental ?insurance|national ?insurance|sbi ?general|reliance ?general|iffco ?tokio|future ?generali|""" +
            """royal ?sundaram|chola ?ms|cholamandalam ?ms|universal ?sompo|liberty ?general|magma ?hdi|\bzuno\b|navi ?general|kotak ?general|insurance ?dekho"""
    )
    private val healthWords = rx("""\bhealth\b|optima|medisure|mediclaim|hospital""")
    private val notHealth = rx(
        """motor|\bcar\b|\bbike\b|two ?wheeler|vehicle|\btravel|\btrip\b|home ?insurance|property|\bfire\b|\bshop\b|marine|gadget|""" +
            """mobile|phone|cyber|\bpet\b|\bpa\b|personal ?accident"""
    )

    private val lifeInsurer = rx(
        """\blic\b|licindia|lic ?premium|life ?insurance|hdfc ?life|hdfc ?standard ?life|sbi ?life|icici ?pru(dential)? ?life|ipru ?life|""" +
            """max ?life|axis ?max ?life|tata ?aia|bajaj ?allianz ?life|bajaj ?life|kotak ?(mahindra ?)?life|pnb ?met ?life|\bmetlife|""" +
            """birla ?sun ?life ?insurance|\babsli\b|canara ?hsbc ?life|reliance ?nippon ?life|bharti ?axa ?life|aegon ?life|bandhan ?life|""" +
            """edelweiss ?(tokio ?)?life|future ?generali ?life|indiafirst ?life|pramerica ?life|shriram ?life|star ?union ?dai|ageas ?federal|""" +
            """term ?(plan|insurance)"""
    )
    /** LIC's mutual fund and housing finance arms are not life cover. */
    private val licNotLife = rx("""lic ?(mf|mutual)|lic ?housing|lichfl|lic ?hfl""")
    private val savings80C = rx(
        """\bppf\b|public ?provident|sukanya|\bssy\b|\bnsc\b|national ?savings ?cert|\bepf\b|\bvpf\b|\bscss\b|senior ?citizens? ?savings|""" +
            """\bulip\b|\belss\b|tax ?sav(er|ing)"""
    )

    private val fund = rx(
        """\bsip\b|mutual ?fund|\bmf\b|\bamc\b|asset ?management|bse ?star|bsestar|\biccl\b|icclearing|indian ?clearing|\bcams\b|camsonline|""" +
            """kfin|karvy|zerodha|groww|nextbillion|kuvera|paytm ?money|et ?money|\bmfu\b|mf ?utilities|scripbox|fundsindia|indmoney|ppfas|""" +
            """parag ?parikh|mirae|nippon ?india|\bquant\b|\buti\b|\bdsp\b|franklin|canara ?robeco|motilal|invesco|bandhan|edelweiss|""" +
            """axis ?(mf|mutual)|hdfc ?(mf|mutual)|sbi ?(mf|mutual|funds)|icici ?pru(dential)?|aditya ?birla ?sun ?life|kotak ?(mf|mutual)|""" +
            """tata ?(mf|mutual)|hsbc ?(mf|mutual)|lic ?(mf|mutual)"""
    )
    private val notFund = rx("""\b(fd|fixed ?deposit|rd|recurring ?deposit|gold|sgb|crypto|bitcoin|us ?stocks?|vested|smallcase|ipo|futures|options|margin)\b""")

    private val rent = rx("""\brent\b|nobroker""")
    private val notHouseRent = rx(
        """\b(car|bike|cab|scooter|scooty|vehicle|cycle|bicycle|camera|furniture|appliance|equipment|costume|dress|generator|tent|locker|""" +
            """parking|shop|office|godown|warehouse|received)\b|zoomcar|yulu|bounce|revv|drivezy|rentomojo|furlenco"""
    )

    private val tuition = rx("""tuition ?fees?|school ?fees?|college ?fees?|university ?fees?|term ?fees?|\bschool\b|\bcollege\b|university|vidyalaya|vidyalay|kendriya|convent""")
    private val notTuition = rx(
        """coaching|classes|tutorials?|tuitions\b|academy|institute|byju|unacademy|vedantu|physics ?wallah|udemy|coursera|upgrad|simplilearn|""" +
            """whitehat|allen|aakash|fiitjee|hostel|\bmess\b|canteen|bus ?fees?|transport|uniform|\bbooks?\b|stationery|exam ?fees?"""
    )

    private val donationFull = rx("""pm ?cares|\bpmnrf\b|prime ?minister'?s? ?(national )?relief|national ?defen[cs]e ?fund|chief ?minister'?s? ?relief|\bcmrf\b""")
    private val donation = rx(
        """donation|donate|charity|charitable|\bngo\b|giveindia|give ?india|\bcry\b|akshaya ?patra|goonj|helpage|smile ?foundation|unicef|""" +
            """save ?the ?children|teach ?for ?india|nanhi ?kali"""
    )
    private val donationMaybe = rx("""ketto|milaap|impact ?guru|\btemple\b|mandir|gurudwara|gurdwara|church|masjid|devasthan|\btrust\b|foundation""")

    /** Rent is usually a round amount well above day-to-day payments to friends. */
    private const val MIN_RENT_PAISE = 5_000_00L
    /** A loan EMI below this is unlikely to be a home loan. */
    private const val MIN_HOME_EMI_PAISE = 10_000_00L

    private fun text(t: Transaction) = t.merchant + " " + (t.note ?: "")

    /** The section a payment counts toward when the rules are sure, or null. Possible matches are left out: see [guess]. */
    fun suggest(t: Transaction): TaxSection? = guess(t)?.takeIf { it.sure }?.section

    /** What the rules make of one payment out, sure or possible; null when it doesn't look like a deduction. */
    fun guess(t: Transaction): TaxGuess? {
        if (t.type != TransactionType.DEBIT || t.needsReview) return null
        if (t.flow != Flow.EXPENSE && t.flow != Flow.INVESTMENT && t.flow != Flow.TRANSFER) return null
        return classify(t)
    }

    /** The section a refund or reversal belongs to, so it can come off that section's total; only sure matches. */
    fun refundSection(t: Transaction): TaxSection? {
        if (t.type != TransactionType.CREDIT || t.flow != Flow.REFUND || t.needsReview) return null
        return classify(t)?.takeIf { it.sure }?.section
    }

    private fun classify(t: Transaction): TaxGuess? {
        val s = text(t)
        val investment = t.flow == Flow.INVESTMENT || t.category == Category.INVESTMENT
        val expense = t.flow == Flow.EXPENSE || (t.type == TransactionType.CREDIT && t.flow == Flow.REFUND)

        // Loans: the specific wording first, so "education loan interest" never reads as "loan".
        if (eduLoanInterest.containsMatchIn(s)) return TaxGuess(TaxSection.S80E)
        if (homeLoanInterest.containsMatchIn(s)) return TaxGuess(TaxSection.S24B)
        if (homeLoanPrincipal.containsMatchIn(s)) return TaxGuess(TaxSection.S80C)
        if (eduLoan.containsMatchIn(s)) return TaxGuess(TaxSection.S80E, PossibleKind.EDUCATION_LOAN_EMI)
        if (homeLoan.containsMatchIn(s)) return TaxGuess(TaxSection.S24B, PossibleKind.HOME_LOAN_EMI)

        // NPS: Tier I only; Tier II is a savings account with no deduction.
        if (nps.containsMatchIn(s)) return if (npsTier2.containsMatchIn(s)) null else TaxGuess(TaxSection.S80CCD1B)
        if (npsMaybe.containsMatchIn(s) && !panFee.containsMatchIn(s) && !npsTier2.containsMatchIn(s)) return TaxGuess(TaxSection.S80CCD1B, PossibleKind.NPS)

        // Insurance: health-only insurers are sure; life insurers are 80C; general insurers may be motor or travel.
        if (healthOnly.containsMatchIn(s)) return TaxGuess(TaxSection.S80D)
        if (lifeInsurer.containsMatchIn(s) && !licNotLife.containsMatchIn(s)) return TaxGuess(TaxSection.S80C)
        if (savings80C.containsMatchIn(s)) return TaxGuess(TaxSection.S80C)
        if (generalInsurer.containsMatchIn(s)) return when {
            notHealth.containsMatchIn(s) -> null
            healthWords.containsMatchIn(s) -> TaxGuess(TaxSection.S80D)
            else -> TaxGuess(TaxSection.S80D, PossibleKind.INSURANCE)
        }

        // Funds: ELSS can't be told apart from any other fund, so these always need a yes.
        if (investment && fund.containsMatchIn(s) && !notFund.containsMatchIn(s)) return TaxGuess(TaxSection.S80C, PossibleKind.ELSS)
        if (t.flow == Flow.INVESTMENT) return null

        // Rent: not a car or a shop; a "rent" transfer may be to your own account, so it only counts once confirmed.
        if (rent.containsMatchIn(s) && !notHouseRent.containsMatchIn(s)) {
            return if (expense) TaxGuess(TaxSection.HRA) else TaxGuess(TaxSection.HRA, PossibleKind.RENT)
        }

        if (tuition.containsMatchIn(s) && !notTuition.containsMatchIn(s)) return TaxGuess(TaxSection.S80C, PossibleKind.TUITION)

        if (donationFull.containsMatchIn(s) || donation.containsMatchIn(s)) return TaxGuess(TaxSection.S80G)
        if (expense && donationMaybe.containsMatchIn(s)) return TaxGuess(TaxSection.S80G, PossibleKind.DONATION)

        if (t.type == TransactionType.DEBIT && t.amountPaise >= MIN_HOME_EMI_PAISE && loanEmi.containsMatchIn(s) && !otherLoan.containsMatchIn(s)) {
            return TaxGuess(TaxSection.S24B, PossibleKind.HOME_LOAN_EMI)
        }
        return null
    }

    /** A donation known to count in full (PM CARES, the national and state relief funds). */
    fun countsInFull(t: Transaction): Boolean = donationFull.containsMatchIn(text(t))

    private fun isPerson(t: Transaction): Boolean =
        (t.counterpartyKind ?: PayerClassifier.classify("", t.merchant, t.type)) == CounterpartyKind.PERSON

    /**
     * Payments that look like rent to a landlord: about the same amount (within 10%) to the same person in at least
     * three months in a row, allowing one skipped month. Reads the whole history, so a lease that began last year still
     * shows from April. Payments the rules already place are left alone.
     */
    fun rentLikeIds(txns: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): Set<Long> {
        val candidates = txns.filter {
            it.type == TransactionType.DEBIT && !it.needsReview && (it.flow == Flow.EXPENSE || it.flow == Flow.TRANSFER) &&
                it.amountPaise >= MIN_RENT_PAISE && guess(it) == null && isPerson(it)
        }
        val out = HashSet<Long>()
        candidates.groupBy { PayerClassifier.partyKey(it.merchant, it.id) }.values.forEach { group ->
            if (group.size < 3) return@forEach
            for (t in group) {
                if (t.id in out) continue
                val near = group.filter { Math.abs(it.amountPaise - t.amountPaise) * 10 <= t.amountPaise }
                val months = near.map { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }.toSortedSet().toList()
                // Rent is once a month: many similar payments in the same months look more like a friend than a landlord.
                if (near.size > months.size * 2) continue
                var run = 1; var best = 1
                for (i in 1 until months.size) {
                    run = if (months[i - 1].plusMonths(2) >= months[i]) run + 1 else 1
                    best = maxOf(best, run)
                }
                if (best >= 3) near.forEach { out += it.id }
            }
        }
        return out
    }

    /** Totals per section for [fy]. [manual] holds the person's own tags by transaction id; a null value means "not a deduction". */
    fun summary(txns: List<Transaction>, fy: FinancialYear, manual: Map<Long, TaxSection?>, zone: ZoneId = ZoneId.systemDefault()): List<SectionTotal> =
        report(txns, fy, manual, zone).totals

    /**
     * The year's sections and the payments to check. A tag the person set always wins; otherwise sure matches count,
     * possible ones wait in [TaxReport.possible], and refunds of a counted premium or fee come off its section.
     */
    fun report(txns: List<Transaction>, fy: FinancialYear, manual: Map<Long, TaxSection?>, zone: ZoneId = ZoneId.systemDefault()): TaxReport {
        val inYear = txns.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().let { d -> !d.isBefore(fy.start) && !d.isAfter(fy.endInclusive) } }
        val rentIds by lazy { rentLikeIds(txns, zone) }
        val paid = HashMap<TaxSection, MutableList<Transaction>>()
        val back = HashMap<TaxSection, MutableList<Transaction>>()
        val maybe = LinkedHashMap<Pair<PossibleKind, String>, MutableList<Transaction>>()
        fun MutableMap<TaxSection, MutableList<Transaction>>.add(s: TaxSection, t: Transaction) = getOrPut(s) { ArrayList() }.add(t)

        for (t in inYear) {
            if (manual.containsKey(t.id)) {
                val s = manual[t.id] ?: continue
                if (t.type == TransactionType.CREDIT) back.add(s, t) else paid.add(s, t)
                continue
            }
            if (t.type == TransactionType.CREDIT) {
                refundSection(t)?.let { back.add(it, t) }
                continue
            }
            val g = guess(t)
            val kind = when {
                g == null -> if (t.id in rentIds) PossibleKind.RENT else null
                g.sure -> { paid.add(g.section, t); null }
                else -> g.possible
            }
            if (kind != null) maybe.getOrPut(kind to PayerClassifier.partyKey(t.merchant, t.id)) { ArrayList() }.add(t)
        }

        val totals = TaxSection.entries.mapNotNull { s ->
            val out = paid[s].orEmpty()
            if (out.isEmpty()) return@mapNotNull null
            val gross = out.sumOf { it.amountPaise }
            val refunded = minOf(back[s].orEmpty().sumOf { it.amountPaise }, gross)
            val net = gross - refunded
            val claimable = when (s) {
                TaxSection.HRA -> 0L
                TaxSection.S80G -> minOf(net, out.filter { countsInFull(it) }.sumOf { it.amountPaise })
                else -> s.limitPaise?.let { minOf(net, it) } ?: net
            }
            val ids = (out + back[s].orEmpty()).sortedBy { it.timestamp }.map { it.id }
            SectionTotal(s, net, claimable, ids, refunded)
        }
        val possible = maybe.map { (key, list) ->
            val sorted = list.sortedBy { it.timestamp }
            PossibleGroup(key.first, sorted.last().merchant, sorted.map { it.id }, sorted.sumOf { it.amountPaise })
        }.sortedByDescending { it.totalPaise }
        return TaxReport(totals, possible)
    }
}
