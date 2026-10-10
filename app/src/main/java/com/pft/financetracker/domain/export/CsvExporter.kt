package com.pft.financetracker.domain.export

import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.split.Split
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {
    // DateTimeFormatter is immutable, so two exports at once cannot garble each other's dates.
    private val minuteFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)
    private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    /** In the phone's time zone, read at each call as SimpleDateFormat did. */
    private fun minute(millis: Long) = minuteFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    private fun day(millis: Long) = dayFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private fun rupees(paise: Long) = String.format(Locale.ENGLISH, "%.2f", paise / 100.0)

    fun toCsv(list: List<Transaction>): String {
        val sb = StringBuilder()
        sb.append("id,date,type,flow,amount,merchant,category,bank,account_last4,ref,source,note\n")
        for (t in list) {
            sb.append(t.id).append(',')
                .append(esc(minute(t.timestamp))).append(',')
                .append(t.type.name).append(',')
                .append(t.flow.name).append(',')
                .append(rupees(t.amountPaise)).append(',')
                .append(esc(t.merchant)).append(',')
                .append(esc(t.category.label)).append(',')
                .append(esc(t.bankName ?: "")).append(',')
                .append(esc(t.accountRef ?: "")).append(',')
                .append(esc(t.refNumber ?: "")).append(',')
                .append(t.source.name).append(',')
                .append(esc(t.note ?: "")).append('\n')
        }
        return sb.toString()
    }

    fun splitsToCsv(splits: List<Split>): String {
        val sb = StringBuilder("split_id,date,title,total,mode,payer,person,share,settled\n")
        for (s in splits) for (sh in s.shares) {
            sb.append(s.id).append(',')
                .append(esc(minute(s.date))).append(',')
                .append(esc(s.title)).append(',')
                .append(rupees(s.totalPaise)).append(',')
                .append(s.mode.name).append(',')
                .append(esc(s.people.getOrNull(s.payerIndex)?.name ?: "")).append(',')
                .append(esc(s.people.getOrNull(sh.personIndex)?.name ?: "")).append(',')
                .append(rupees(sh.amountPaise)).append(',')
                .append(rupees(sh.settledPaise)).append('\n')
        }
        return sb.toString()
    }

    /** One row per payment counted toward a deduction, grouped by section, for the person's tax records. */
    fun taxToCsv(totals: List<com.pft.financetracker.domain.tax.SectionTotal>, byId: Map<Long, Transaction>, fyLabel: String): String {
        val sb = StringBuilder("Financial year,Section,What,Date,Payee,Amount (INR)\n")
        for (t in totals) for (id in t.transactionIds) {
            val tx = byId[id] ?: continue
            sb.append(esc(fyLabel)).append(',').append(esc(t.section.code)).append(',').append(esc(t.section.label)).append(',')
                .append(day(tx.timestamp)).append(',').append(esc(tx.merchant)).append(',')
                // A refund of a premium or fee comes off the section, so it is written as a negative amount.
                .append(rupees(if (tx.type == com.pft.financetracker.domain.model.TransactionType.CREDIT) -tx.amountPaise else tx.amountPaise)).append('\n')
        }
        return sb.toString()
    }

    /** RFC-4180 quoting; also neutralises spreadsheet formula injection (=, +, -, @ prefixes). */
    private fun esc(v: String): String {
        var s = v
        if (s.isNotEmpty() && s[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) s = "'$s"
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
