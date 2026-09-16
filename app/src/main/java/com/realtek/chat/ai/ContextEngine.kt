package com.realtek.chat.ai

import android.content.Context
import com.realtek.chat.storage.AppDb
import com.realtek.chat.storage.Contact
import com.realtek.chat.storage.MemoryItem

data class PromptBundle(
    val system: String,
    val user: String
)

class ContextEngine(context: Context) {
    private val db = AppDb.get(context.applicationContext)

    fun build(
        contact: Contact,
        currentText: String,
        fastMode: Boolean,
        plan: ConversationPlan,
        selectedMemories: List<MemoryItem>,
        socialState: com.realtek.chat.storage.SocialState,
        recentSelfTopics: String,
        recentAssistantText: String,
        stageIndex: Int,
        previousStageText: String?
    ): PromptBundle {
        val recentAll = db.getRecentMessages(
            contact.id,
            if (fastMode) 8 else 14
        )

        // 当前消息已经先写入数据库，因此从“最近聊天”中移除它，
        // 避免同一句同时出现在历史和【最新消息】里。
        val recent = if (
            recentAll.isNotEmpty() &&
            recentAll.last().role == "user" &&
            (
                recentAll.last().text == currentText ||
                    (
                        recentAll.last().type == "image" &&
                            currentText.contains("图片")
                        )
                )
        ) {
            recentAll.dropLast(1)
        } else {
            recentAll
        }.takeLast(if (fastMode) 6 else 12)

        val summary = db.getSummary(contact.id)?.summary.orEmpty()

        val transcript = recent.joinToString("\n") {
            "${if (it.role == "user") "用户" else contact.name}：" +
                if (it.type == "image") "[图片]" else it.text
        }

        val relation = when (db.userMessageCount(contact.id)) {
            in 0..7 -> "刚认识"
            in 8..29 -> "逐渐熟悉"
            in 30..99 -> "比较熟悉"
            else -> "已经很熟"
        }

        val memoryText = selectedMemories
            .joinToString("\n") {
                "- (${it.type}) ${it.content}"
            }
            .ifBlank { "暂无" }

        val stageInstruction = when {
            stageIndex == 0 -> """
                这是这一轮的第一段发言。
                直接说这一段真正该说的话，不要解释你为什么这样回复。
            """.trimIndent()

            plan.mode == "talk_burst" -> """
                这是连续讲话的第 ${stageIndex + 1} 段。
                前一段你已经说过：
                ${previousStageText.orEmpty()}

                不要重复前一段。像真人连续发消息一样自然接着说。
                每一段只推进一点内容，不要一次把剩下所有话说完。
            """.trimIndent()

            else -> """
                这是同一轮聊天的第 ${stageIndex + 1} 段。
                前一段你已经说过：
                ${previousStageText.orEmpty()}

                只有真正还有自然要补充的内容时才继续。
                不要重复，不要把一条完整答案机械切碎。
            """.trimIndent()
        }

        val system = """
            你正在作为联系人“${contact.name}”与用户进行即时聊天。

            【固定核心性格】
            ${contact.corePersona}

            【固定说话方式】
            ${contact.styleRules}

            【当前关系状态】
            $relation

            【Conversation Planner 决策】
            mode = ${plan.mode}
            floor = ${plan.floor}
            response_style = ${plan.responseStyle}
            ask_followup = ${plan.askFollowup}
            topic = ${plan.topicLabel}
            phase = ${plan.phase}

            【持续社交状态】
            mood = ${socialState.mood}
            conversation_energy = ${"%.2f".format(socialState.conversationEnergy)}
            social_drive = ${"%.2f".format(socialState.socialDrive)}
            familiarity = ${"%.2f".format(socialState.familiarity)}
            trust = ${"%.2f".format(socialState.trust)}
            humor_comfort = ${"%.2f".format(socialState.humorComfort)}
            current_topic = ${socialState.currentTopic.ifBlank { "暂无" }}
            conversation_phase = ${socialState.phase}

            这些是“聊天行为状态”，不是现实身体状态。
            不要因为 energy 低就编造“我好累、我困、我刚下班”等现实身体或线下经历。

            【最近已经聊过的自我主题/冷却】
            $recentSelfTopics

            【最近你自己已经说过的话】
            $recentAssistantText

            【近期摘要】
            ${summary.ifBlank { "暂无" }}

            【这一轮语义上真正相关的长期记忆】
            $memoryText

            交流规则：
            1. 不要把每句话都当成需要解决的问题。
            2. 不强制追问；很多时候一句反应、一个调侃、一个确认就够了。
            3. 如果 planner 让你 hold floor，可以连续说几段；如果 yield，就尽快把话轮交回用户。
            4. 不使用客服式铺垫，不写“以下几点”，除非用户明确要求详细结构化回答。
            5. 根据用户的长度和语气调整自己的长度：用户很短时通常也短，用户认真展开时再适当展开。
            6. 如果用户在纠正你，优先修复理解，不为自己辩护。
            7. 不要重复最近自己已经说过的状态、开场或观点；尤其不要反复说“好累、好困、无聊”等同一自我状态。
            8. 如果某个自我主题正在冷却，除非出现明显的新发展，否则换一个话题，或者不说。
            9. 不要为了显得像真人而虚构现实身体、所在地、工作、上学、吃饭、睡觉等线下经历。
            10. 联系人资料明确是 AI；如果用户直接问你是不是 AI，必须如实回答。自然感来自交流方式，不靠冒充真人身份。
            11. ${if (fastMode) "这是快速闲聊路径：优先极快、极短、自然。" else "正常质量路径：保持自然同时保证内容质量。"}
        """.trimIndent()

        val user = """
            【最近聊天】
            ${transcript.ifBlank { "暂无" }}

            【最新消息】
            $currentText

            【当前阶段】
            ${stageIndex + 1}/${plan.maxStages}

            $stageInstruction

            只输出“这一条聊天消息本身”。
            不要 JSON，不要 Markdown 代码块，不要标注角色，不要解释内部决策。
        """.trimIndent()

        return PromptBundle(system, user)
    }
}
