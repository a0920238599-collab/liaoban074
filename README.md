# Realtek v7.4 — 几秒级自主续聊版

这是 Realtek v3.1 修正版。已修复 Common.kt 中 KeyboardOptions 缺少正确 import 导致的 Kotlin 编译失败。

## v3 新增
- 软件名：Realtek
- 首次启动强制设置 4 位打开密码
- App 任务/进程重新启动后重新验证
- 微信式消息 / 通讯录 / 发现 / 我 四栏布局
- 联系人可新增、删除
- AI 自动完善联系人性格和说话方式
- 联系人技术设置藏到资料页右上角 `···`
- 用户头像
- 联系人头像
- 每个联系人独立聊天背景
- 可发送聊天图片
- 头像、背景、聊天图片复制进 App 私有目录
- 图片使用 Android Keystore AES-GCM 加密
- 聊天文字、长期记忆、人物设定、摘要在 SQLite 中以密文保存
- 禁止 Android 云备份/设备迁移备份
- debug APK 也设置为不可调试
- 卸载 App 后由 Android 删除全部 App 私有数据

## 本地数据
数据库：
`/data/user/0/com.realtek.chat/databases/realtek_v3.db`

加密媒体：
`/data/user/0/com.realtek.chat/files/secure_media/`

普通文件管理器和电脑 USB 文件浏览无法直接读取。
数据库中的聊天文本不是明文；图片文件也不是普通 jpg/png，而是加密 `.bin`。

> Root/系统级取证属于更高权限边界，不承诺对已被完全控制的设备提供绝对保护。

## 卸载
本项目不做云同步，并关闭 Android Backup。
卸载 `com.realtek.chat` 后，聊天、联系人、记忆、头像、图片、背景、RunAPI Key 和打开密码都会随 App 私有数据删除。

## RunAPI
入口：
`我 → 设置 → AI 服务`

默认接口：
`https://runapi.co/v1/chat/completions`

默认模型：
`deepseek-v4-pro`

G 默认：
`grok-4.6`

## 从 GitHub 生成 APK
1. 新建空 GitHub 仓库。
2. 上传本项目根目录“里面的内容”。
3. Code 首页直接看到 `.github`、`app`、`build.gradle.kts`、`settings.gradle.kts`。
4. 打开 Actions。
5. 选择 `Build Realtek APK`。
6. Run workflow。
7. 成功后在 Artifacts 下载 `Realtek-v3-APK`。
8. 解压得到 `app-debug.apk`。

v3 的 applicationId 是 `com.realtek.chat`。
建议先卸载旧版，再装 v3。


## v3.2 修复
- 移除 AndroidManifest.xml 中硬编码的 `android:debuggable="false"`。
- debug 是否可调试继续由 `build.gradle.kts` 控制，避免 `HardcodedDebugMode` Lint 致命错误。


## v4 聊天体验变化

### 主动聊天改成两层
1. **对话惯性**：一轮回复结束后，如果用户没有继续说话，45~135 秒后会进行一次本地后台检查。模型判断话题是否还有自然余韵；有必要时联系人会自己再补一句。用户在等待期间一旦发新消息，本次跟进自动取消。
2. **隔段时间主动联系**：熟悉联系人不再要求 8 小时静默 + 18 小时主动间隔。根据熟悉程度，约静默 2.5~6 小时后进入候选；非事件型主动联系约每 3 小时得到一次判断机会，真正发不发仍由人物模型决定。

Android WorkManager 受系统省电影响，后台时间不是秒级精确。

### 对话连贯性
- 修复“当前用户消息既在最近聊天里出现、又作为最新消息重复出现”的问题。
- 系统提示明确允许“不追问”“只反应一句”“自然停住”，减少每轮都像问答助手。
- 多条消息的间隔改由 App 根据文字长度计算，不再完全依赖模型随手生成 delay_ms。

### 顶部按钮
- 返回按钮扩大到 56dp 触控区。
- 右上角 `···`、`+` 等扩大到约 64×58dp。
- `保存/完成` 最小宽度 72dp、高度 56dp，字号增大。


