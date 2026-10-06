package com.pft.financetracker.domain.model

import kotlin.math.abs

/** How a rupee figure shows its paise. */
enum class Paise { NEVER, WHEN_NONZERO, ALWAYS }

/**
 * The one rupee format used everywhere a figure is shown: Indian grouping (₹1,05,000), the sign before the ₹, and
 * paise only when the amount has them (₹2,400 and ₹2,400.50).
 */
object Rupees {
    /** 1,05,000: the last three digits, then pairs. */
    fun group(n: Long): String {
        val s = abs(n).toString()
        val sign = if (n < 0) "-" else ""
        if (s.length <= 3) return sign + s
        val pairs = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return sign + pairs + "," + s.takeLast(3)
    }

    fun format(paise: Long, mode: Paise = Paise.WHEN_NONZERO): String {
        val sign = if (paise < 0) "-" else ""
        val a = abs(paise)
        val body = when {
            mode == Paise.NEVER -> group(Math.round(a / 100.0))
            mode == Paise.ALWAYS || a % 100 != 0L -> group(a / 100) + "." + (a % 100).toString().padStart(2, '0')
            else -> group(a / 100)
        }
        return "$sign₹$body"
    }
}
