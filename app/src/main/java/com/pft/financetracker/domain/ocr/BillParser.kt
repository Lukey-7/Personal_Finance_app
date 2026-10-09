package com.pft.financetracker.domain.ocr

import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.split.BillItem
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Turns raw OCR text (one line per printed line) into a structured bill. Pure Kotlin, unit tested with
 * sample receipt text. OCR is never perfect, so the caller shows every field for correction before
 * anything is calculated.
 */
data class ParsedBill(
    val merchant: String?,
    val date: Long?,
    val totalPaise: Long?,
    val subtotalPaise: Long?,
    val taxPaise: Long,
    val servicePaise: Long,
    val discountPaise: Long,
    val items: List<BillItem>,
    val rawLines: List<String>,
) {
    /** Items + extras should equal the total. Zero means the numbers reconcile. */
    val reconciliationGapPaise: Long?
        get() {
            val t = totalPaise ?: return null
            if (items.isEmpty()) return null
            return t - (items.sumOf { it.pricePaise * it.quantity } + taxPaise + servicePaise - discountPaise)
        }
}

object BillParser {
    // "1,234.00", "1234", "1234.5", with optional currency marks. Trailing "/-" common on Indian bills.
    // Lookbehind stops the trailing digit of codes like GSTIN 29ABCDE1234F1Z5 from reading as an amount.
    private const val AMT = """(?<![A-Za-z0-9,])(?<!\d\.)((?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d{1,2})?)"""
    private val amountAtEnd = Regex("""(?:rs\.?|inr|₹|रु\.?|रू\.?|\$)?\s*$AMT\s*(?:/-)?\s*$""", RegexOption.IGNORE_CASE)
    private val standaloneAmount = Regex("""^-?\s*(?:rs\.?|inr|₹|रु\.?|रू\.?)?\s*[\d,]+(?:\.\d{1,2})?\s*(?:/-)?$""", RegexOption.IGNORE_CASE)
    private val anyAmount = Regex("""(?:rs\.?|inr|₹|रु\.?|रू\.?)?\s*$AMT""", RegexOption.IGNORE_CASE)

    // Labels that only ever name the bill's total win over a bare "Total", which also heads GST summaries ("Total GST")
    // and quantity lines printed after the real total.
    private val strongTotalKeys = listOf("grand total", "net amount", "amount payable", "net payable", "total payable", "amount due", "bill total", "total amount", "net total",
        "कुल योग", "कुल राशि", "कुल देय")
    private val weakTotalKeys = listOf("total", "कुल")
    private val totalKeys = strongTotalKeys + weakTotalKeys
    private val subtotalKeys = listOf("sub total", "subtotal", "sub-total", "item total", "items total", "gross amount", "basic amount", "उप योग", "उपयोग")
    private val taxKeys = listOf("cgst", "sgst", "igst", "gst", "vat", "tax", "cess", "जीएसटी", "टैक्स", "वैट")
    private val serviceKeys = listOf("service charge", "service chg", "svc charge", "packing", "delivery", "convenience fee", "platform fee", "tip", "round off", "round-off", "roundoff", "सेवा शुल्क", "पैकिंग", "डिलीवरी")
    private val discountKeys = listOf("discount", "less", "coupon", "promo", "offer", "छूट")
    private val roundOffKeys = listOf("round off", "round-off", "roundoff")
    /** Lines that mention tax but carry no tax amount: the amount tax was charged on, an invoice number, a GSTIN. */
    private val notTaxKeys = listOf("taxable", "invoice", "gstin")
    /** Money handed over and given back is never the bill's total. */
    private val paymentKeys = listOf("tendered", "change", "paid", "cash", "card", "upi", "balance", "saved", "saving", "refund")
    private val skipItemKeys = totalKeys + subtotalKeys + taxKeys + serviceKeys + discountKeys + listOf(
        "cash", "change", "paid", "tendered", "balance", "upi", "card", "invoice", "bill no", "table", "gstin", "fssai", "thank", "visit", "qty", "rate", "amount", "description", "particulars", "hsn", "date", "time", "phone", "ph:", "tel", "www", ".com", "cashier", "order", "token", "kot", "saved", "saving",
        "दिनांक", "तारीख", "बिल सं", "धन्यवाद", "मात्रा", "राशि", "फोन", "नकद", "बचत"
    )

