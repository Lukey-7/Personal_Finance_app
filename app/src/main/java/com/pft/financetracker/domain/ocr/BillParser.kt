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
    private val amountAtEnd = Regex("""(?:rs\.?|inr|₹|\$)?\s*$AMT\s*(?:/-)?\s*$""", RegexOption.IGNORE_CASE)
    private val anyAmount = Regex("""(?:rs\.?|inr|₹)?\s*$AMT""", RegexOption.IGNORE_CASE)

    private val totalKeys = listOf("grand total", "net amount", "amount payable", "net payable", "total payable", "amount due", "bill total", "total amount", "net total", "total")
    private val subtotalKeys = listOf("sub total", "subtotal", "sub-total", "item total", "items total", "gross amount", "basic amount")
    private val taxKeys = listOf("cgst", "sgst", "igst", "gst", "vat", "tax", "cess")
    private val serviceKeys = listOf("service charge", "service chg", "svc charge", "packing", "delivery", "convenience fee", "platform fee", "tip", "round off", "round-off", "roundoff")
    private val discountKeys = listOf("discount", "less", "coupon", "promo", "offer", "saved")
    private val skipItemKeys = totalKeys + subtotalKeys + taxKeys + serviceKeys + discountKeys + listOf(
        "cash", "change", "paid", "tendered", "balance", "upi", "card", "invoice", "bill no", "table", "gstin", "fssai", "thank", "visit", "qty", "rate", "amount", "description", "particulars", "hsn", "date", "time", "phone", "ph:", "tel", "www", ".com", "cashier", "order", "token", "kot"
    )

    private val qtyPrefix = Regex("""^(\d{1,2})\s*[xX×*]\s*(.+)$""")
    private val qtySuffix = Regex("""^(.+?)\s+(\d{1,2})\s*(?:x|X|×|nos?|pcs?|qty)?\s+$AMT\s+$AMT\s*$""")
    private val dateRx = Regex("""\b(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{1,2}[-/ ][A-Za-z]{3}[-/ ]\d{2,4}|\d{4}-\d{2}-\d{2})\b""")
    private val dateFormats = listOf("dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy", "dd-MM-yy", "dd/MM/yy", "dd.MM.yy", "dd-MMM-yyyy", "dd MMM yyyy", "dd-MMM-yy", "dd MMM yy", "yyyy-MM-dd", "dd/MMM/yyyy")

    fun parse(text: String): ParsedBill {
        val lines = text.lines().map { it.trim().replace(Regex("""\s{2,}"""), " ") }.filter { it.isNotEmpty() }
        val lower = lines.map { it.lowercase(Locale.ROOT) }

        val total = findKeyed(lines, lower, totalKeys, preferLast = true)
        val subtotal = findKeyed(lines, lower, subtotalKeys, preferLast = true)
        val tax = sumKeyed(lines, lower, taxKeys, exclude = totalKeys + subtotalKeys)
        val service = sumKeyed(lines, lower, serviceKeys, exclude = totalKeys)
        val discount = sumKeyed(lines, lower, discountKeys, exclude = totalKeys)

        val items = extractItems(lines, lower)

        // Fallback total: the largest amount in the bottom half that is >= sum of items.
        val itemsSum = items.sumOf { it.pricePaise * it.quantity }
        val fallbackTotal = lines.drop(lines.size / 2).mapNotNull { amountAtEnd.find(it)?.groupValues?.get(1)?.let(::paise) }
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

    private fun findKeyed(lines: List<String>, lower: List<String>, keys: List<String>, preferLast: Boolean): Long? {
        val hits = mutableListOf<Long>()
        for ((i, l) in lower.withIndex()) {
            val key = keys.firstOrNull { l.contains(it) } ?: continue
            // "total" must not match "sub total" / "total qty" when we are looking for the grand total.
            if (key == "total" && (l.contains("sub") || l.contains("qty") || l.contains("items") || l.contains("saving"))) continue
            val amt = amountAtEnd.find(lines[i])?.groupValues?.get(1)?.let(::paise)
                ?: lines.getOrNull(i + 1)?.takeIf { anyAmount.matches(it.trim()) }?.let { anyAmount.find(it)?.groupValues?.get(1)?.let(::paise) }
                ?: continue
            if (amt > 0) hits += amt
        }
        return if (preferLast) hits.lastOrNull() else hits.firstOrNull()
    }

    private fun sumKeyed(lines: List<String>, lower: List<String>, keys: List<String>, exclude: List<String>): Long {
        var sum = 0L
        for ((i, l) in lower.withIndex()) {
            if (keys.none { l.contains(it) }) continue
            if (exclude.any { l.contains(it) } && keys.none { l.startsWith(it) }) continue
            val amt = amountAtEnd.find(lines[i])?.groupValues?.get(1)?.let(::paise) ?: continue
            // Percent-only lines like "CGST 2.5%" carry no amount at the end; skip if the match is the percentage.
            if (lines[i].trimEnd().endsWith("%")) continue
            sum += amt
        }
        return sum
    }

    private fun extractItems(lines: List<String>, lower: List<String>): List<BillItem> {
        val out = mutableListOf<BillItem>()
        for ((i, raw) in lines.withIndex()) {
            val l = lower[i]
            if (skipItemKeys.any { l.contains(it) }) continue
            val m = amountAtEnd.find(raw) ?: continue
            val price = paise(m.groupValues[1]) ?: continue
            if (price <= 0) continue
            var name = raw.substring(0, m.range.first).trim().trimEnd('-', ':', '.', 'x', 'X', '@')
            var qty = 1
            // "2 x Paneer Tikka   480.00" or "Paneer Tikka 2 240.00 480.00"
            qtySuffix.find(raw)?.let { s ->
                name = s.groupValues[1].trim(); qty = s.groupValues[2].toIntOrNull() ?: 1
                val lineTotal = paise(s.groupValues[4]) ?: price
                val unit = if (qty > 0) lineTotal / qty else lineTotal
                out += BillItem(name = clean(name), quantity = qty, pricePaise = unit)
                return@let
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
            if (l.any { it.isLetter() } && l.count { it.isDigit() } <= 2 && skipItemKeys.none { l.contains(it) } && l.length in 3..40) return lines[i]
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
