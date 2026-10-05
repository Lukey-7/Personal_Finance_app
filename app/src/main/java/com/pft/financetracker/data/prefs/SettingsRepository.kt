package com.pft.financetracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * All user settings. The OpenAI API key is stored in EncryptedSharedPreferences backed by an
 * AES-256-GCM key in the Android Keystore. It is never logged or exported. A key can be built into the app
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

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey

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

    /** Reminders already posted, as "thing@dueDay#lead" keys (no amounts or names), so none is sent twice. */
    fun sentReminders(): Set<String> = plain.getStringSet(KEY_REMINDERS_SENT, emptySet())?.toSet() ?: emptySet()
    fun setSentReminders(keys: Set<String>) { plain.edit().putStringSet(KEY_REMINDERS_SENT, keys).apply() }

    /**
     * AI answers kept by request, so an unchanged week is never asked (or paid for) twice. The requests are the
     * anonymised payloads, the answers the model's JSON; at most 300 are kept.
     */
    val aiAnswerCache = PrefsAiAnswerCache(context)

    init {
        _hasApiKey.value = runCatching { !secure.getString(KEY_API, null).isNullOrBlank() }.getOrDefault(false)
    }

    fun getApiKey(): String? = runCatching { secure.getString(KEY_API, null)?.takeIf { it.isNotBlank() } }.getOrNull()

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
        _hasApiKey.value = !trimmed.isNullOrEmpty()
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
        _hasApiKey.value = true
    }

    fun setOnboarded(v: Boolean) { plain.edit().putBoolean(KEY_ONBOARDED, v).apply(); _onboarded.value = v }
    fun setLastImportAt(t: Long) { plain.edit().putLong(KEY_LAST_IMPORT, t).apply(); _lastImportAt.value = t }
    fun setAutoImport(v: Boolean) { plain.edit().putBoolean(KEY_AUTO_IMPORT, v).apply(); _autoImport.value = v }
    fun setCountCashAsSpend(v: Boolean) { plain.edit().putBoolean(KEY_CASH_SPEND, v).apply(); _countCashAsSpend.value = v }
    fun setMyName(v: String) { val n = v.trim().ifBlank { "Me" }; plain.edit().putString(KEY_MY_NAME, n).apply(); _myName.value = n }

    fun clearAll() {
        runCatching { secure.edit().clear().apply() }
        plain.edit().clear().apply()
        _hasApiKey.value = false
        _apiKeyBuiltIn.value = false
        _onboarded.value = false
        _lastImportAt.value = 0L
        _autoImport.value = true
        _countCashAsSpend.value = true
        _myName.value = "Me"
        _splitAi.value = true
        _remindersEnabled.value = false
        _widgetHideAmounts.value = true
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
        const val KEY_API_SEEDED = "api_key_seeded"
        const val KEY_API_USER_MANAGED = "api_key_user_managed"
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