    private val longWord = Regex("""\p{L}{4,}""")
    private val qtyPrefix = Regex("""^(\d{1,2})\s*[xX×*]\s*(.+)$""")
    private val qtyAmount = Regex("""^(.*\p{L}.*?)\s+(\d{1,2})\s+(?:rs\.?|inr|₹|रु\.?|रू\.?)?\s*$AMT\s*(?:/-)?\s*$""", RegexOption.IGNORE_CASE)
    private val qtySuffix = Regex("""^(.+?)\s+(\d{1,2})\s*(?:x|X|×|nos?|pcs?|qty)?\s+$AMT\s+$AMT\s*$""")
    private val dateRx = Regex("""\b(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{1,2}[-/ ][A-Za-z]{3}[-/ ]\d{2,4}|\d{4}-\d{2}-\d{2})\b""")
    private val dateFormats = listOf("dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy", "dd-MM-yy", "dd/MM/yy", "dd.MM.yy", "dd-MMM-yyyy", "dd MMM yyyy", "dd-MMM-yy", "dd MMM yy", "yyyy-MM-dd", "dd/MMM/yyyy")

    fun parse(text: String): ParsedBill {
        val lines = text.lines().map { normaliseAmounts(asciiDigits(it).trim().replace(Regex("""\s{2,}"""), " ")) }.filter { it.isNotEmpty() }
        val lower = lines.map { it.lowercase(Locale.ROOT) }

        val total = findTotal(lines, lower)
        val subtotal = findKeyed(lines, lower, subtotalKeys, preferLast = true)
        val tax = sumKeyed(lines, lower, taxKeys, exclude = totalKeys + subtotalKeys, skip = notTaxKeys)
        val service = sumKeyed(lines, lower, serviceKeys, exclude = totalKeys, signed = roundOffKeys)
        val discount = sumKeyed(lines, lower, discountKeys, exclude = totalKeys)

        val items = extractItems(lines, lower)

        // Fallback total: the largest amount in the bottom half that is >= sum of items, never the cash handed over
        // or the change given back.
        val itemsSum = items.sumOf { it.pricePaise * it.quantity }
        val half = lines.size / 2
        val fallbackTotal = lines.indices.drop(half).filter { i -> paymentKeys.none { has(lower[i], it) } }
            .mapNotNull { i -> amountAtEnd.find(lines[i])?.groupValues?.get(1)?.let(::paise) }
            .filter { it >= itemsSum }.maxOrNull()

        return ParsedBill(
            merchant = guessMerchant(lines, lower),
            date = findDate(lines),
            totalPaise = total ?: fallbackTotal,
            subtotalPaise = subtotal,
            taxPaise = tax,
            servicePaise = service,
            discountPaise = discount,
            items = items,
            rawLines = lines,
        )
    }

    private fun paise(s: String): Long? = Money.parsePaise(s)

    private val keyRx = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    /**
     * Does lower-cased [line] carry label [key] as a word? "less" is not in "Eggless", "table" not in "Vegetable", "tax"
     * not in "Taxable"; a plural still counts ("Service charges"). Hindi labels are matched as they are: Devanagari
     * words join up ("सीजीएसटी" holds "जीएसटी").
     */
    internal fun has(line: String, key: String): Boolean {
        if (key.any { it.code > 127 }) return line.contains(key)
        val rx = keyRx.getOrPut(key) {
            val start = if (key.first().isLetterOrDigit()) """(?<![\p{L}\p{N}])""" else ""
            val end = if (key.last().isLetter()) """s?(?![\p{L}])""" else ""
            Regex(start + Regex.escape(key) + end)
        }
        return rx.containsMatchIn(line)
    }

    /** "Round off -0.40", "Round off (-) 0.40": a minus just before the amount. */
    private fun negativeBefore(prefix: String): Boolean = prefix.trimEnd().let { it.endsWith("-") || it.endsWith("(-)") }

    /**
     * The bill's total: the last line labelled as nothing but the total ("Grand Total", "Net Amount", "Amount Payable"),
     * else the last bare "Total" that isn't a subtotal, a quantity, a saving, a discount or a tax line ("Total GST").
     */
    private fun findTotal(lines: List<String>, lower: List<String>): Long? {
        val strong = keyedAmounts(lines, lower) { l -> strongTotalKeys.any { has(l, it) } && !has(l, "taxable") }
        if (strong.isNotEmpty()) return strong.last()
        val weak = keyedAmounts(lines, lower) { l ->
            when {
                has(l, "कुल") -> !(l.contains("मात्रा") || l.contains("नग"))
                has(l, "total") -> listOf("sub", "qty", "items", "item", "saving", "savings", "taxable").none { has(l, it) } &&
                    (l.contains("incl") || taxKeys.none { has(l, it) }) && discountKeys.none { has(l, it) }
                else -> false
            }
        }
        return weak.lastOrNull()
    }

