package com.pft.financetracker.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * The one way an adapter talks to a provider: a JSON POST over platform APIs only (HttpURLConnection + org.json), so
 * no SDK and no new library. Plain http is refused unless the server is on this phone (a model running in Termux);
 * every other address must be https, matching network_security_config.xml. No error ever carries the key.
 */
object HttpJson {
    class Response(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    /** https anywhere, or http to this phone only. */
    fun allowed(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host.isBlank()) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> host in LOCAL_HOSTS
            else -> false
        }
    }

    suspend fun post(url: String, headers: Map<String, String>, body: JSONObject, readTimeoutMs: Int = 90_000): Response = withContext(Dispatchers.IO) {
        require(allowed(url)) { "This address isn't allowed: use https, or http only for a server on this phone." }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            doOutput = true
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            Response(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** The provider's own error message from a failed response, else "HTTP 401". */
    fun errorMessage(r: Response): String {
        val json = runCatching { JSONObject(r.body) }.getOrNull()
        val msg = json?.let { o ->
            o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: o.optString("message").takeIf { it.isNotBlank() }
                ?: o.optString("error").takeIf { it.isNotBlank() && !it.startsWith("{") }
        }
        return msg ?: "HTTP ${r.code}"
    }

    /** [s] with [key] and anything shaped like an API key replaced, so an error can never show a key on screen. */
    fun scrub(s: String, key: String?): String {
        var out = s
        if (!key.isNullOrBlank() && key.length >= 6) out = out.replace(key, "***")
        return out
            .replace(Regex("""\b(sk|gsk|AIza|ABSK|xai)[-_A-Za-z0-9]{12,}"""), "***")
            .replace(Regex("""(?i)bearer\s+[-_.A-Za-z0-9=]{12,}"""), "Bearer ***")
    }

    /** An exception as a short message for the screen. */
    fun failure(e: Exception, key: String?): LlmResult.Error = LlmResult.Error(scrub(e.message ?: "Network error", key))
}

/** Pulling JSON out of a reply from a model without a JSON mode (it may wrap the object in prose or ``` fences). */
object JsonText {
    fun firstObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        return null
    }
}
