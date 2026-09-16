package com.realtek.chat.ai

import android.content.Context
import com.realtek.chat.storage.AppDb
import kotlin.math.max

class RepetitionGuard(
    context: Context,
    private val socialEngine: SocialEngine
) {
    private val db =
        AppDb.get(
            context.applicationContext
        )

    fun isRepetitive(
        contactId: Int,
        candidate: String
    ): Boolean {
        val clean =
            normalize(candidate)

        if (clean.isBlank()) {
            return true
        }

        val topic =
            socialEngine.inferSelfTopic(
                candidate
            )

        // Hard cooldown is only for repeated AI self-state loops.
        // Ordinary shared topics such as food/weather may legitimately continue
        // for several turns and should not be blocked just because the topic repeats.
        if (
            topic in setOf(
                "self_fatigue",
                "self_sleep",
                "boredom"
            ) &&
            socialEngine.topicOnCooldown(
                contactId,
                candidate
            )
        ) {
            return true
        }

        // “嗯 / 啊 / 哈哈”这类短 backchannel 本来就会重复，
        // 不应该因为文本相同被当作人格复读。
        if (
            topic == "general" &&
            clean.length <= 3
        ) {
            return false
        }

        val cutoff =
            System.currentTimeMillis() -
                72L * 3_600_000L

        val recent =
            db.getRecentMessages(
                contactId,
                30
            )
                .filter {
                    it.role == "assistant" &&
                        it.type == "text" &&
                        it.createdAt >= cutoff
                }
                .takeLast(16)

        return recent.any {
            tooSimilar(
                candidate,
                it.text
            )
        }
    }

    fun isSuspiciousPrefix(
        contactId: Int,
        partial: String
    ): Boolean {
        val clean =
            normalize(partial)

        if (clean.length < 4) {
            return false
        }

        val recent =
            db.getRecentMessages(
                contactId,
                20
            )
                .filter {
                    it.role == "assistant" &&
                        it.type == "text"
                }
                .takeLast(12)

        return recent.any {
            val old =
                normalize(it.text)

            old.startsWith(clean) ||
                clean.startsWith(old)
        }
    }

    fun recentAssistantText(
        contactId: Int
    ): String {
        val recent =
            db.getRecentMessages(
                contactId,
                18
            )
                .filter {
                    it.role == "assistant" &&
                        it.type == "text"
                }
                .takeLast(8)

        if (recent.isEmpty()) {
            return "暂无。"
        }

        return recent
            .joinToString("\n") {
                "- ${it.text}"
            }
    }

    private fun tooSimilar(
        a: String,
        b: String
    ): Boolean {
        val x =
            normalize(a)

        val y =
            normalize(b)

        if (
            x.isBlank() ||
            y.isBlank()
        ) {
            return false
        }

        if (
            x == y
        ) {
            return true
        }

        val minLen =
            minOf(
                x.length,
                y.length
            )

        val maxLen =
            max(
                x.length,
                y.length
            )

        if (
            minLen >= 4 &&
            (
                x.contains(y) ||
                    y.contains(x)
                ) &&
            minLen.toDouble() /
                maxLen.toDouble() >=
                0.72
        ) {
            return true
        }

        val gx =
            grams(x)

        val gy =
            grams(y)

        if (
            gx.isEmpty() ||
            gy.isEmpty()
        ) {
            return false
        }

        val intersection =
            gx.intersect(gy).size

        val union =
            gx.union(gy).size

        val jaccard =
            intersection.toDouble() /
                union.toDouble()

        return jaccard >= 0.72
    }

    private fun normalize(
        text: String
    ): String =
        text.lowercase()
            .replace(
                Regex(
                    "[\\p{Punct}\\s，。！？；：“”‘’（）【】…~～]+"
                ),
                ""
            )

    private fun grams(
        text: String
    ): Set<String> {
        if (text.length < 2) {
            return setOf(text)
        }

        return (
            0 until text.length - 1
            ).map {
            text.substring(
                it,
                it + 2
            )
        }.toSet()
    }
}
