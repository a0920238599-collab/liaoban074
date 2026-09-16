package com.realtek.chat.storage

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("realtek_lock", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean =
        prefs.contains("salt") && prefs.contains("hash")

    fun setPin(pin: String) {
        require(pin.matches(Regex("\\d{4}")))
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltText = prefs.getString("salt", null) ?: return false
        val hashText = prefs.getString("hash", null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        val actual = derive(pin, salt)
        if (expected.size != actual.size) return false

        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].toInt() xor actual[i].toInt())
        }
        return diff == 0
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verify(oldPin)) return false
        setPin(newPin)
        return true
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }
}
