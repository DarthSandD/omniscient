package com.darrenai.omniscient

import android.content.Context
import android.content.SharedPreferences

/** Backend + voice settings. Defaults point at OpenAI; any OpenAI-compatible URL works. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("omniscient", Context.MODE_PRIVATE)

    var endpoint: String
        get() = sp.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(v) = sp.edit().putString(KEY_ENDPOINT, v.trim().trimEnd('/')).apply()

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_KEY, v.trim()).apply()

    var model: String
        get() = sp.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString(KEY_MODEL, v.trim()).apply()

    var speakReplies: Boolean
        get() = sp.getBoolean(KEY_TTS, true)
        set(v) = sp.edit().putBoolean(KEY_TTS, v).apply()

    var searchEndpoint: String
        get() = sp.getString(KEY_SEARCH_EP, "") ?: ""
        set(v) = sp.edit().putString(KEY_SEARCH_EP, v.trim().trimEnd('/')).apply()

    var searchKey: String
        get() = sp.getString(KEY_SEARCH_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_SEARCH_KEY, v.trim()).apply()

    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(v) = sp.edit().putBoolean(KEY_ONBOARDED, v).apply()

    /** One-tap preset for Darren's LAN OmniRoute proxy (keyless, OpenAI-compatible). */
    fun applyOmniRoutePreset() {
        endpoint = OMNIROUTE_ENDPOINT
        apiKey = OMNIROUTE_KEY
        model = OMNIROUTE_MODEL
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val OMNIROUTE_ENDPOINT = "http://10.212.104.124:20128/v1"
        const val OMNIROUTE_KEY = "x"
        const val OMNIROUTE_MODEL = "default"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TTS = "tts_enabled"
        private const val KEY_SEARCH_EP = "search_endpoint"
        private const val KEY_SEARCH_KEY = "search_key"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
