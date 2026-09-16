package com.realtek.chat.storage

import android.content.Context

class ProfileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("realtek_profile", Context.MODE_PRIVATE)

    var nickname: String
        get() = prefs.getString("nickname", "我") ?: "我"
        set(value) = prefs.edit().putString("nickname", value.trim().ifBlank { "我" }).apply()

    var avatarPath: String?
        get() = prefs.getString("avatar_path", null)
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove("avatar_path").apply()
            else prefs.edit().putString("avatar_path", value).apply()
        }
}
