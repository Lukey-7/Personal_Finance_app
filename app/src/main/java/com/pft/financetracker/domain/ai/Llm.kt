package com.pft.financetracker.domain.ai

/**
 * What FinTrack needs from a language model, whoever runs it: one reply to a system prompt and a few turns, as plain
 * text or as JSON. The features (the monthly summary, split judging, Ask) only ever talk to this. How OpenAI, Claude,
 * Gemini, Bedrock or a model on the phone do it is each adapter's business (see [Providers]).
 */
interface LlmProvider {
    /** Who answers, in the words the app shows: "ChatGPT", "Claude", "your local model". */
    val assistantName: String

    /** Tells the AI answer cache apart per provider and model, so switching never replays another model's answer. */
    val cacheScope: String

    suspend fun complete(request: LlmRequest): LlmResult
}

data class LlmRequest(
    val system: String,
    val turns: List<Turn>,
    /** The reply must be one JSON object. Adapters use the vendor's JSON mode where there is one. */
    val wantJson: Boolean = false,
    val maxTokens: Int = 1_200,
    val temperature: Double = 0.3,
) {
    data class Turn(val role: Role, val text: String)
    enum class Role { USER, ASSISTANT }

    companion object {
        /** A single question. */
        fun ask(system: String, user: String, wantJson: Boolean = false, maxTokens: Int = 1_200, temperature: Double = 0.3) =
            LlmRequest(system, listOf(Turn(Role.USER, user)), wantJson, maxTokens, temperature)
    }
}

sealed class LlmResult {
    data class Ok(val text: String) : LlmResult()
    data class Error(val message: String) : LlmResult()
}

/** How a provider is spoken to. Most hosts speak OpenAI's Chat Completions; Anthropic and Bedrock have their own. */
enum class Protocol { OPENAI_COMPATIBLE, ANTHROPIC, BEDROCK }

/**
 * The providers Settings › AI offers. Open models (Llama, Qwen, Gemma, gpt-oss, DeepSeek) run through Groq,
 * OpenRouter, Bedrock, DeepSeek or a local server; [CUSTOM] takes any other OpenAI-compatible https address.
 * Every endpoint here is listed in scripts/audit_apk.py, and none is contacted until it is picked and an AI feature runs.
 */
enum class ProviderKind(
    val id: String,
    val label: String,
    val assistantName: String,
    val protocol: Protocol,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyHint: String,
    val needsKey: Boolean = true,
    /** The address can be changed (a local server, another compatible host). */
    val editableUrl: Boolean = false,
) {
    OPENAI("openai", "OpenAI", "ChatGPT", Protocol.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-4o-mini", "sk-…"),
    ANTHROPIC("anthropic", "Anthropic", "Claude", Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-haiku-5-5", "sk-ant-…"),
    GEMINI("gemini", "Google Gemini", "Gemini", Protocol.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "AIza…"),
    BEDROCK("bedrock", "AWS Bedrock", "Bedrock", Protocol.BEDROCK, "https://bedrock-runtime.{region}.amazonaws.com", "us.meta.llama3-3-70b-instruct-v1:0", "Bedrock API key"),
    GROQ("groq", "Groq", "Groq", Protocol.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "gsk_…"),
    OPENROUTER("openrouter", "OpenRouter", "OpenRouter", Protocol.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct", "sk-or-…"),
    MISTRAL("mistral", "Mistral", "Mistral", Protocol.OPENAI_COMPATIBLE, "https://api.mistral.ai/v1", "mistral-small-latest", "Mistral API key"),
    DEEPSEEK("deepseek", "DeepSeek", "DeepSeek", Protocol.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", "deepseek-chat", "sk-…"),
    LOCAL("local", "Local model (Ollama, LM Studio, llama.cpp)", "your local model", Protocol.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "llama3.2", "Key, if the server needs one", needsKey = false, editableUrl = true),
    CUSTOM("custom", "Other (OpenAI-compatible)", "your model", Protocol.OPENAI_COMPATIBLE, "https://", "", "API key", needsKey = false, editableUrl = true);

    companion object {
        fun fromId(id: String?): ProviderKind = entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

/** One provider as Settings has it: which kind, which model, where, and its key. */
data class ProviderConfig(
    val kind: ProviderKind,
    val model: String = kind.defaultModel,
    val baseUrl: String = kind.defaultBaseUrl,
    val region: String = "us-east-1",
    val apiKey: String? = null,
) {
    /** Everything needed to ask: a key when the provider needs one, a model, an address that is allowed. */
    val ready: Boolean get() = model.isNotBlank() && (!kind.needsKey || !apiKey.isNullOrBlank()) && HttpJson.allowed(resolvedBaseUrl)

    val resolvedBaseUrl: String get() = baseUrl.trim().trimEnd('/').replace("{region}", region.trim().ifBlank { "us-east-1" })
}

/** The strategy: the one adapter that speaks [ProviderConfig.kind]'s protocol, or null when it isn't set up yet. */
object Providers {
    fun build(config: ProviderConfig): LlmProvider? {
        if (!config.ready) return null
        val key = config.apiKey?.trim()?.takeIf { it.isNotEmpty() }
        return when (config.kind.protocol) {
            Protocol.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(config.kind, config.resolvedBaseUrl, config.model.trim(), key)
            Protocol.ANTHROPIC -> AnthropicProvider(config.resolvedBaseUrl, config.model.trim(), key!!)
            Protocol.BEDROCK -> BedrockProvider(config.resolvedBaseUrl, config.model.trim(), key!!)
        }
    }
}