## v5 新增

### 休眠模式
路径：
`我 → 设置 → 休眠模式`

开启后：
- 手动聊天正常回复
- 刚聊完后的主动补一句关闭
- 几小时后的主动聊天关闭
- 事件主动消息关闭

关闭后恢复主动聊天调度。

### 快速闲聊
短消息且没有“分析/解释/方案/代码”等复杂意图时进入快速路径：
- 最近上下文从最多 12 条缩到最多 6 条
- 长期记忆从最多 7 条缩到最多 3 条
- 最大生成长度限制为 220 tokens
- 可以在 `我 → 设置 → AI 服务 → 快速聊天模型` 单独填写一个更快的 RunAPI 模型 ID
- 留空则继续使用联系人的模型

复杂问题仍走完整上下文，避免为了快牺牲质量。

### 清理后台后的主动消息
Realtek 的主动消息通过 Android WorkManager 注册给系统调度。
普通把 App 从最近任务划掉后，系统仍有机会重新启动相关后台组件并发出通知。
但如果在 Android 系统设置里执行“强行停止”，系统会阻止应用继续后台运行，直到用户再次手动打开。

纯本地方案受 Android Doze、厂商省电策略影响，无法达到微信/FCM 服务端推送那种绝对实时性。


## v6 对话内核

v6 专门解决此前剩下的四个聊天问题。

### 1. Conversation Planner
每一轮不再直接把用户消息扔给人物模型。

先经过 ConversationPlanner，得到：
- mode：闲聊 / 情绪反应 / 回答 / 修复理解 / 连续讲话 / 倾听等
- floor：是否立即让出话轮、轻度占住话轮、持续占住话轮
- maxStages：这一轮最多说几段
- responseStyle：这一轮的具体交流方式
- askFollowup：是否适合追问
- selectedMemoryIds：这轮真正相关的长期记忆

明确的简单闲聊使用本地 Planner，几乎没有额外网络延迟；
涉及“上次、之前、那个、后来、终于、明天、结果”等语义指代，
或需要复杂规划时，再调用对话规划模型做语义重排。

### 2. 流式输出
普通文字聊天优先使用 OpenAI-compatible SSE：
`stream=true`

第一批 token 到达后，聊天气泡立即开始显示，不再必须等完整答案返回。

如果 RunAPI 某个通道不支持流式：
- 自动识别非 SSE 返回；
- 或遇到常见 stream 参数错误；
- 自动退回普通 Chat Completions；
- 不会因此让聊天完全失效。

### 3. 真正多阶段聊天
不再是“一次生成 3 条，再假装分开发”。

现在每个 stage 都是独立生成：
1. 第一段真正生成并流式显示；
2. 稍停；
3. 检查用户有没有插话；
4. Conversation Planner 重新判断是否还应该继续占住话轮；
5. 只有需要时才生成下一段。

用户中途一发消息，旧话轮后续阶段立即终止。

### 4. 连续讲话 / hold floor
用户说：
- “你继续说”
- “你说一会儿”
- “讲个故事”
- “我想听你说”
- “陪我聊会儿”
- “你多说点”

会进入 `talk_burst`。

这时联系人可以连续说 3~5 段，每段都是单独生成的聊天消息，
中间自然停一下，而且每次继续前都会检查用户是否插话。

这模拟真人“拿住话轮讲一会儿”，而不是一次性提前写完全部内容。

### 5. 语义记忆
v6 不再只靠关键词命中最终记忆。

流程变成：
- 本地先从长期记忆中组成候选池：
  - 高重要度
  - 最近记忆
  - 临近事件
  - open_loop 未完成话题
  - 有表面文字线索的记忆
- 最多约 40 条候选进入语义 Planner
- Planner 根据“意义”选择最多 8 条真正相关记忆

例如：
“明天终于轮到我了”
可以语义关联：
“用户周五进行论文答辩”

即使“轮到我”和“论文答辩”没有直接关键词重合。

### 6. 未完成话题
记忆整理增加：
`type = open_loop`

用于：
- “晚点告诉你结果”
- “明天出成绩”
- “这个事等会再说”
- 等待后续结果的问题

