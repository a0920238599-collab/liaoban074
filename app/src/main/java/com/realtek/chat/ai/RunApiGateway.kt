package com.realtek.chat.ai

import android.content.Context
import android.util.Base64
import com.google.gson.*
import com.realtek.chat.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.util.concurrent.TimeUnit

class RunApiGateway(context: Context) {
    private val settings = AppSettings(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        provider: String = settings.systemProvider,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBytes: ByteArray? = null,
        imageMime: String = "image/jpeg",
        maxTokens: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val normalizedProvider =
            settings.normalizeProvider(provider)

        requireConfigured(
            normalizedProvider,
            model
        )

        val payload = makePayload(
            provider = normalizedProvider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imageBytes = imageBytes,
            imageMime = imageMime,
            maxTokens = maxTokens,
            stream = false
        )

        executeJson(
            provider = normalizedProvider,
            payload = payload
        )
    }

suspend fun streamChat(
    provider: String = settings.systemProvider,
    model: String,
    systemPrompt: String,
    userPrompt: String,
    maxTokens: Int? = null,
    onPartialText: (String) -> Unit
): String = withContext(Dispatchers.IO) {
    val normalizedProvider =
        settings.normalizeProvider(provider)

    requireConfigured(
        normalizedProvider,
        model
    )

    val payload = makePayload(
        provider = normalizedProvider,
        model = model,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        imageBytes = null,
        imageMime = "image/jpeg",
        maxTokens = maxTokens,
        stream = true
    )

    val key =
        settings.apiKeyFor(normalizedProvider)

    val request = Request.Builder()
        .url(
            settings.endpointFor(
                normalizedProvider
            )
        )
        .header(
            "Authorization",
            "Bearer $key"
        )
        .header(
            "Accept",
            "text/event-stream"
        )
        .header(
            "Content-Type",
            "application/json"
        )
        .post(
            payload.toString()
                .toRequestBody(
                    "application/json; charset=utf-8"
                        .toMediaType()
                )
        )
        .build()

    val call = client.newCall(request)

    try {
        call.execute().use { response ->
            val body = response.body
            val contentType =
                response.header(
                    "Content-Type"
                ).orEmpty()

            if (!response.isSuccessful) {
                val raw =
                    body?.string().orEmpty()

                if (
                    response.code in
                    listOf(
                        400,
                        404,
                        405,
                        415,
                        422,
                        501
                    )
                ) {
                    fallbackNormal(
                        provider =
                            normalizedProvider,
                        model = model,
                        systemPrompt =
                            systemPrompt,
                        userPrompt =
                            userPrompt,
                        maxTokens =
                            maxTokens,
                        onPartialText =
                            onPartialText
                    )
                } else {
                    error(
                        providerErrorPrefix(
                            normalizedProvider
                        ) +
                            " HTTP " +
                            response.code +
                            ": " +
                            raw
                                .replace(
                                    key,
                                    "***"
                                )
                                .take(900)
                    )
                }
            } else if (body == null) {
                fallbackNormal(
                    provider =
                        normalizedProvider,
                    model = model,
                    systemPrompt =
                        systemPrompt,
                    userPrompt =
                        userPrompt,
                    maxTokens =
                        maxTokens,
                    onPartialText =
                        onPartialText
                )
            } else if (
                !contentType.contains(
                    "text/event-stream",
                    ignoreCase = true
                )
            ) {
                val raw = body.string()
                val parsedText =
                    extractNormalText(raw)

                if (parsedText.isNotBlank()) {
                    withContext(
                        Dispatchers.Main.immediate
                    ) {
                        onPartialText(
                            parsedText
                        )
                    }

                    parsedText
                } else {
                    fallbackNormal(
                        provider =
                            normalizedProvider,
                        model = model,
                        systemPrompt =
                            systemPrompt,
                        userPrompt =
                            userPrompt,
                        maxTokens =
                            maxTokens,
                        onPartialText =
                            onPartialText
                    )
                }
            } else {
                val full =
                    StringBuilder()

                readSse(
                    body.source()
                ) { delta ->
                    currentCoroutineContext()
                        .ensureActive()

                    if (delta.isNotEmpty()) {
                        full.append(delta)

                        val snapshot =
                            full.toString()

                        withContext(
                            Dispatchers.Main.immediate
                        ) {
                            onPartialText(
                                snapshot
                            )
                        }
                    }
                }

                val result =
                    full.toString()
                        .trim()

                if (result.isNotBlank()) {
                    result
                } else {
                    fallbackNormal(
                        provider =
                            normalizedProvider,
                        model = model,
                        systemPrompt =
                            systemPrompt,
                        userPrompt =
                            userPrompt,
                        maxTokens =
                            maxTokens,
                        onPartialText =
                            onPartialText
                    )
                }
            }
        }
    } catch (
        t: Throwable
    ) {
        if (
            t is
            kotlinx.coroutines.CancellationException
        ) {
            call.cancel()
            throw t
        }

        fallbackNormal(
            provider =
                normalizedProvider,
            model = model,
            systemPrompt =
                systemPrompt,
            userPrompt =
                userPrompt,
            maxTokens =
                maxTokens,
            onPartialText =
                onPartialText
        )
    }
}

