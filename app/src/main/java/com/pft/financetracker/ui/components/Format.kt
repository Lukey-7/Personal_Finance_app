package com.pft.financetracker.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

fun money(v: Double, decimals: Boolean = false): String {
    val neg = v < 0
    val a = kotlin.math.abs(v)
    val s = if (decimals) String.format(Locale.ENGLISH, "%,.2f", a) else String.format(Locale.ENGLISH, "%,d", a.roundToLong())
    return (if (neg) "-₹" else "₹") + s
}

private val dayFmt = SimpleDateFormat("dd MMM", Locale.ENGLISH)
private val fullFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

fun shortDate(t: Long): String = dayFmt.format(Date(t))
fun fullDate(t: Long): String = fullFmt.format(Date(t))
fun dateOnly(t: Long): String = dateFmt.format(Date(t))
