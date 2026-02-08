package com.example.chat

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "ChatAAPrefs"
    private const val KEY_PROVIDER = "provider" // "openai" or "gemini"
    private const val KEY_OPENAI_KEY = "openai_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"
    private const val KEY_GEMINI_KEY = "gemini_key"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"

    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getProvider(context: Context): String {
        return getPreferences(context).getString(KEY_PROVIDER, "openai") ?: "openai"
    }

    fun setProvider(context: Context, provider: String) {
        getPreferences(context).edit().putString(KEY_PROVIDER, provider).apply()
    }

    fun getApiKey(context: Context, provider: String): String {
        val key = if (provider == "openai") KEY_OPENAI_KEY else KEY_GEMINI_KEY
        return getPreferences(context).getString(key, "") ?: ""
    }

    fun setApiKey(context: Context, provider: String, apiKey: String) {
        val key = if (provider == "openai") KEY_OPENAI_KEY else KEY_GEMINI_KEY
        getPreferences(context).edit().putString(key, apiKey).apply()
    }
    
    fun getSystemPrompt(context: Context): String {
        return getPreferences(context).getString(KEY_SYSTEM_PROMPT, 
            "You are a helpful driving assistant. Keep answers short, safe, and concise.") ?: ""
    }

    fun getGeminiModel(context: Context): String {
        return getPreferences(context).getString(KEY_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"
    }

    fun setGeminiModel(context: Context, model: String) {
        getPreferences(context).edit().putString(KEY_GEMINI_MODEL, model).apply()
    }
}
