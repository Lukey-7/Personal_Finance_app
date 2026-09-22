package com.pft.financetracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * All user settings. The OpenAI API key is stored in EncryptedSharedPreferences backed by an
 * AES-256-GCM key in the Android Keystore. It is never logged, never exported, never hardcoded.
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

    init {
        _hasApiKey.value = runCatching { !secure.getString(KEY_API, null).isNullOrBlank() }.getOrDefault(false)
    }

    fun getApiKey(): String? = runCatching { secure.getString(KEY_API, null)?.takeIf { it.isNotBlank() } }.getOrNull()

    fun setApiKey(key: String?) {
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) secure.edit().remove(KEY_API).apply()
        else secure.edit().putString(KEY_API, trimmed).apply()
        // A key typed by hand is the user's own; only the debug seeder marks one as seeded.
        plain.edit().putBoolean(KEY_API_SEEDED, false).apply()
        _hasApiKey.value = !trimmed.isNullOrEmpty()
    }

    /**
     * Debug-build helper: adopt a key supplied at build time from OPENAI_API_KEY. It replaces a key that a
     * previous build seeded (so rebuilding with a different key actually takes effect) but never one you
     * entered yourself.
     */
    fun seedApiKey(key: String) {
        val current = getApiKey()
        val seededBefore = plain.getBoolean(KEY_API_SEEDED, false)
        if (!current.isNullOrBlank() && !seededBefore) return
        if (current == key) return
        secure.edit().putString(KEY_API, key).apply()
        plain.edit().putBoolean(KEY_API_SEEDED, true).apply()
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
        _onboarded.value = false
        _lastImportAt.value = 0L
        _autoImport.value = true
        _countCashAsSpend.value = true
        _myName.value = "Me"
    }

    private companion object {
        const val KEY_API = "openai_api_key"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_LAST_IMPORT = "last_import_at"
        const val KEY_AUTO_IMPORT = "auto_import"
        const val KEY_CASH_SPEND = "cash_as_spend"
        const val KEY_MY_NAME = "my_name"
        const val KEY_API_SEEDED = "api_key_seeded"
    }
}
