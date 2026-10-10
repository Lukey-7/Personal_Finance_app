package com.pft.financetracker.ui.model

import com.pft.financetracker.ui.components.money
import kotlin.math.roundToInt

/** The Budgets screen's words and the limit field's rules, apart from drawing. */
object BudgetLines {
    /**
     * "₹1,300 left · 35% used" or "₹200 over". Refunds can leave a category below ₹0 for the month; that reads as
     * nothing used yet, never as a negative share or more left than the limit.
     */
    fun row(spentPaise: Long, limitPaise: Long): String {
        val spent = spentPaise.coerceAtLeast(0)
        if (spent > limitPaise) return "${money(spent - limitPaise)} over"
        val pct = if (limitPaise <= 0) 0 else (spent * 100.0 / limitPaise).roundToInt()
        return "${money(limitPaise - spent)} left · $pct% used"
    }

    /** The hero's line under the bar, with the same floor at ₹0. */
    fun hero(spentPaise: Long, limitsPaise: Long): String {
        val spent = spentPaise.coerceAtLeast(0)
        return if (spent > limitsPaise) "of ${money(limitsPaise)} budgeted · ${money(spent - limitsPaise)} over"
        else "of ${money(limitsPaise)} budgeted · ${money(limitsPaise - spent)} left"
    }

    /** What the limit field holds. */
    sealed class Input {
        data class Limit(val paise: Long) : Input()
        /** 0 typed on purpose: take the limit away. */
        data object Remove : Input()
        data class Invalid(val message: String) : Input()
    }

    /** Whole rupees, as the field accepts. Blank is not a limit and not a removal: it asks for a figure. */
    fun parse(input: String): Input {
        val t = input.trim()
        if (t.isEmpty()) return Input.Invalid("Enter a monthly limit, or 0 to remove it")
        if (!t.all { it.isDigit() }) return Input.Invalid("Use whole rupees, like 5000")
        val rupees = t.toLongOrNull()?.takeIf { it <= MaxRupees } ?: return Input.Invalid("That limit is too large")
        return if (rupees == 0L) Input.Remove else Input.Limit(rupees * 100)
    }

    /** ₹10 crore: well past any monthly budget, and far from overflowing paise. */
    private const val MaxRupees = 10_00_00_000L
}