    private suspend fun fallbackNormal(
        provider: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int?,
        onPartialText: (String) -> Unit
    ): String {
        val text = chat(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = maxTokens
        )

        withContext(
            Dispatchers.Main.immediate
        ) {
            onPartialText(text)
        }

        return text
    }

    private fun makePayload(
        provider: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBytes: ByteArray?,
        imageMime: String,
        maxTokens: Int?,
        stream: Boolean
    ): JsonObject {
        return JsonObject().apply {
            addProperty(
                "model",
                model
            )

            addProperty(
                "stream",
                stream
            )

            maxTokens
                ?.takeIf { it > 0 }
                ?.let {
                    addProperty(
                        "max_tokens",
                        it
                    )
                }

            // Official DeepSeek currently supports thinking mode.
            // For everyday chat we explicitly disable it to reduce latency.
            if (
                provider == "deepseek"
            ) {
                add(
                    "thinking",
                    JsonObject().apply {
                        addProperty(
                            "type",
                            "disabled"
                        )
                    }
                )
            }

            add(
                "messages",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty(
                                "role",
                                "system"
                            )
                            addProperty(
                                "content",
                                systemPrompt
                            )
                        }
                    )

                    add(
                        JsonObject().apply {
                            addProperty(
                                "role",
                                "user"
                            )

                            if (
                                imageBytes == null
                            ) {
                                addProperty(
                                    "content",
                                    userPrompt
                                )
                            } else {
                                add(
                                    "content",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty(
                                                    "type",
                                                    "text"
                                                )
                                                addProperty(
                                                    "text",
                                                    userPrompt
                                                )
                                            }
                                        )

                                        add(
                                            JsonObject().apply {
                                                addProperty(
                                                    "type",
                                                    "image_url"
                                                )

                                                add(
                                                    "image_url",
                                                    JsonObject().apply {
                                                        addProperty(
                                                            "url",
                                                            "data:" +
                                                                imageMime +
                                                                ";base64," +
                                                                Base64.encodeToString(
                                                                    imageBytes,
                                                                    Base64.NO_WRAP
                                                                )
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    private fun executeJson(
        provider: String,
        payload: JsonObject
    ): String {
        val key =
            settings.apiKeyFor(provider)

        val request = Request.Builder()
            .url(
                settings.endpointFor(
                    provider
                )
            )
            .header(
                "Authorization",
                "Bearer $key"
            )
            .header(
                "Content-Type",
                "application/json"
            )
            .post(
                payload.toString()
                    .toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType()
                    )
            )
            .build()

        client.newCall(request)
            .execute()
            .use { response ->
                val raw =
                    response.body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    error(
                        providerErrorPrefix(
                            provider
                        ) +
                            " HTTP " +
                            response.code +
                            ": " +
                            raw
                                .replace(
                                    key,
                                    "***"
                                )
                                .take(900)
                    )
                }

                return extractNormalText(
                    raw
                )
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: error(
                        providerLabel(
                            provider
                        ) +
                            " 返回成功，但没有找到回复文本。"
                    )
            }
    }

    private fun extractNormalText(
        raw: String
    ): String {
        val root = runCatching {
            JsonParser
                .parseString(raw)
                .asJsonObject
        }.getOrNull()
            ?: return ""

        root.get("output_text")
            ?.takeIf {
                it.isJsonPrimitive
            }
            ?.asString
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                return it
            }

        val choices =
            root.getAsJsonArray(
                "choices"
            )

        if (
            choices != null &&
            choices.size() > 0
        ) {
            val first =
                choices[0].asJsonObject

            val content =
                first
                    .getAsJsonObject(
                        "message"
                    )
                    ?.get("content")

            if (content != null) {
                when {
                    content.isJsonPrimitive ->
                        return content.asString

                    content.isJsonArray -> {
                        val parts =
                            content.asJsonArray
                                .mapNotNull {
                                    item ->
                                    if (
                                        !item.isJsonObject
                                    ) {
                                        null
                                    } else {
                                        item.asJsonObject
                                            .get("text")
                                            ?.takeIf {
                                                it.isJsonPrimitive
                                            }
                                            ?.asString
                                    }
                                }

                        if (
                            parts.isNotEmpty()
                        ) {
                            return parts
                                .joinToString(
                                    "\n"
                                )
                        }
                    }
                }
            }
        }

        return ""
    }

    private suspend fun readSse(
        source: BufferedSource,
        onDelta: suspend (String) -> Unit
    ) {
        while (!source.exhausted()) {
            val line =
                source.readUtf8Line()
                    ?: break

            if (
                !line.startsWith(
                    "data:"
                )
            ) {
                continue
            }

            val data =
                line.removePrefix(
                    "data:"
                ).trim()

            if (data == "[DONE]") {
                break
            }

            if (data.isBlank()) {
                continue
            }

            val json =
                runCatching {
                    JsonParser
                        .parseString(data)
                        .asJsonObject
                }.getOrNull()
                    ?: continue

            val choices =
                json.getAsJsonArray(
                    "choices"
                )

            if (
                choices == null ||
                choices.size() == 0
            ) {
                continue
            }

            val delta =
                choices[0]
                    .asJsonObject
                    .getAsJsonObject(
                        "delta"
                    )
                    ?.get("content")
                    ?: continue

            val text =
                when {
                    delta.isJsonPrimitive ->
                        delta.asString

                    delta.isJsonArray ->
                        delta.asJsonArray
                            .mapNotNull {
                                item ->
                                if (
                                    !item.isJsonObject
                                ) {
                                    null
                                } else {
                                    item.asJsonObject
                                        .get("text")
                                        ?.takeIf {
                                            it.isJsonPrimitive
                                        }
                                        ?.asString
                                }
                            }
                            .joinToString(
                                ""
                            )

                    else -> ""
                }

            if (text.isNotEmpty()) {
                onDelta(text)
            }
        }
    }

    private fun requireConfigured(
        provider: String,
        model: String
    ) {
        require(
            settings.providerConfigured(
                provider
            )
        ) {
            "请先到“我 → 设置 → AI 服务”配置 " +
                providerLabel(provider) +
                " API Key。"
        }

        require(
            model.isNotBlank()
        ) {
            "当前联系人的模型 ID 为空。"
        }
    }

    private fun providerLabel(
        provider: String
    ): String =
        if (
            provider == "deepseek"
        ) {
            "DeepSeek 官方"
        } else {
            "海鲸AI"
        }

    private fun providerErrorPrefix(
        provider: String
    ): String =
        providerLabel(provider) +
            " 请求失败"
}
