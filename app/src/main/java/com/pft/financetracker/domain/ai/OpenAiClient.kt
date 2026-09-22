package com.pft.financetracker.domain.ai

import com.pft.financetracker.domain.insights.PeriodSummary
import com.pft.financetracker.domain.model.Budget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt

/**
 * Minimal OpenAI Chat Completions client using only platform APIs (HttpsURLConnection + org.json).
 * - Called ONLY when the user taps the button in the app.
 * - Sends ONLY aggregated numbers (category totals, counts, budgets). No merchant names, no SMS text,
 *   no account numbers, no bank names.
 * - The API key lives in memory only for the duration of the request.
 */
class OpenAiClient(private val model: String = "gpt-4o-mini") {

    sealed class Result {
        data class Ok(val text: String) : Result()
        data class Error(val message: String) : Result()
    }

    /** Build the aggregated, anonymised payload. Public so the Settings screen can show the user exactly what is sent. */
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

    suspend fun monthlySummary(apiKey: String, payload: String): Result = withContext(Dispatchers.IO) {
        val system = "You are a concise personal-finance coach for an Indian user. You receive only aggregated monthly totals in INR. " +
            "Write (1) a 3-4 sentence plain-English summary of the month, then (2) 3-5 specific, actionable money-saving suggestions as a bulleted list. " +
            "Be practical and non-judgemental. Do not ask for more data. Use ₹ for amounts."
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.4)
            put("max_tokens", 600)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", "Here is my aggregated data:\n$payload"))
            })
        }
        try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                useCaches = false
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
                return@withContext Result.Error(sanitize(msg))
            }
            val content = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            Result.Ok(content.trim())
        } catch (e: Exception) {
            Result.Error(sanitize(e.message ?: "Network error"))
        }
    }

    /** Make sure an error string can never echo the key back into the UI. */
    private fun sanitize(s: String) = s.replace(Regex("""sk-[A-Za-z0-9_\-]{8,}"""), "sk-***")

}
