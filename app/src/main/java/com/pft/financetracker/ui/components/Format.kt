package com.pft.financetracker.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pft.financetracker.domain.model.Paise
import com.pft.financetracker.domain.model.Rupees

/** Paise as rupees, the one way: ₹1,05,000, with paise only when present (₹1,234.50). [decimals] always shows them. */
fun money(paise: Long, decimals: Boolean = false): String = Rupees.format(paise, if (decimals) Paise.ALWAYS else Paise.WHEN_NONZERO)

/** An estimate (a daily average, a projection, a monthly target): whole rupees, since paise would claim false precision. */
fun approxMoney(paise: Long): String = Rupees.format(paise, Paise.NEVER)

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
