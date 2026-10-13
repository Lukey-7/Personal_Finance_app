package com.pft.financetracker.domain.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * OpenAI's Chat Completions, which most hosts speak: OpenAI, Gemini (its OpenAI-compatible endpoint), Groq,
 * OpenRouter, Mistral, DeepSeek, Ollama, LM Studio, llama.cpp and vLLM. JSON mode uses `response_format`; a server
 * that rejects that field is asked once more without it, and the object is taken from the reply.
 */
class OpenAiCompatibleProvider(
    private val kind: ProviderKind,
    private val baseUrl: String,
    private val model: String,
    private val key: String?,
) : LlmProvider {
    override val assistantName: String get() = kind.assistantName
    // The OpenAI default keeps the cache key answers had before v1.5.1, so no paid answer is asked for again.
    override val cacheScope: String get() = if (kind == ProviderKind.OPENAI && model == ProviderKind.OPENAI.defaultModel) "" else "${kind.id}:$model"

    override suspend fun complete(request: LlmRequest): LlmResult = try {
        val first = send(request, jsonMode = request.wantJson)
        val r = if (!first.ok && request.wantJson && first.code in 400..422 && rejectsJsonMode(first)) send(request, jsonMode = false) else first
        if (!r.ok) LlmResult.Error(HttpJson.scrub(HttpJson.errorMessage(r), key))
        else {
            val text = JSONObject(r.body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
            reply(text, request.wantJson)
        }
    } catch (e: Exception) {
        HttpJson.failure(e, key)
    }

    private suspend fun send(request: LlmRequest, jsonMode: Boolean): HttpJson.Response {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", request.system))
                request.turns.forEach { put(JSONObject().put("role", it.role.wire()).put("content", it.text)) }
            })
        }
        val headers = if (key != null) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return HttpJson.post("$baseUrl/chat/completions", headers, body)
    }

    private fun rejectsJsonMode(r: HttpJson.Response) = HttpJson.errorMessage(r).lowercase().let { "response_format" in it || "json" in it }
}

/** Anthropic's Messages API (Claude). No JSON mode: the system prompt asks for JSON and the object is taken from the reply. */
class AnthropicProvider(private val baseUrl: String, private val model: String, private val key: String) : LlmProvider {
    override val assistantName: String get() = ProviderKind.ANTHROPIC.assistantName
    override val cacheScope: String get() = "anthropic:$model"

    override suspend fun complete(request: LlmRequest): LlmResult = try {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("system", request.system + if (request.wantJson) JSON_ONLY else "")
            put("messages", JSONArray().apply {
                request.turns.forEach { put(JSONObject().put("role", it.role.wire()).put("content", it.text)) }
            })
        }
        val r = HttpJson.post("$baseUrl/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body)
        if (!r.ok) LlmResult.Error(HttpJson.scrub(HttpJson.errorMessage(r), key))
        else {
            val parts = JSONObject(r.body).getJSONArray("content")
            val text = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }.trim()
            reply(text, request.wantJson)
        }
    } catch (e: Exception) {
        HttpJson.failure(e, key)
    }
}

/**
 * AWS Bedrock's Converse API, signed in with a Bedrock API key (a bearer token). Any Bedrock model the account can
 * use works: Llama, Claude, Mistral, Nova, DeepSeek, gpt-oss. Cross-region models need their inference profile id
 * ("us.meta.llama3-3-70b-instruct-v1:0").
 */
class BedrockProvider(private val baseUrl: String, private val model: String, private val key: String) : LlmProvider {
    override val assistantName: String get() = ProviderKind.BEDROCK.assistantName
    override val cacheScope: String get() = "bedrock:$model"

    override suspend fun complete(request: LlmRequest): LlmResult = try {
        val body = JSONObject().apply {
            put("system", JSONArray().put(JSONObject().put("text", request.system + if (request.wantJson) JSON_ONLY else "")))
            put("messages", JSONArray().apply {
                request.turns.forEach { t ->
                    put(JSONObject().put("role", t.role.wire()).put("content", JSONArray().put(JSONObject().put("text", t.text))))
                }
            })
            put("inferenceConfig", JSONObject().put("maxTokens", request.maxTokens).put("temperature", request.temperature))
        }
        val id = URLEncoder.encode(model, "UTF-8")
        val r = HttpJson.post("$baseUrl/model/$id/converse", mapOf("Authorization" to "Bearer $key"), body)
        if (!r.ok) LlmResult.Error(HttpJson.scrub(HttpJson.errorMessage(r), key))
        else {
            val parts = JSONObject(r.body).getJSONObject("output").getJSONObject("message").getJSONArray("content")
            val text = (0 until parts.length()).map { parts.getJSONObject(it) }.joinToString("") { it.optString("text") }.trim()
            reply(text, request.wantJson)
        }
    } catch (e: Exception) {
        HttpJson.failure(e, key)
    }
}

private const val JSON_ONLY = "\n\nReply with one JSON object only, no other text."

private fun LlmRequest.Role.wire() = if (this == LlmRequest.Role.USER) "user" else "assistant"

/** The reply as the caller wants it: for JSON, the first object in it (an answer with none is an error). */
private fun reply(text: String, wantJson: Boolean): LlmResult = when {
    text.isBlank() -> LlmResult.Error("The model sent an empty reply.")
    !wantJson -> LlmResult.Ok(text)
    else -> JsonText.firstObject(text)?.let { LlmResult.Ok(it) } ?: LlmResult.Error("The model's reply wasn't JSON.")
}
