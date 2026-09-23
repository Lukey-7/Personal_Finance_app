package com.pft.financetracker.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Format paise as rupees: ₹1,234 or ₹1,234.50 with [decimals]. */
fun money(paise: Long, decimals: Boolean = false): String {
    val neg = paise < 0
    val a = abs(paise)
    val s = if (decimals) String.format(Locale.ENGLISH, "%,d.%02d", a / 100, a % 100)
    else String.format(Locale.ENGLISH, "%,d", Math.round(a / 100.0))
    return (if (neg) "-₹" else "₹") + s
}

/** Rupee Double convenience for chart code that still works in rupees. */
fun money(rupees: Double, decimals: Boolean = false): String = money(Math.round(rupees * 100), decimals)

/** Paise -> editable text ("250" or "250.50"). */
fun paiseToInput(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else String.format(Locale.ENGLISH, "%d.%02d", paise / 100, paise % 100)

private val dayFmt = SimpleDateFormat("dd MMM", Locale.ENGLISH)
private val fullFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
private val timeFmt = SimpleDateFormat("HH:mm", Locale.ENGLISH)

fun shortDate(t: Long): String = dayFmt.format(Date(t))
fun fullDate(t: Long): String = fullFmt.format(Date(t))
fun dateOnly(t: Long): String = dateFmt.format(Date(t))
fun timeOnly(t: Long): String = timeFmt.format(Date(t))
