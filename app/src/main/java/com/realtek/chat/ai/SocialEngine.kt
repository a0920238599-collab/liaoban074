package com.realtek.chat.ai

import android.content.Context
import com.realtek.chat.storage.AppDb
import com.realtek.chat.storage.Contact
import com.realtek.chat.storage.SocialState
import kotlin.math.max
import kotlin.math.min

class SocialEngine(context: Context) {
    private val db = AppDb.get(
        context.applicationContext
    )

    fun prepareState(
        contact: Contact,
        userText: String
    ): SocialState {
        val now = System.currentTimeMillis()
        val old = db.getSocialState(contact.id)
            ?: defaultState(contact.id, now)

        val elapsedHours =
            ((now - old.updatedAt)
                .coerceAtLeast(0L))
                .toDouble() / 3_600_000.0

        val userCount =
            db.userMessageCount(contact.id)

        val familiarity =
            min(
                1.0,
                0.08 +
                    userCount / 90.0
            )

        val memoryCount =
            db.getMemories(
                contact.id,
                80
            ).size

        val trust =
            min(
                0.95,
                0.12 +
                    userCount / 140.0 +
                    min(
                        0.18,
                        memoryCount / 220.0
                    )
            )

        val humorComfort =
            min(
                0.90,
                0.10 +
                    userCount / 150.0
            )

        val mood =
            inferConversationMood(
                userText,
                old.mood
            )

        val recoveredEnergy =
            min(
                0.85,
                old.conversationEnergy +
                    min(
                        0.22,
                        elapsedHours * 0.035
                    )
            )

        val conversationEnergy =
            when (mood) {
                "playful" ->
                    min(
                        0.95,
                        recoveredEnergy + 0.08
                    )

                "attentive" ->
                    max(
                        0.42,
                        recoveredEnergy - 0.03
                    )

                "quiet" ->
                    max(
                        0.30,
                        recoveredEnergy - 0.08
                    )

                else ->
                    recoveredEnergy
            }

        val socialDrive =
            min(
                0.92,
                0.28 +
                    familiarity * 0.34 +
                    humorComfort * 0.16
            )

        val state = old.copy(
            mood = mood,
            conversationEnergy =
                conversationEnergy,
            socialDrive = socialDrive,
            familiarity = familiarity,
            trust = trust,
            humorComfort = humorComfort,
            updatedAt = now
        )

        db.upsertSocialState(state)
        return state
    }

    fun applyPlan(
        state: SocialState,
        plan: ConversationPlan
    ): SocialState {
        val updated =
            state.copy(
                currentTopic =
                    plan.topicLabel
                        .ifBlank {
                            state.currentTopic
                        },
                phase =
                    plan.phase
                        .ifBlank {
                            state.phase
                        },
                conversationEnergy =
                    (
                        state.conversationEnergy -
                            when (
                                plan.floor
                            ) {
                                "hold" -> 0.05
                                "soft_hold" -> 0.025
                                else -> 0.01
                            }
                        ).coerceIn(
                        0.20,
                        0.95
                    ),
                updatedAt =
                    System.currentTimeMillis()
            )

        db.upsertSocialState(updated)
        return updated
    }

    fun afterAssistantMessage(
        contactId: Int,
        text: String
    ) {
        val topic =
            inferSelfTopic(text)

        if (
            topic != "general" &&
            topic.isNotBlank()
        ) {
            db.addSelfTopic(
                contactId,
                topic
            )
        }
    }

    fun snapshot(
        contactId: Int
    ): SocialState =
        db.getSocialState(contactId)
            ?: defaultState(
                contactId,
                System.currentTimeMillis()
            )

    fun recentTopicSummary(
        contactId: Int
    ): String {
        val topics =
            db.getRecentSelfTopics(
                contactId,
                14
            )

        if (topics.isEmpty()) {
            return "暂无近期自我话题冷却。"
        }

        val now =
            System.currentTimeMillis()

        return topics
            .groupBy {
                it.topic
            }
            .map { (topic, items) ->
                val latest =
                    items.maxOf {
                        it.createdAt
                    }

                val hours =
                    (
                        now - latest
                        ).coerceAtLeast(0L)
                        .toDouble() /
                        3_600_000.0

                "$topic（${"%.1f".format(hours)}小时前）"
            }
            .take(8)
            .joinToString("；")
    }

    fun topicOnCooldown(
        contactId: Int,
        candidate: String
    ): Boolean {
        val topic =
            inferSelfTopic(candidate)

        if (
            topic == "general" ||
            topic.isBlank()
        ) {
            return false
        }

        val cooldownHours =
            when (topic) {
                "self_fatigue" -> 10.0
                "self_sleep" -> 8.0
                "weather" -> 6.0
                "boredom" -> 6.0
                "food" -> 4.0
                else -> 3.0
            }

        val now =
            System.currentTimeMillis()

        return db.getRecentSelfTopics(
            contactId,
            30
        ).any {
            it.topic == topic &&
                (
                    now - it.createdAt
                    ).coerceAtLeast(0L)
                    .toDouble() /
                    3_600_000.0 <
                    cooldownHours
        }
    }

    fun inferSelfTopic(
        text: String
    ): String {
        val clean =
            text.trim()

        val fatigueWords = listOf(
            "好累",
            "累死",
            "累了",
            "疲惫",
            "没精神",
            "没力气"
        )

        val sleepWords = listOf(
            "困死",
            "好困",
            "想睡",
            "睡觉",
            "没睡醒"
        )

        val likelyAboutUser =
            clean.contains("你") &&
                !clean.contains("我")

        return when {
            !likelyAboutUser &&
                fatigueWords.any {
                    clean.contains(it)
                } -> "self_fatigue"

            !likelyAboutUser &&
                sleepWords.any {
                    clean.contains(it)
                } -> "self_sleep"

            listOf(
                "下雨",
                "天气",
                "好冷",
                "好热",
                "太阳"
            ).any {
                clean.contains(it)
            } -> "weather"

            listOf(
                "无聊",
                "没事干",
                "闲着"
            ).any {
                clean.contains(it)
            } -> "boredom"

            listOf(
                "饿",
                "吃饭",
                "吃啥",
                "好吃"
            ).any {
                clean.contains(it)
            } -> "food"

            else -> "general"
        }
    }

    private fun inferConversationMood(
        text: String,
        oldMood: String
    ): String {
        val clean =
            text.trim()

        return when {
            listOf(
                "哈哈",
                "笑死",
                "好玩",
                "逗",
                "离谱"
            ).any {
                clean.contains(it)
            } -> "playful"

            listOf(
                "难受",
                "烦",
                "气死",
                "崩溃",
                "委屈",
                "压力"
            ).any {
                clean.contains(it)
            } -> "attentive"

            clean.length <= 3 ->
                "quiet"

            clean.contains("？") ||
                clean.contains("?") ->
                "curious"

            oldMood.isBlank() ->
                "calm"

            else ->
                oldMood
        }
    }

    private fun defaultState(
        contactId: Int,
        now: Long
    ): SocialState =
        SocialState(
            contactId = contactId,
            mood = "calm",
            conversationEnergy = 0.68,
            socialDrive = 0.42,
            currentTopic = "",
            phase = "opening",
            familiarity = 0.08,
            trust = 0.12,
            humorComfort = 0.10,
            updatedAt = now
        )
}
