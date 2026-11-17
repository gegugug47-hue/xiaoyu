package com.xiaoyu.app.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiaoyu.app.service.R
import com.xiaoyu.app.data.repo.ChatEvent
import com.xiaoyu.app.data.repo.ChatRepository
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.app.work.Notifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val TAG = "XiaoyuNotifyReply"

/** 通知直回需要的依赖（只做加法，复用 WorkerEntryPoint 的同一批单例）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationReplyEntryPoint {
    fun chatRepository(): ChatRepository
    fun settingsRepository(): SettingsRepository
}

/**
 * 通知里 RemoteInput 的直接回复（执行书 6.11 + v3 §7.16 改进）：
 * 取出文本后先更新通知显示「正在发送…」，再交给聊天链路，真正生成放到 WorkManager，
 * 避免广播超时。成功后取消通知，失败则更新通知显示错误。
 */
class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = results.getCharSequence(Notifier.KEY_REPLY_TEXT)?.toString()?.trim().orEmpty()
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Notifier.notificationId(sessionId))
        if (text.isEmpty() || sessionId <= 0) return

        val appContext = context.applicationContext
        // v3 §7.16：回复后不立即 cancel，先更新通知显示「正在发送…」
        showSendingNotification(appContext, notificationId, sessionId, text)

        val request = OneTimeWorkRequestBuilder<NotificationReplyWorker>()
            .setInputData(
                workDataOf(
                    NotificationReplyWorker.KEY_SESSION_ID to sessionId,
                    NotificationReplyWorker.KEY_TEXT to text,
                    NotificationReplyWorker.KEY_NOTIFICATION_ID to notificationId,
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork("notify_reply-$sessionId", ExistingWorkPolicy.REPLACE, request)
        }.onFailure { Log.w(TAG, "通知直回入队失败: ${it.message}") }
    }

    companion object {
        const val ACTION_REPLY = "com.xiaoyu.app.action.NOTIFICATION_REPLY"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        /** v3 §7.16：通知回复后先展示「正在发送…」，给用户即时反馈。 */
        private fun showSendingNotification(ctx: Context, notificationId: Int, sessionId: Long, replyText: String) {
            val notif = NotificationCompat.Builder(ctx, Notifier.CHANNEL_PROACTIVE)
                .setSmallIcon(R.drawable.ic_stat_fish)
                .setContentTitle(ctx.getString(R.string.notify_app_name))
                .setContentText(ctx.getString(R.string.notify_reply_sending, replyText.take(50)))
                .setStyle(
                    NotificationCompat.MessagingStyle(
                        androidx.core.app.Person.Builder().setName(ctx.getString(R.string.notify_sender_her)).build()
                    )
                        .setConversationTitle(ctx.getString(R.string.notify_conversation_title))
                        .addMessage(
                            ctx.getString(R.string.notify_reply_sending, replyText),
                            System.currentTimeMillis(),
                            androidx.core.app.Person.Builder().setName(ctx.getString(R.string.notify_sender_me)).build()
                        )
                )
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        ctx, sessionId.toInt(),
                        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("sessionId", sessionId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(notificationId, notif) }
        }
    }
}

/**
 * 把通知里的回复送进聊天链路：用户消息先落库（ChatRepository.send 内部完成），
 * 再流式生成她的回复。成功后取消通知，失败则更新通知显示错误（v3 §7.16）。
 */
class NotificationReplyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT)?.trim().orEmpty()
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, -1)
        if (text.isEmpty() || sessionId <= 0) return Result.success()
        val ep = EntryPointAccessors.fromApplication(applicationContext, NotificationReplyEntryPoint::class.java)
        ep.settingsRepository().ensureLoaded()
        var failed: String? = null
        runCatching {
            ep.chatRepository().send(sessionId, text).collect { event ->
                if (event is ChatEvent.Failed) failed = event.message
            }
        }.onFailure {
            failed = it.message
            Log.w(TAG, "通知直回生成失败: ${it.message}")
        }

        val nm = NotificationManagerCompat.from(applicationContext)
        if (failed != null && notificationId > 0) {
            // v3 §7.16：回复失败时通知显示错误，方便手动重试
            val failNotif = NotificationCompat.Builder(applicationContext, Notifier.CHANNEL_PROACTIVE)
                .setSmallIcon(R.drawable.ic_stat_fish)
                .setContentTitle(applicationContext.getString(R.string.notify_reply_failed_title))
                .setContentText(applicationContext.getString(R.string.notify_reply_failed_body, failed))
                .setStyle(NotificationCompat.BigTextStyle().bigText(applicationContext.getString(R.string.notify_reply_failed_body, failed)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext, sessionId.toInt(),
                        applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("sessionId", sessionId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .build()
            runCatching { nm.notify(notificationId, failNotif) }
        } else if (notificationId > 0) {
            // 成功：通知消失，正文已在聊天里
            runCatching { nm.cancel(notificationId) }
        }

        return if (failed != null) Result.failure() else Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_TEXT = "reply_text"
        const val KEY_NOTIFICATION_ID = "notification_id"
    }
}
