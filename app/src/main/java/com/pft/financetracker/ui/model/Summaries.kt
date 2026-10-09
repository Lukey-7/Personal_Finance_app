package com.pft.financetracker.ui.model

import com.pft.financetracker.domain.insights.BudgetStatus
import com.pft.financetracker.domain.insights.CategorySpend
import com.pft.financetracker.domain.insights.MerchantSpend
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.components.displayMerchant
import com.pft.financetracker.ui.components.money
import kotlin.math.roundToInt

/** Chart arithmetic, apart from drawing: which bar a finger is on, how tall each bar is, what TalkBack says. */
object ChartMath {
    /**
     * The bar under [x] on a chart [width] wide with [count] bars. Each bar owns an equal column, gaps included, so a
     * touch between two bars still picks one; a drag past either edge holds the end bar. Null when there is nothing.
     */
    fun indexAt(x: Float, width: Float, count: Int): Int? {
        if (count <= 0 || width <= 0f) return null
        return (x / (width / count)).toInt().coerceIn(0, count - 1)
    }

    /** Each value as a share of the tallest; a period that is zero or below (refunds outweighed spend) draws empty. */
    fun fractions(values: List<Long>): List<Float> {
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: return values.map { 0f }
        return values.map { if (it <= 0) 0f else it.toFloat() / max }
    }

    fun spoken(label: String, paise: Long): String = if (paise <= 0) "$label: nothing spent" else "$label: ${money(paise)}"
}

/** A category's name short enough for a one-line summary: "Food", "Bills", "Cash". */
fun shortLabel(c: Category): String = when (c) {
    Category.FOOD -> "Food"
    Category.BILLS -> "Bills"
    Category.ATM -> "Cash"
    else -> c.label
}

/** The one line each collapsed card on Home shows. */
object HomeLines {
    fun budgets(status: List<BudgetStatus>): String {
        if (status.isEmpty()) return "No budgets set"
        val over = status.filter { it.over }.sortedByDescending { it.spentPaise - it.budget.monthlyLimitPaise }
        if (over.isNotEmpty()) {
            val top = over.first()
            return "${over.size} over · ${shortLabel(top.budget.category)} ${money(top.spentPaise - top.budget.monthlyLimitPaise)} over"
        }
        val near = status.filter { it.fraction >= 0.8f }.maxByOrNull { it.fraction }
        val set = "${status.size} set"
        return if (near != null) "${shortLabel(near.budget.category)} at ${(near.fraction * 100).roundToInt()}% · $set" else "All within budget · $set"
    }

    fun whereItWent(byCategory: List<CategorySpend>): String {
        val spend = byCategory.filter { it.amountPaise > 0 }
        val total = spend.sumOf { it.amountPaise }
        if (total <= 0) return "No spending yet"
        return spend.take(3).joinToString(" · ") { "${shortLabel(it.category)} ${(it.amountPaise * 100.0 / total).roundToInt()}%" }
    }

    /** One slice of the "Where it went" ring: a category, or the rest together when [category] is null. */
    data class RingPart(val category: Category?, val paise: Long)

    /**
     * The ring's slices: the [top] biggest categories with positive spend, and everything after them as one slice, so
     * the ring adds up to the same total [whereItWent] takes its percentages from.
     */
    fun ringParts(byCategory: List<CategorySpend>, top: Int = 7): List<RingPart> {
        val spend = byCategory.filter { it.amountPaise > 0 }.sortedByDescending { it.amountPaise }
        val rest = spend.drop(top).sumOf { it.amountPaise }
        return spend.take(top).map { RingPart(it.category, it.amountPaise) } + (if (rest > 0) listOf(RingPart(null, rest)) else emptyList())
    }

    /** Whole-number share of [paise] in [totalPaise], the way the ring and the summary line both round it. */
    fun percentOf(paise: Long, totalPaise: Long): Int = if (totalPaise <= 0) 0 else (paise * 100.0 / totalPaise).roundToInt()

    fun merchants(byMerchant: List<MerchantSpend>): String =
        byMerchant.take(2).joinToString(" · ") { "${displayMerchant(it.merchant)} ${money(it.amountPaise)}" }

    fun notCounted(movedPaise: Long, investedPaise: Long, cashPaise: Long, paidBackPaise: Long): String =
        listOf(movedPaise to "moved", investedPaise to "invested", cashPaise to "cash withdrawn", paidBackPaise to "paid back by friends")
            .filter { it.first > 0 }.sortedByDescending { it.first }.take(2)
            .joinToString(" · ") { "${money(it.first)} ${it.second}" }
}