Planner 会优先把 open_loop 放入语义候选池。

### 7. 更接近人类轮流交流
v6 增加了这些规则：
- 不强制每轮追问
- 短消息优先短回应
- 用户展开时再展开
- 理解错时进入 repair，而不是辩解
- AI 可以 yield 让出话轮
- 也可以 hold floor 连续说一会
- 用户随时可以插话，中断 AI 后续阶段

## AI 服务里的新选项
`我 → 设置 → AI 服务 → 对话规划模型`

建议使用响应快、成本低的模型。
留空时依次使用：
1. 快速聊天模型
2. 默认聊天模型
3. 当前联系人模型


## v6.1：DeepSeek 官方直连

v6.1 在保留 RunAPI 的同时，增加 DeepSeek 官方 API 直连。

### AI 服务
路径：
`我 → 设置 → AI 服务`

现在可分别保存：
- RunAPI 接口与 API Key
- DeepSeek 官方 API Key

DeepSeek 官方 Chat Completions：
`https://api.deepseek.com/chat/completions`

DeepSeek 官方默认模型：
`deepseek-flash`

### 每个联系人单独选线路
路径：
`联系人资料 → ··· → 更多设置 → API 接入`

可选：
- `RunAPI`
- `DeepSeek 官方`

所以可以：
- 小夏 → RunAPI / GPT 或其他模型
- 阿深 → DeepSeek 官方 / deepseek-flash
- G → RunAPI / Grok

人格、聊天记录、长期记忆与联系人 API 提供商相互独立。

### 后台模型
对话规划、记忆整理、主动聊天有单独的“后台任务提供商”：
- RunAPI
- DeepSeek 官方

如果选择 DeepSeek 官方，建议相关模型填：
`deepseek-flash`

### 数据升级
数据库从 version 1 升级到 version 2。
旧联系人自动标记为 `runapi`，原聊天和记忆不需要因为升级而删除。


## v7：Social Engine

v7 的重点不是增加普通功能，而是让联系人具有更连续的“社交行为”。

### 1. Persistent Social State
每个联系人本地保存：
- mood：当前聊天气氛
- conversation_energy：聊天活跃度
- social_drive：主动交流倾向
- current_topic：当前话题
- phase：对话阶段
- familiarity：熟悉度
- trust：交流信任度
- humor_comfort：玩笑接受度

这些值只用于控制聊天行为，不代表真实人的身体或现实生活状态。

### 2. Conversation State
Conversation Planner 现在额外维护：
- topic_label
- phase

所以系统能区分：
- 正在听用户讲故事
- 正在回应情绪
- 正在回答问题
- 正在修复误解
- 正在连续讲话
- 正在自然收尾

避免每一句都重新像“第一句话”一样处理。

### 3. AI Self History
新增 `ai_topic_history`：
记录联系人最近自己聊过的主题。

例如最近已经出现：
- self_fatigue
- self_sleep
- boredom

这些主题进入冷却，避免 AI 反复自己说：
“好累”
“好困”
“无聊”

### 4. Self-Repetition Guard
每条 AI 消息发送前会检查：
- 是否和最近 72 小时内的 AI 消息高度相似
- 是否属于正在冷却的自我状态主题
- 是否是上一轮已经说过内容的近似改写

如果重复：
1. 不直接发送；
2. 重新生成一次；
3. 第二次仍重复时，宁可不发，也不重复刷同一句。

“嗯 / 啊 / 哈哈”这类真人聊天常见的短 backchannel 不会因为文本相同被强行拦截。

### 5. Streaming Repetition Protection
流式回复不会一拿到第一个字符就立刻显示。
前几个字符先做一个很短的本地缓冲：
- “好累”
- “好困”
这类短重复可以在用户看到前被拦截。

正常且不重复的回复仍继续流式显示。

### 6. Topic Novelty
普通聊天允许一个话题连续聊几轮；
但主动聊天会优先避开最近已经主动聊过的自我状态和开场方式。

没有新东西可说时：
`send=false`

