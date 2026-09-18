package com.iris.assistant.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores API keys and provider settings encrypted on-device.
 *
 * Supports multiple free/OpenAI-compatible providers so IRIS can try them
 * in order when the user wants zero-cost online intelligence.
 *
 * Recommended free providers (only need email signup):
 *  - Groq          https://console.groq.com
 *  - Google AI     https://aistudio.google.com
 *  - OpenRouter    https://openrouter.ai  (has free models)
 *  - Together AI   https://api.together.xyz
 *  - Cloudflare    Workers AI
 */
object ApiKeyStore {
    private const val PREFS_NAME = "iris_secure_prefs"
    private const val KEY_API_KEY = "openai_api_key"
    private const val KEY_MODEL = "openai_model"
    private const val KEY_BASE_URL = "openai_base_url"
    private const val KEY_PROVIDERS = "providers_json"

    const val DEFAULT_MODEL = "gpt-4o-mini"
    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

    data class Provider(
        val name: String,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val enabled: Boolean = true
    )

    /** Presets the user can quickly pick from (all have free tiers via email). */
    val FREE_PRESETS = listOf(
        Provider("Groq", "https://api.groq.com/openai/v1", "", "llama-3.3-70b-versatile"),
        Provider("Google AI Studio", "https://generativelanguage.googleapis.com/v1beta/openai", "", "gemini-2.0-flash"),
        Provider("OpenRouter (free)", "https://openrouter.ai/api/v1", "", "meta-llama/llama-3.2-3b-instruct:free"),
        Provider("OpenAI", "https://api.openai.com/v1", "", "gpt-4o-mini")
    )

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cachedPrefs = created
            return created
        }
    }

    // ---- single-provider (backward compatible) ----

    fun getApiKey(context: Context): String? =
        prefs(context).getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KEY_API_KEY).apply()
    }

    fun getModel(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun setModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun getBaseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL)?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URL

    fun setBaseUrl(context: Context, baseUrl: String) {
        val cleaned = baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        prefs(context).edit().putString(KEY_BASE_URL, cleaned.ifBlank { DEFAULT_BASE_URL }).apply()
    }

    // ---- multi-provider support ----

    fun getProviders(context: Context): List<Provider> {
        val json = prefs(context).getString(KEY_PROVIDERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Provider(
                    name = o.optString("name", "Provider"),
                    baseUrl = o.optString("baseUrl", DEFAULT_BASE_URL),
                    apiKey = o.optString("apiKey", ""),
                    model = o.optString("model", DEFAULT_MODEL),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setProviders(context: Context, providers: List<Provider>) {
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(JSONObject()
                .put("name", p.name)
                .put("baseUrl", p.baseUrl)
                .put("apiKey", p.apiKey)
                .put("model", p.model)
                .put("enabled", p.enabled)
            )
        }
        prefs(context).edit().putString(KEY_PROVIDERS, arr.toString()).apply()
    }

    /** Returns all usable providers: multi-list first, then the single legacy key. */
    fun getAllUsableProviders(context: Context): List<Provider> {
        val multi = getProviders(context).filter { it.enabled && it.apiKey.isNotBlank() }
        if (multi.isNotEmpty()) return multi

        val key = getApiKey(context) ?: return emptyList()
        return listOf(
            Provider(
                name = "Default",
                baseUrl = getBaseUrl(context),
                apiKey = key,
                model = getModel(context)
            )
        )
    }
}
