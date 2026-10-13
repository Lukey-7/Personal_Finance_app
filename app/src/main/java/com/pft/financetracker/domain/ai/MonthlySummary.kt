package com.pft.financetracker.domain.ai

import com.pft.financetracker.domain.insights.PeriodSummary
import com.pft.financetracker.domain.model.Budget
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * The AI month summary in Settings › AI, with whichever provider is set up.
 * - Asked ONLY when the person taps Generate.
 * - Sends ONLY aggregated numbers (category totals, counts, budgets). No merchant names, no SMS text, no account
 *   numbers, no bank names. (Ask is different: see AskAiPrompt.)
 */
object MonthlySummary {
    /** The aggregated, anonymised payload. Public so the Settings screen can show exactly what is sent. */
    fun buildPayload(current: PeriodSummary, previous: PeriodSummary, budgets: List<Budget>): String {
        val o = JSONObject()
        o.put("currency", "INR")
        o.put("period", current.period.label)
        // Net figures: expenses minus refunds. Transfers, card-bill payments and investments are excluded.
        o.put("net_spend", current.spend.roundToInt())
        o.put("gross_spend", Math.round(current.grossSpendPaise / 100.0))
        o.put("refunds", Math.round(current.refundsPaise / 100.0))
        o.put("total_income", current.income.roundToInt())
        o.put("investments", Math.round(current.investmentsPaise / 100.0))
        o.put("transaction_count", current.expenseCount)
        o.put("previous_period_net_spend", previous.spend.roundToInt())
        val cats = JSONArray()
        current.byCategory.forEach { cs ->
            val prev = previous.byCategory.firstOrNull { it.category == cs.category }?.amount ?: 0.0
            cats.put(JSONObject().apply {
                put("category", cs.category.label)
                put("amount", cs.amount.roundToInt())
                put("count", cs.count)
                put("previous_amount", prev.roundToInt())
                budgets.firstOrNull { it.category == cs.category }?.let { put("budget", it.monthlyLimit.roundToInt()) }
            })
        }
        o.put("categories", cats)
        return o.toString(2)
    }

    private const val SYSTEM = "You are a concise personal-finance coach for an Indian user. You receive only aggregated monthly totals in INR. " +
        "Write (1) a 3-4 sentence plain-English summary of the month, then (2) 3-5 specific, actionable money-saving suggestions as a bulleted list. " +
        "Be practical and non-judgemental. Do not ask for more data. Use ₹ for amounts."

    fun request(payload: String) = LlmRequest.ask(SYSTEM, "Here is my aggregated data:\n$payload", maxTokens = 600, temperature = 0.4)
}