而不是为了完成“主动聊天任务”硬发一句。

### 7. Socially-Aware Proactive Timing
主动聊天不再只看固定小时数。

现在会同时考虑：
- familiarity
- social_drive
- open_loop 未完成话题
- 临近事件
- 最近一次主动消息

熟悉且互动意愿较高的联系人可以更自然地早点联系；
陌生或当前社交驱动力低时会更克制。

### 8. Memory Separation
长期记忆整理器明确区分：
- 用户事实
- 共同经历
- 未完成话题
- AI 联系人临时自述

AI 自己说“我累了/我困了/我无聊”不会再被错误写成长期记忆，
避免形成“摘要里有累 → 下次又说累 → 再写进摘要”的自我强化循环。

### 9. Identity Transparency
聊天方式会尽量自然，但联系人资料仍明确是 AI。
用户直接问身份时必须如实回答。
自然感来自交流连续性，不靠冒充真实人类或编造线下经历。


## v7.1 compile fix

修复 `RunApiGateway.streamChat()` 中 SSE 分支的 Kotlin 标签返回问题。

此前在 `call.execute().use { ... }` 内使用 `return@withContext`，
会令 `use` lambda 被推断成 `Unit`，而 `streamChat()` 需要 `String`，
从而产生：

- Argument type mismatch: Unit / String
- Return type mismatch: expected Unit, actual String

v7.1 已将这些返回改为 `return@use`，使 `use` 表达式正确返回 String。


## v7.2 compile fix

重写 `RunApiGateway.streamChat()` 的控制流。

v7.1 仍在 `use {}` lambda 内使用带标签的提前返回，
Kotlin 对 `use` / `try` 表达式进行类型推断时仍会把部分分支推断为
`Unit` / `Any`，导致 `String` 返回类型冲突。

v7.2 不再使用任何 `return@use` / `return@withContext` 来处理 SSE 分支，
而是把整个 `use {}` 写成一个纯 `if / else if / else` 的 String 表达式。
所有正常分支都直接产生 `String`，异常分支为 `Nothing`。


## v7.3 API 简化

现在只保留两条路线：

1. 海鲸AI
   - 固定接口：`https://api.haijingai.com/v2/chat/completions`
   - 用户填写 API Key
   - 默认模型可编辑
   - 每个联系人也可单独填写模型 ID
   - 不再写死 `grok-4.6`

2. DeepSeek 官方
   - 固定接口：`https://api.deepseek.com/chat/completions`
   - 用户填写 API Key
   - 默认模型可编辑

联系人只选择：
- 海鲸AI
- DeepSeek 官方

后台 Planner / Memory / Proactive：
- 优先走 DeepSeek
- DeepSeek 未配置时自动走海鲸

已删除用户可见的：
- RunAPI
- fastChatModel
- plannerModel
- memoryModel
- proactiveModel
- systemProvider

这些内部角色不再要求用户单独配置。


## v7.4：几秒级自主续聊

正在聊天时，不再使用旧的 45~135 秒 WorkManager “补一句”。

现在流程：

1. 联系人正常回复第一条；
2. 同时后台开始一次“是否继续聊”的判断；
3. 自然停顿约 1.5~4.5 秒；
4. 如果用户已经发新消息，立即终止；
5. 如果判断适合继续，联系人自己的模型再生成 1~3 条独立消息；
6. 每条之间再停约 1.5~4.5 秒；
7. 整个短续聊只做一次继续/停止决策，不会每发一条都重新调用 DeepSeek。

判断模型：
- 配置 DeepSeek 时：DeepSeek 负责“要不要继续、继续几条、沿什么方向”
- 没配 DeepSeek 时：自动退回海鲸

真正聊天内容：
- 海鲸联系人 → 仍由该联系人选择的海鲸模型生成
- DeepSeek 联系人 → 由该联系人选择的 DeepSeek 模型生成

因此 DeepSeek 不会替海鲸联系人直接写台词。

旧的 45~135 秒 ConversationFollowUpWorker 已禁用。
几小时级主动聊天仍保留，和正在聊天时的几秒级续聊是两套独立机制。
