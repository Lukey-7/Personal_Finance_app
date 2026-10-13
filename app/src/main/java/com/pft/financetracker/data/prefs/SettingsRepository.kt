package com.pft.financetracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pft.financetracker.domain.ai.LlmProvider
import com.pft.financetracker.domain.ai.ProviderConfig
import com.pft.financetracker.domain.ai.ProviderKind
import com.pft.financetracker.domain.ai.Providers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * All user settings. AI provider keys (one per provider) are stored in EncryptedSharedPreferences backed by an
 * AES-256-GCM key in the Android Keystore. They are never logged or exported. An OpenAI key can be built into the app
 * at build time (see app/build.gradle.kts); it is copied in here once and can then be changed or removed.
 */
class SettingsRepository(context: Context) {

    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "fintrack_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val plain: SharedPreferences = context.getSharedPreferences("fintrack_prefs", Context.MODE_PRIVATE)

    /** The active AI provider is set up: it has a key (or needs none), a model and an allowed address. */
    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey

    /** The AI provider every AI feature uses (Settings › AI). OpenAI unless the person picked another. */
    private val _aiProvider = MutableStateFlow(ProviderKind.fromId(plain.getString(KEY_AI_PROVIDER, null)))
    val aiProvider: StateFlow<ProviderKind> = _aiProvider

    /** The active provider's settings, with the key blanked (screens only need to know whether one is saved). */
    private val _aiConfig = MutableStateFlow(ProviderConfig(ProviderKind.OPENAI))
    val aiConfig: StateFlow<ProviderConfig> = _aiConfig

