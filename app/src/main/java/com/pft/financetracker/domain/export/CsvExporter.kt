package com.pft.financetracker.domain.export

import com.pft.financetracker.domain.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    fun toCsv(list: List<Transaction>): String {
        val sb = StringBuilder()
        sb.append("id,date,type,amount,merchant,category,bank,account_last4,source,note\n")
        for (t in list) {
            sb.append(t.id).append(',')
                .append(esc(df.format(Date(t.timestamp)))).append(',')
                .append(t.type.name).append(',')
                .append(String.format(Locale.ENGLISH, "%.2f", t.amount)).append(',')
                .append(esc(t.merchant)).append(',')
                .append(esc(t.category.label)).append(',')
                .append(esc(t.bankName ?: "")).append(',')
                .append(esc(t.accountRef ?: "")).append(',')
                .append(t.source.name).append(',')
                .append(esc(t.note ?: "")).append('\n')
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
