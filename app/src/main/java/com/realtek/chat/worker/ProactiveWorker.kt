package com.realtek.chat.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.realtek.chat.MainActivity
import com.realtek.chat.ai.ChatEngine
import com.realtek.chat.ai.SocialEngine
import com.realtek.chat.settings.AppSettings
import com.realtek.chat.storage.AppDb
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ConversationFollowUpWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // v7.4：旧的 45~135 秒跟进机制已停用。
        // 保留这个 Worker 只为了让升级前已经排队的旧任务安全结束。
        return Result.success()
    }
}

object ConversationFollowUpScheduler {
    fun schedule(
        context: Context,
        contactId: Int,
        expectedLastMessageId: Long
    ) {
        // v7.4 不再创建延迟几十秒的跟进任务。
    }
}

class ProactiveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (AppSettings(applicationContext).sleepMode) {
            return Result.success()
        }

        val hour = LocalDateTime.now().hour
        if (hour >= 23 || hour < 8) return Result.success()

        val db = AppDb.get(applicationContext)
        val engine = ChatEngine(applicationContext)
        val socialEngine = SocialEngine(applicationContext)
        val now = System.currentTimeMillis()

        for (contact in db.getContacts()) {
            if (!contact.proactiveEnabled) continue

            val last = db.getLastMessage(contact.id) ?: continue
            val sinceLastHours = (now - last.createdAt) / 3_600_000.0

            val userCount = db.userMessageCount(contact.id)
            val state = socialEngine.snapshot(contact.id)

            val baseQuietHours = when {
                userCount < 8 -> 6.0
                userCount < 30 -> 4.0
                else -> 3.0
            }

            val minQuietHours =
                (
                    baseQuietHours -
                        state.socialDrive * 1.2
                    ).coerceIn(
                    1.8,
                    6.0
                )

            if (sinceLastHours < minQuietHours) continue

            val sinceProactive = contact.lastProactiveAt?.let {
                (now - it) / 3_600_000.0
            } ?: 999.0

            val minProactiveGap =
                (
                    10.0 -
                        state.familiarity * 3.5 -
                        state.socialDrive * 1.5
                    ).coerceIn(
                    4.5,
                    10.0
                )

            if (sinceProactive < minProactiveGap) continue

            val memories = db.getMemories(contact.id, 120)

            val dueSoon = memories
                .firstOrNull { m ->
                    m.dueAt?.let {
                        abs(it - now) <=
                            24 * 3_600_000L
                    } == true
                }

            val openLoop = memories
                .firstOrNull {
                    it.type == "open_loop"
                }

            // 非事件型主动联系不每小时都触发模型。
            // 熟悉联系人约每 3 小时得到一次“要不要主动说话”的机会。
            val hourBucket = now / 3_600_000L
            val socialWindow =
                ((hourBucket + contact.id * 5L) % 3L == 0L)

            if (
                dueSoon == null &&
                openLoop == null &&
                !socialWindow
            ) continue

            val reason = when {
                dueSoon != null ->
                    "你记得的一件事情已经临近或刚发生：${dueSoon.content}。如果自然，可以自己问一句或提一句。"

                openLoop != null ->
                    "你们之前还有一件没有真正结束的话题：${openLoop.content}。如果现在接着问很自然，可以承接；不自然就不要发。"

                else ->
                    "已经有几个小时没有交流。结合当前社交状态、最近话题和共同记忆，判断这个联系人是否真的有自然理由主动开启一个小话题。"
            }

            val text = engine.maybeProactive(
                contact = contact,
                reason = reason,
                conversationContinuation = false
            )

            if (!text.isNullOrBlank()) {
                NotificationHelper.show(
                    applicationContext,
                    contact.id,
                    contact.name,
                    text
                )
            }
        }

        return Result.success()
    }
}

object ProactiveScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ProactiveWorker>(
            1,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("realtek_proactive")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "realtek_proactive",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

private object NotificationHelper {
    fun show(
        context: Context,
        id: Int,
        title: String,
        text: String
    ) {
        val manager = context
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "realtek_messages"
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "消息",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val pending = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            4000 + id,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }
}