    /** Amounts on the lines [pick] accepts: at the end of the line, or alone on the next one. */
    private fun keyedAmounts(lines: List<String>, lower: List<String>, pick: (String) -> Boolean): List<Long> {
        val hits = mutableListOf<Long>()
        for ((i, l) in lower.withIndex()) {
            if (!pick(l)) continue
            val amt = amountAtEnd.find(lines[i])?.groupValues?.get(1)?.let(::paise)
                ?: lines.getOrNull(i + 1)?.takeIf { anyAmount.matches(it.trim()) }?.let { anyAmount.find(it)?.groupValues?.get(1)?.let(::paise) }
                ?: continue
            if (amt > 0) hits += amt
        }
        return hits
    }

    /**
     * "३२०.००" -> "320.00": Hindi bills often print Devanagari digits (U+0966..U+096F). Bengali digits
     * (U+09E6..U+09EF) are mapped too, because the Devanagari recogniser sometimes returns a Bengali zero
     * for a Devanagari one.
     */
    internal fun asciiDigits(s: String): String =
        if (s.none { it in '\u0966'..'\u096F' || it in '\u09E6'..'\u09EF' }) s
        else s.map {
            when (it) {
                in '\u0966'..'\u096F' -> '0' + (it - '\u0966')
                in '\u09E6'..'\u09EF' -> '0' + (it - '\u09E6')
                else -> it
            }
        }.joinToString("")

    // "120,00" at the end of a line is a decimal comma: how the Devanagari recogniser reads "१२०.००", and the
    // European style. Indian grouping always ends in three digits ("1,00,000"), so two digits after the last
    // comma can only be paise.
    private val commaDecimal = Regex("""(?<![\d,])(\d{1,6}),(\d{2})\s*$""")

    private val splitDecimal = Regex("""(\d)\.\s+(\d{2})\s*$""")
    private val lastToken = Regex("""(\S+)\s*$""")
    private val decimalTail = Regex("""[\dOoeDQlI|]\.[\dOoeDQlI|]{1,2}(?:/-)?$""")
    private val amountLike = Regex("""^(?:rs\.?|inr|₹|रु\.?|रू\.?)?-?[\dOoeDQlI|,.]+(?:/-)?$""", RegexOption.IGNORE_CASE)

    /**
     * Repairs what the recogniser does to printed amounts: a space after the decimal point ("1,551. 00") and
     * letters read for digits ("360.0e", "36O.00", "l20.00"). Only the last token on a line is touched, and
     * only when it already looks like an amount (at least two real digits and a decimal point), so words such
     * as "Dal" or sizes such as "500g" are never rewritten.
     */
    internal fun normaliseAmounts(line: String): String {
        val joined = splitDecimal.replace(line) { "${it.groupValues[1]}.${it.groupValues[2]}" }
            .let { l -> commaDecimal.replace(l) { "${it.groupValues[1]}.${it.groupValues[2]}" } }
        val m = lastToken.find(joined) ?: return joined
        val tok = m.groupValues[1]
        if (!amountLike.matches(tok) || tok.count { it.isDigit() } < 2 || !decimalTail.containsMatchIn(tok)) return joined
        val prefixLen = Regex("""^(?:rs\.?|inr|₹|रु\.?|रू\.?)""", RegexOption.IGNORE_CASE).find(tok)?.value?.length ?: 0
        val fixed = tok.take(prefixLen) + tok.drop(prefixLen).map { c ->
            when (c) { 'O', 'o', 'e', 'D', 'Q' -> '0'; 'l', 'I', '|' -> '1'; else -> c }
        }.joinToString("")
        return joined.substring(0, m.range.first) + fixed
    }

    private fun findKeyed(lines: List<String>, lower: List<String>, keys: List<String>, preferLast: Boolean): Long? {
        val hits = keyedAmounts(lines, lower) { l -> keys.any { has(l, it) } }
        return if (preferLast) hits.lastOrNull() else hits.firstOrNull()
    }

    /**
     * The amounts on lines labelled with one of [keys], added up. A line also carrying an [exclude] label is left out
     * unless it starts with one of [keys]; a line with a [skip] label is always left out. On a line with a [signed]
     * label a minus before the amount is kept ("Round off -0.40" takes 40 paise off).
     */
    private fun sumKeyed(lines: List<String>, lower: List<String>, keys: List<String>, exclude: List<String>, skip: List<String> = emptyList(), signed: List<String> = emptyList()): Long {
        var sum = 0L
        for ((i, l) in lower.withIndex()) {
            if (keys.none { has(l, it) }) continue
            if (skip.any { has(l, it) }) continue
            if (exclude.any { has(l, it) } && keys.none { l.startsWith(it) }) continue
            // Percent-only lines like "CGST 2.5%" carry no amount at the end; skip if the match is the percentage.
            if (lines[i].trimEnd().endsWith("%")) continue
            val keepSign = signed.any { has(l, it) }
            val here = amountAtEnd.find(lines[i])
            val amt: Long
            val negative: Boolean
            if (here != null) {
                amt = paise(here.groupValues[1]) ?: continue
                negative = keepSign && negativeBefore(lines[i].substring(0, here.range.first))
            } else {
                val next = lines.getOrNull(i + 1)?.takeIf { standaloneAmount.matches(it.trim()) } ?: continue
                val m = amountAtEnd.find(next) ?: continue
                amt = paise(m.groupValues[1]) ?: continue
                negative = keepSign && negativeBefore(next.substring(0, m.range.first))
            }
            sum += if (negative) -amt else amt
        }
        return sum
    }

