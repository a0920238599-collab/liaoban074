package com.realtek.chat.ai

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.realtek.chat.settings.AppSettings
import com.realtek.chat.storage.AppDb
import com.realtek.chat.storage.Contact
import com.realtek.chat.storage.MemoryItem
import kotlin.math.abs

data class ConversationPlan(
    val mode: String,
    val floor: String,
    val maxStages: Int,
    val responseStyle: String,
    val askFollowup: Boolean,
    val pauseMs: Long,
    val selectedMemoryIds: List<Long>,
    val usedSemanticPlanner: Boolean,
    val topicLabel: String = "",
    val phase: String = "exchange"
)

class ConversationPlanner(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDb.get(appContext)
    private val settings = AppSettings(appContext)
    private val gateway = RunApiGateway(appContext)

    suspend fun plan(
        contact: Contact,
        currentText: String,
        fastMode: Boolean
    ): ConversationPlan {
        val local = localPlan(currentText, fastMode)
        val memories = db.getMemories(contact.id, 250)

        if (memories.isEmpty()) return local

        val shouldSemanticRefine =
            needsSemanticMemory(currentText) ||
                currentText.length > 48 ||
                local.mode == "repair" ||
                local.mode == "talk_burst"

        if (!shouldSemanticRefine) {
            val lightweight = lightweightMemorySelection(currentText, memories)
            return local.copy(selectedMemoryIds = lightweight.map { it.id })
        }

        val candidates = memoryCandidates(currentText, memories)
        if (candidates.isEmpty()) return local

        return runCatching {
            semanticRefine(
                contact = contact,
                currentText = currentText,
                local = local,
                candidates = candidates
            )
        }.getOrElse {
            local.copy(
                selectedMemoryIds = lightweightMemorySelection(
                    currentText,
                    candidates
                ).map { it.id }
            )
        }
    }

    suspend fun shouldContinue(
        contact: Contact,
        plan: ConversationPlan,
        completedStages: Int
    ): Boolean {
        if (completedStages >= plan.maxStages) return false
        if (plan.floor == "yield") return false

        // 连续讲话模式仍然每一段重新判断，而不是一次性预生成全部内容。
        val recent = db.getRecentMessages(contact.id, 7)
        val transcript = recent.joinToString("\n") {
            "${if (it.role == "user") "用户" else contact.name}：" +
                if (it.type == "image") "[图片]" else it.text
        }

        val plannerModel = effectivePlannerModel()

        val raw = runCatching {
            gateway.chat(
                provider = settings.systemProvider,
                model = plannerModel,
                systemPrompt = """
                    你是即时聊天的话轮控制器。
                    你的任务不是写聊天内容，只判断联系人现在是否还应该继续占住话轮。
                    真人连续说话时会分成几段，中间稍停；如果已经说够了就应该把话轮让给对方。
                """.trimIndent(),
                userPrompt = """
                    联系人：${contact.name}
                    当前模式：${plan.mode}
                    当前 floor：${plan.floor}
                    最多阶段：${plan.maxStages}
                    已完成阶段：$completedStages
                    最近聊天：
                    $transcript

                    只输出 JSON：
                    {"continue":true}
                    或
                    {"continue":false}
                """.trimIndent(),
                maxTokens = 60
            )
        }.getOrNull() ?: return completedStages < plan.maxStages &&
            plan.mode == "talk_burst"

        return parseJson(raw)
            ?.get("continue")
            ?.takeIf { it.isJsonPrimitive }
            ?.asBoolean
            ?: false
    }

    fun resolveMemories(
        contactId: Int,
        plan: ConversationPlan
    ): List<MemoryItem> {
        if (plan.selectedMemoryIds.isEmpty()) return emptyList()
        val wanted = plan.selectedMemoryIds.toSet()
        return db.getMemories(contactId, 250)
            .filter { it.id in wanted }
            .take(8)
    }

    private fun localPlan(
        text: String,
        fastMode: Boolean
    ): ConversationPlan {
        val clean = text.trim()
        val lower = clean.lowercase()

        val talkBurstSignals = listOf(
            "你继续说", "继续说", "你多说点", "多说点",
            "你说一会", "你说会儿", "你来讲", "你讲讲",
            "讲给我听", "一直说", "你先说", "你说吧",
            "再说点", "继续讲", "接着说",
            "讲个故事", "讲个事", "跟我说说", "陪我说会儿",
            "陪我聊会儿", "我想听你说", "你随便说", "多聊会儿",
            "你说点啥", "你多讲一会"
        )

        val repairSignals = listOf(
            "不是这个意思", "你理解错", "你搞错", "不是这样",
            "我不是说", "你没懂", "不是啊"
        )

        val emotionSignals = listOf(
            "气死", "烦死", "烦", "累死", "好累", "难受",
            "离谱", "笑死", "开心", "崩溃", "无语", "郁闷"
        )

        val questionSignals = listOf(
            "为什么", "怎么", "怎么办", "什么", "哪种",
            "哪个", "多少", "能不能", "可以吗", "是不是"
        )

        val mode: String
        val floor: String
        val stages: Int
        val style: String
        val ask: Boolean
        val pause: Long

        when {
            talkBurstSignals.any { clean.contains(it) } -> {
                mode = "talk_burst"
                floor = "hold"
                stages = 5
                style = "连续讲一小段时间；每一段都是独立聊天消息，不要写成长文章。"
                ask = false
                pause = 900L
            }

            repairSignals.any { clean.contains(it) } -> {
                mode = "repair"
                floor = "yield"
                stages = 1
                style = "先承认理解偏差并重新对齐，不辩解，不重复大段解释。"
                ask = true
                pause = 650L
            }

            clean.length <= 6 && !clean.contains("?") && !clean.contains("？") -> {
                mode = "backchannel"
                floor = "yield"
                stages = 1
                style = "像真人即时聊天的短回应；通常一句就够。"
                ask = false
                pause = 450L
            }

            emotionSignals.any { clean.contains(it) } -> {
                mode = "react"
                floor = if (clean.length > 28) "soft_hold" else "yield"
                stages = if (clean.length > 28) 2 else 1
                style = "先接住情绪或事件本身；不要立刻分析，不强制追问。"
                ask = clean.length > 12
                pause = 750L
            }

            clean.contains("?") ||
                clean.contains("？") ||
                questionSignals.any { clean.contains(it) } -> {
                mode = "answer"
                floor = if (clean.length > 80) "soft_hold" else "yield"
                stages = if (clean.length > 80) 2 else 1
                style = if (fastMode)
                    "直接、快速回答，不铺垫。"
                else
                    "先给核心回答；复杂时允许第二段补充。"
                ask = false
                pause = 850L
            }

            clean.length > 120 -> {
                mode = "listen_then_respond"
                floor = "soft_hold"
                stages = 2
                style = "先回应对方真正想表达的内容，再补一段有价值的反应。"
                ask = false
                pause = 900L
            }

            else -> {
                mode = "casual"
                floor = "yield"
                stages = 1
                style = "自然回应，不把每句话变成问题，不为了延长聊天而硬追问。"
                ask = false
                pause = 650L
            }
        }

        return ConversationPlan(
            mode = mode,
            floor = floor,
            maxStages = stages,
            responseStyle = style,
            askFollowup = ask,
            pauseMs = pause,
            selectedMemoryIds = emptyList(),
            usedSemanticPlanner = false,
            topicLabel = inferLocalTopic(clean),
            phase = phaseForMode(mode)
        )
    }

    private fun needsSemanticMemory(text: String): Boolean {
        val cues = listOf(
            "上次", "之前", "那个", "那件事", "后来", "结果",
            "终于", "又", "还是", "还记得", "你记得", "明天",
            "昨天", "今天", "这次", "那个人", "他", "她", "它",
            "当时", "之前说", "后面", "后来呢"
        )
        return cues.any { text.contains(it) }
    }

    private fun memoryCandidates(
        text: String,
        memories: List<MemoryItem>
    ): List<MemoryItem> {
        val now = System.currentTimeMillis()
        val picked = LinkedHashMap<Long, MemoryItem>()

        fun add(items: List<MemoryItem>) {
            for (m in items) {
                if (picked.size >= 40) break
                picked.putIfAbsent(m.id, m)
            }
        }

        // 重要记忆
        add(memories.sortedByDescending { it.importance }.take(12))

        // 最近记忆
        add(memories.sortedByDescending { it.createdAt }.take(12))

        // 未完成话题优先
        add(
            memories.filter { it.type == "open_loop" }
                .sortedByDescending { it.createdAt }
                .take(8)
        )

        // 临近事件优先
        add(
            memories.filter { m ->
                m.dueAt?.let {
                    abs(it - now) <= 7L * 24L * 3_600_000L
                } == true
            }
                .sortedBy { abs((it.dueAt ?: now) - now) }
                .take(8)
        )

        // 文本上有线索的也纳入候选，之后由模型做真正语义重排
        add(lightweightMemorySelection(text, memories).take(12))

        return picked.values.toList()
    }

    private fun lightweightMemorySelection(
        query: String,
        memories: List<MemoryItem>
    ): List<MemoryItem> {
        val queryTokens = tokenSet(query)
        val now = System.currentTimeMillis()

        return memories.sortedByDescending { m ->
            val memoryTokens = tokenSet(m.content + " " + m.tags)
            val overlap = queryTokens.intersect(memoryTokens).size.toDouble()
            val recentDays = (now - m.createdAt).coerceAtLeast(0) / 86_400_000.0
            val recency = 1.0 / (1.0 + recentDays / 30.0)
            val dueBoost = m.dueAt?.let {
                if (abs(it - now) <= 72 * 3_600_000L) 3.0 else 0.0
            } ?: 0.0

            overlap * 2.5 +
                m.importance * 1.5 +
                recency +
                dueBoost +
                if (m.type == "open_loop") 1.8 else 0.0
        }
    }

    private suspend fun semanticRefine(
        contact: Contact,
        currentText: String,
        local: ConversationPlan,
        candidates: List<MemoryItem>
    ): ConversationPlan {
        val recent = db.getRecentMessages(contact.id, 8)
        val transcript = recent.joinToString("\n") {
            "${if (it.role == "user") "用户" else contact.name}：" +
                if (it.type == "image") "[图片]" else it.text
        }

        val memoryList = candidates.joinToString("\n") {
            "[${it.id}] (${it.type}) ${it.content}" +
                if (it.tags.isNotBlank()) " | 关联词:${it.tags}" else ""
        }

        val recentSelfTopics = db.getRecentSelfTopics(
            contact.id,
            12
        )
            .map { it.topic }
            .distinct()
            .take(8)
            .joinToString("、")
            .ifBlank { "暂无" }

        val raw = gateway.chat(
            provider = settings.systemProvider,
            model = effectivePlannerModel(),
            systemPrompt = """
                你是聊天系统的 Conversation Planner。
                你不负责写最终聊天内容，只负责：
                1. 判断当前这一轮怎么聊；
                2. 从候选长期记忆中按“意义”而不是表面关键词选出真正相关的记忆；
                3. 决定是否需要占住话轮连续说几段。

                注意：即使用户说法和记忆里的词完全不同，只要语义上是在指同一件事，也应该选中。
            """.trimIndent(),
            userPrompt = """
                联系人：${contact.name}
                本地初步模式：${local.mode}
                本地初步 floor：${local.floor}
                本地初步阶段数：${local.maxStages}

                最近聊天：
                $transcript

                当前消息：
                $currentText

                候选记忆：
                $memoryList

                联系人最近自己已经聊过的主题：
                $recentSelfTopics

                只输出 JSON：
                {
                  "mode":"casual/react/answer/repair/talk_burst/listen_then_respond/backchannel",
                  "floor":"yield/soft_hold/hold",
                  "max_stages":1,
                  "response_style":"一句简短指令",
                  "ask_followup":false,
                  "pause_ms":800,
                  "selected_memory_ids":[1,2],
                  "topic_label":"当前真正话题的简短标签",
                  "phase":"opening/listening/storytelling/reacting/answering/repairing/closing"
                }

                约束：
                - selected_memory_ids 最多 8 条；
                - max_stages 普通对话 1~2；
                - 只有讲故事、连续说明、用户明确让你继续说时才可 3~6；
                - 不要为了显得主动而强制追问。
            """.trimIndent(),
            maxTokens = 220
        )

        val obj = parseJson(raw) ?: return local

        val ids = obj.getAsJsonArray("selected_memory_ids")
            ?.mapNotNull {
                runCatching { it.asLong }.getOrNull()
            }
            ?.filter { id -> candidates.any { it.id == id } }
            ?.distinct()
            ?.take(8)
            .orEmpty()

        val mode = obj["mode"]
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?: local.mode

        val floor = obj["floor"]
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it in setOf("yield", "soft_hold", "hold") }
            ?: local.floor

        val maxStages = obj["max_stages"]
            ?.takeIf { it.isJsonPrimitive }
            ?.asInt
            ?.coerceIn(1, 6)
            ?: local.maxStages

        return local.copy(
            mode = mode,
            floor = floor,
            maxStages = maxStages,
            responseStyle = obj["response_style"]
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: local.responseStyle,
            askFollowup = obj["ask_followup"]
                ?.takeIf { it.isJsonPrimitive }
                ?.asBoolean
                ?: local.askFollowup,
            pauseMs = obj["pause_ms"]
                ?.takeIf { it.isJsonPrimitive }
                ?.asLong
                ?.coerceIn(350L, 2500L)
                ?: local.pauseMs,
            selectedMemoryIds = ids,
            usedSemanticPlanner = true,
            topicLabel = obj["topic_label"]
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: local.topicLabel,
            phase = obj["phase"]
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: local.phase
        )
    }

    private fun inferLocalTopic(text: String): String {
        val clean = text.trim()

        return when {
            clean.contains("老师") -> "老师相关"
            clean.contains("同事") -> "同事相关"
            clean.contains("考试") -> "考试"
            clean.contains("面试") -> "面试"
            clean.contains("答辩") -> "答辩"
            clean.contains("工作") -> "工作"
            clean.contains("学校") || clean.contains("上课") -> "学校/课程"
            clean.contains("游戏") -> "游戏"
            clean.contains("电影") || clean.contains("剧") -> "影视"
            clean.contains("吃") || clean.contains("饭") -> "吃饭"
            clean.contains("睡") || clean.contains("困") -> "睡眠"
            clean.contains("累") -> "疲惫"
            clean.length <= 10 -> clean
            else -> clean.take(18)
        }
    }

    private fun phaseForMode(mode: String): String =
        when (mode) {
            "repair" -> "repairing"
            "answer" -> "answering"
            "react" -> "reacting"
            "talk_burst" -> "storytelling"
            "listen_then_respond" -> "listening"
            "backchannel" -> "listening"
            else -> "exchange"
        }

    private fun effectivePlannerModel(): String =
        settings.plannerModel.ifBlank {
            settings.fastChatModel.ifBlank {
                settings.defaultModelFor(
                    settings.systemProvider
                )
            }
        }

    private fun tokenSet(text: String): Set<String> {
        val clean = text.lowercase()
            .replace(Regex("[\\p{Punct}\\s，。！？；：“”‘’（）【】]+"), "")

        val grams = if (clean.length >= 2) {
            (0 until clean.length - 1).map {
                clean.substring(it, it + 2)
            }
        } else {
            listOf(clean)
        }

        return grams.filter { it.isNotBlank() }.toSet()
    }

    private fun parseJson(raw: String): JsonObject? {
        val clean = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        runCatching {
            JsonParser.parseString(clean).asJsonObject
        }.getOrNull()?.let { return it }

        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return runCatching {
                JsonParser.parseString(
                    clean.substring(start, end + 1)
                ).asJsonObject
            }.getOrNull()
        }

        return null
    }
}