    private val _onboarded = MutableStateFlow(plain.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded

    private val _lastImportAt = MutableStateFlow(plain.getLong(KEY_LAST_IMPORT, 0L))
    val lastImportAt: StateFlow<Long> = _lastImportAt

    private val _autoImport = MutableStateFlow(plain.getBoolean(KEY_AUTO_IMPORT, true))
    val autoImport: StateFlow<Boolean> = _autoImport

    /** Whether ATM / cash withdrawals count towards "spend". Default on: cash out is usually spent. */
    private val _countCashAsSpend = MutableStateFlow(plain.getBoolean(KEY_CASH_SPEND, true))
    val countCashAsSpend: StateFlow<Boolean> = _countCashAsSpend

    /** Display name used for "me" in bill splits. */
    private val _myName = MutableStateFlow(plain.getString(KEY_MY_NAME, "Me") ?: "Me")
    val myName: StateFlow<String> = _myName

    /** Split intelligence may ask the AI (with the user's key) about unclear group payments. On by default. */
    private val _splitAi = MutableStateFlow(plain.getBoolean(KEY_SPLIT_AI, true))
    val splitAi: StateFlow<Boolean> = _splitAi
    fun setSplitAi(v: Boolean) { plain.edit().putBoolean(KEY_SPLIT_AI, v).apply(); _splitAi.value = v }

    /** Local notifications before bills, EMIs and renewals. Off until the user switches it on (and grants the permission). */
    private val _remindersEnabled = MutableStateFlow(plain.getBoolean(KEY_REMINDERS, false))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled
    fun setRemindersEnabled(v: Boolean) { plain.edit().putBoolean(KEY_REMINDERS, v).apply(); _remindersEnabled.value = v }

    /** The home-screen widget shows "₹••••" instead of figures. On by default: anyone can see a home screen. */
    private val _widgetHideAmounts = MutableStateFlow(plain.getBoolean(KEY_WIDGET_HIDE, true))
    val widgetHideAmounts: StateFlow<Boolean> = _widgetHideAmounts
    fun setWidgetHideAmounts(v: Boolean) { plain.edit().putBoolean(KEY_WIDGET_HIDE, v).apply(); _widgetHideAmounts.value = v }

    /** Ask may hand questions its rules do not understand to Gemini Nano on the phone, where available. On by default. */
    private val _useNano = MutableStateFlow(plain.getBoolean(KEY_USE_NANO, true))
    val useNano: StateFlow<Boolean> = _useNano
    fun setUseNano(v: Boolean) { plain.edit().putBoolean(KEY_USE_NANO, v).apply(); _useNano.value = v }

    /**
     * Ask sends every question, with a summary of the person's payments (see [com.pft.financetracker.domain.ask.AskAiPrompt]),
     * to ChatGPT when there is an OpenAI key. On by default; the rules on the phone answer when it is off or offline.
     */
    private val _askUseOpenAi = MutableStateFlow(plain.getBoolean(KEY_ASK_OPENAI, true))
    val askUseOpenAi: StateFlow<Boolean> = _askUseOpenAi
    fun setAskUseOpenAi(v: Boolean) { plain.edit().putBoolean(KEY_ASK_OPENAI, v).apply(); _askUseOpenAi.value = v }

    /** When the person last saved an encrypted backup; 0 = never. Drives the monthly "time for a backup" reminder. */
    private val _lastBackupAt = MutableStateFlow(plain.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt
    fun setLastBackupAt(t: Long) { plain.edit().putLong(KEY_LAST_BACKUP, t).apply(); _lastBackupAt.value = t }

    /** Reminders already posted, as "thing@dueDay#lead" keys (no amounts or names), so none is sent twice. */
    fun sentReminders(): Set<String> = plain.getStringSet(KEY_REMINDERS_SENT, emptySet())?.toSet() ?: emptySet()
    fun setSentReminders(keys: Set<String>) { plain.edit().putStringSet(KEY_REMINDERS_SENT, keys).apply() }

    /**
     * AI answers kept by request, so an unchanged week is never asked (or paid for) twice. The requests are the
     * anonymised payloads, the answers the model's JSON; at most 300 are kept.
     */
    val aiAnswerCache = PrefsAiAnswerCache(context)

    init {
        refreshAi()
    }

    /** The saved key for [kind]; OpenAI's stays where it was before v1.5.1, so nothing is lost on upgrade. */
    fun getApiKey(kind: ProviderKind = ProviderKind.OPENAI): String? =
        runCatching { secure.getString(keyFor(kind), null)?.takeIf { it.isNotBlank() } }.getOrNull()

    /** Everything needed to ask [kind], key included. */
    fun providerConfig(kind: ProviderKind = aiProvider.value) = ProviderConfig(
        kind = kind,
        model = plain.getString(KEY_AI_MODEL + kind.id, null) ?: kind.defaultModel,
        baseUrl = plain.getString(KEY_AI_URL + kind.id, null) ?: kind.defaultBaseUrl,
        region = plain.getString(KEY_AI_REGION, null) ?: "us-east-1",
        apiKey = getApiKey(kind),
    )

    /** The provider the AI features use right now, or null when it isn't set up. */
    fun activeLlm(): LlmProvider? = Providers.build(providerConfig())

    fun setAiProvider(kind: ProviderKind) { plain.edit().putString(KEY_AI_PROVIDER, kind.id).apply(); _aiProvider.value = kind; refreshAi() }
    fun setAiModel(kind: ProviderKind, model: String) { plain.edit().putString(KEY_AI_MODEL + kind.id, model.trim()).apply(); refreshAi() }
    fun setAiBaseUrl(kind: ProviderKind, url: String) { plain.edit().putString(KEY_AI_URL + kind.id, url.trim()).apply(); refreshAi() }
    fun setAiRegion(region: String) { plain.edit().putString(KEY_AI_REGION, region.trim()).apply(); refreshAi() }

    /** A key for any provider. OpenAI's goes through [setApiKey], which keeps the built-in key's rules. */
    fun setProviderKey(kind: ProviderKind, key: String?) {
        if (kind == ProviderKind.OPENAI) return setApiKey(key)
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) secure.edit().remove(keyFor(kind)).apply() else secure.edit().putString(keyFor(kind), trimmed).apply()
        refreshAi()
    }

    private fun keyFor(kind: ProviderKind) = if (kind == ProviderKind.OPENAI) KEY_API else KEY_API_PREFIX + kind.id

    private fun refreshAi() {
        val config = runCatching { providerConfig() }.getOrDefault(ProviderConfig(aiProvider.value))
        _hasApiKey.value = config.ready
        _aiConfig.value = config.copy(apiKey = config.apiKey?.let { "" })
    }

    /** True while the saved key is the one built into the app, false once you have set or removed it yourself. */
    private val _apiKeyBuiltIn = MutableStateFlow(plain.getBoolean(KEY_API_SEEDED, false))
    val apiKeyBuiltIn: StateFlow<Boolean> = _apiKeyBuiltIn

    fun setApiKey(key: String?) {
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) secure.edit().remove(KEY_API).apply()
        else secure.edit().putString(KEY_API, trimmed).apply()
        // Setting or removing a key by hand makes the key yours: the built-in one is not put back on the
        // next launch, not even after a removal. Clear all data resets this along with everything else.
        plain.edit().putBoolean(KEY_API_SEEDED, false).putBoolean(KEY_API_USER_MANAGED, true).apply()
        _apiKeyBuiltIn.value = false
        refreshAi()
    }