    private fun extractItems(lines: List<String>, lower: List<String>): List<BillItem> {
        val out = mutableListOf<BillItem>()
        for ((i, raw) in lines.withIndex()) {
            val l = lower[i]
            if (skipItemKeys.any { has(l, it) }) continue
            if (dateRx.containsMatchIn(raw)) continue
            val m = amountAtEnd.find(raw) ?: continue
            val token = m.groupValues[1]
            // 6+ bare digits is a code (PIN, phone, HSN). Five can be a real price printed without decimals
            // ("Speaker 12500"), but only after a real word: "Inv 88213" (OCR: "Iny") is an invoice number.
            if (token.all { it.isDigit() } && (token.length >= 6 || token.length == 5 && !longWord.containsMatchIn(raw.substring(0, m.range.first)))) continue
            val price = paise(token) ?: continue
            if (price <= 0) continue
            var name = raw.substring(0, m.range.first).trim().trimEnd('-', ':', '.', 'x', 'X', '@')
            var qty = 1
            // "2 x Paneer Tikka   480.00" or "Paneer Tikka 2 240.00 480.00"
            qtySuffix.find(raw)?.let { s ->
                name = s.groupValues[1].trim(); qty = s.groupValues[2].toIntOrNull() ?: 1
                val lineTotal = paise(s.groupValues[4]) ?: price
                // Keep what the bill printed for the line: 3 x 33.33 = 100.00 can't be a whole-paise unit price, so
                // such a line stays one item at its line total.
                out += if (qty > 0 && lineTotal % qty == 0L) BillItem(name = clean(name), quantity = qty, pricePaise = lineTotal / qty)
                else BillItem(name = clean(name), quantity = 1, pricePaise = lineTotal)
                return@let
            } ?: qtyAmount.find(raw)?.takeIf { q ->
                // Keep the quantity only when the line total divides evenly, so quantity x price still equals
                // what the bill printed; otherwise fall through and keep the line total as a single item.
                val n = q.groupValues[2].toInt(); val t = paise(q.groupValues[3]) ?: 0L
                n > 0 && t % n == 0L && !qtyPrefix.matches(q.groupValues[1].trim())
            }?.let { q ->
                val n = q.groupValues[2].toInt(); val t = paise(q.groupValues[3])!!
                out += BillItem(name = clean(q.groupValues[1]), quantity = n, pricePaise = t / n)
            } ?: run {
                qtyPrefix.find(name)?.let { q -> qty = q.groupValues[1].toIntOrNull() ?: 1; name = q.groupValues[2] }
                name = clean(name)
                if (name.length < 2 || name.all { !it.isLetter() }) return@run
                // If the price is a line total for qty > 1, store unit price so qty*price reconciles.
                val unit = if (qty > 1 && price % qty == 0L) price / qty else price
                out += BillItem(name = name, quantity = if (unit != price) qty else 1, pricePaise = if (unit != price) unit else price)
            }
        }
        return out
    }

    private fun clean(s: String) = s.replace(Regex("""^\d+[.)]\s*"""), "").replace(Regex("""\s+"""), " ").trim().take(40)

    private fun guessMerchant(lines: List<String>, lower: List<String>): String? {
        // First non-numeric line near the top that is not an address/phone/gst line.
        for ((i, l) in lower.take(5).withIndex()) {
            if (l.any { it.isLetter() } && l.count { it.isDigit() } <= 2 && skipItemKeys.none { has(l, it) } && l.length in 3..40) return lines[i]
        }
        return null
    }

    private fun findDate(lines: List<String>): Long? {
        val now = System.currentTimeMillis()
        for (l in lines) {
            val m = dateRx.find(l) ?: continue
            for (f in dateFormats) {
                val d = runCatching { SimpleDateFormat(f, Locale.ENGLISH).apply { isLenient = false }.parse(m.value) }.getOrNull() ?: continue
                if (d.time in (now - 5L * 365 * 86_400_000L)..(now + 86_400_000L)) return d.time
            }
        }
        return null
    }
}
