package com.realtek.chat.settings

import android.content.Context
import com.realtek.chat.storage.CryptoBox

class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("realtek_settings", Context.MODE_PRIVATE)
    private val crypto = CryptoBox()

    val haijingEndpoint: String
        get() = "https://api.haijingai.com/v2/chat/completions"

    var haijingApiKey: String
        get() = prefs.getString("haijing_api_key", null)
            ?.let(crypto::decryptText)
            .orEmpty()
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove("haijing_api_key").apply()
            } else {
                prefs.edit()
                    .putString(
                        "haijing_api_key",
                        crypto.encryptText(value.trim())
                    )
                    .apply()
            }
        }

    var haijingDefaultModel: String
        get() = prefs.getString(
            "haijing_default_model",
            "grok-4.6"
        ) ?: "grok-4.6"
        set(value) = prefs.edit()
            .putString(
                "haijing_default_model",
                value.trim()
            )
            .apply()

    val deepSeekEndpoint: String
        get() = "https://api.deepseek.com/chat/completions"

    var deepSeekApiKey: String
        get() = prefs.getString(
            "deepseek_api_key",
            null
        )
            ?.let(crypto::decryptText)
            .orEmpty()
        set(value) {
            if (value.isBlank()) {
                prefs.edit()
                    .remove("deepseek_api_key")
                    .apply()
            } else {
                prefs.edit()
                    .putString(
                        "deepseek_api_key",
                        crypto.encryptText(value.trim())
                    )
                    .apply()
            }
        }

    var deepSeekDefaultModel: String
        get() = prefs.getString(
            "deepseek_default_model",
            "deepseek-flash"
        ) ?: "deepseek-flash"
        set(value) = prefs.edit()
            .putString(
                "deepseek_default_model",
                value.trim()
            )
            .apply()

    var sleepMode: Boolean
        get() = prefs.getBoolean("sleep_mode", false)
        set(value) = prefs.edit()
            .putBoolean("sleep_mode", value)
            .apply()

    // 后台任务优先走 DeepSeek；没配 DeepSeek 时才退回海鲸。
    val systemProvider: String
        get() = if (providerConfigured("deepseek")) {
            "deepseek"
        } else {
            "haijing"
        }

    val plannerModel: String
        get() = defaultModelFor(systemProvider)

    val memoryModel: String
        get() = defaultModelFor(systemProvider)

    val proactiveModel: String
        get() = defaultModelFor(systemProvider)

    // 兼容旧代码：快速聊天不再换模型，避免“线路A + 模型B”混用。
    val fastChatModel: String
        get() = ""

    // 兼容旧代码名称。
    val defaultChatModel: String
        get() = haijingDefaultModel

    fun providerConfigured(provider: String): Boolean =
        when (normalizeProvider(provider)) {
            "deepseek" ->
                deepSeekApiKey.isNotBlank()

            else ->
                haijingApiKey.isNotBlank()
        }

    fun endpointFor(provider: String): String =
        when (normalizeProvider(provider)) {
            "deepseek" -> deepSeekEndpoint
            else -> haijingEndpoint
        }

    fun apiKeyFor(provider: String): String =
        when (normalizeProvider(provider)) {
            "deepseek" -> deepSeekApiKey
            else -> haijingApiKey
        }

    fun defaultModelFor(provider: String): String =
        when (normalizeProvider(provider)) {
            "deepseek" -> deepSeekDefaultModel
            else -> haijingDefaultModel
        }

    fun configured(): Boolean =
        providerConfigured(systemProvider)

    fun normalizeProvider(provider: String): String =
        if (provider.lowercase() == "deepseek") {
            "deepseek"
        } else {
            "haijing"
        }
}