    /**
     * Adopt the key built into the app (OPENAI_API_KEY at build time). It replaces a key that a previous
     * build put there, so rebuilding with a different key takes effect, but never a key you typed or
     * removed yourself in Settings.
     */
    fun seedApiKey(key: String) {
        if (plain.getBoolean(KEY_API_USER_MANAGED, false)) return
        val current = getApiKey()
        val seededBefore = plain.getBoolean(KEY_API_SEEDED, false)
        if (!current.isNullOrBlank() && !seededBefore) return
        if (current == key) return
        secure.edit().putString(KEY_API, key).apply()
        plain.edit().putBoolean(KEY_API_SEEDED, true).apply()
        _apiKeyBuiltIn.value = true
        refreshAi()
    }

    fun setOnboarded(v: Boolean) { plain.edit().putBoolean(KEY_ONBOARDED, v).apply(); _onboarded.value = v }
    fun setLastImportAt(t: Long) { plain.edit().putLong(KEY_LAST_IMPORT, t).apply(); _lastImportAt.value = t }
    fun setAutoImport(v: Boolean) { plain.edit().putBoolean(KEY_AUTO_IMPORT, v).apply(); _autoImport.value = v }
    /** The counting rules as set now. Every figure (screens, the widget, Ask) reads through these. */
    fun countingRules() = com.pft.financetracker.domain.books.CountingRules(cashIsSpend = countCashAsSpend.value)

    fun setCountCashAsSpend(v: Boolean) { plain.edit().putBoolean(KEY_CASH_SPEND, v).apply(); _countCashAsSpend.value = v }
    fun setMyName(v: String) { val n = v.trim().ifBlank { "Me" }; plain.edit().putString(KEY_MY_NAME, n).apply(); _myName.value = n }

    fun clearAll() {
        runCatching { secure.edit().clear().apply() }
        plain.edit().clear().apply()
        _aiProvider.value = ProviderKind.OPENAI
        refreshAi()
        _apiKeyBuiltIn.value = false
        _onboarded.value = false
        _lastImportAt.value = 0L
        _autoImport.value = true
        _countCashAsSpend.value = true
        _myName.value = "Me"
        _splitAi.value = true
        _remindersEnabled.value = false
        _widgetHideAmounts.value = true
        _lastBackupAt.value = 0L
        _useNano.value = true
        _askUseOpenAi.value = true
        aiAnswerCache.clear()
    }

    private companion object {
        const val KEY_API = "openai_api_key"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_LAST_IMPORT = "last_import_at"
        const val KEY_AUTO_IMPORT = "auto_import"
        const val KEY_CASH_SPEND = "cash_as_spend"
        const val KEY_MY_NAME = "my_name"
        const val KEY_SPLIT_AI = "split_ai"
        const val KEY_REMINDERS = "reminders_enabled"
        const val KEY_REMINDERS_SENT = "reminders_sent"
        const val KEY_WIDGET_HIDE = "widget_hide_amounts"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_USE_NANO = "use_nano"
        const val KEY_ASK_OPENAI = "ask_use_openai"
        const val KEY_API_SEEDED = "api_key_seeded"
        const val KEY_API_USER_MANAGED = "api_key_user_managed"
        /** Keys for providers other than OpenAI: "api_key_anthropic", "api_key_bedrock"… (encrypted prefs). */
        const val KEY_API_PREFIX = "api_key_"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_AI_MODEL = "ai_model_"
        const val KEY_AI_URL = "ai_url_"
        const val KEY_AI_REGION = "ai_region"
    }
}

/**
 * AI answers kept by request, so an unchanged week is never asked (or paid for) twice. Keys are hashes of the
 * anonymised payloads; values the model's JSON. At most about 300 are kept.
 */
class PrefsAiAnswerCache(context: Context) : com.pft.financetracker.data.split.AiAnswerCache {
    private val prefs = context.getSharedPreferences("fintrack_ai_cache", Context.MODE_PRIVATE)
    private fun key(req: String) = java.security.MessageDigest.getInstance("SHA-256").digest(req.toByteArray()).joinToString("") { "%02x".format(it) }
    override fun get(requestJson: String): String? = prefs.getString(key(requestJson), null)
    override fun put(requestJson: String, answer: String) {
        val e = prefs.edit()
        if (prefs.all.size > 300) prefs.all.keys.take(100).forEach { e.remove(it) }
        e.putString(key(requestJson), answer).apply()
    }
    fun clear() = prefs.edit().clear().apply()
}
