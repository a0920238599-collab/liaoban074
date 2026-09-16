package com.realtek.chat.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

class SecureMediaStore(context: Context) {
    private val appContext = context.applicationContext
    private val crypto = CryptoBox()
    private val dir = File(appContext.filesDir, "secure_media").apply { mkdirs() }

    fun importImage(
        uri: Uri,
        prefix: String,
        maxDimension: Int = 1600,
        quality: Int = 88
    ): String {
        val bitmap = appContext.contentResolver
            .openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it) }
            ?: error("无法读取选择的图片")

        val scaled = scale(bitmap, maxDimension)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 95), out)

        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()

        val encrypted = crypto.encryptBytes(out.toByteArray())
        val file = File(
            dir,
            "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.bin"
        )
        file.writeBytes(encrypted)

        return "secure_media/${file.name}"
    }

    fun loadBytes(relativePath: String?): ByteArray? {
        if (relativePath.isNullOrBlank()) return null
        val file = File(appContext.filesDir, relativePath)
        if (!file.exists()) return null
        return runCatching { crypto.decryptBytes(file.readBytes()) }.getOrNull()
    }

    fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        runCatching { File(appContext.filesDir, relativePath).delete() }
    }

    private fun scale(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
