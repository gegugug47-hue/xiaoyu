package com.xiaoyu.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiaoyu.app.data.repo.ChatEvent
import com.xiaoyu.app.data.repo.ChatRepository
import com.xiaoyu.app.data.settings.SettingsRepository
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
 * 通知里 RemoteInput 的直接回复（执行书 6.11）：
 * 取出文本后交给聊天链路，真正生成放到 WorkManager，避免广播超时。
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
        // 收起通知：回复已交出去，正文稍后出现在聊天里
        runCatching { NotificationManagerCompat.from(appContext).cancel(notificationId) }
        val request = OneTimeWorkRequestBuilder<NotificationReplyWorker>()
            .setInputData(
                workDataOf(
                    NotificationReplyWorker.KEY_SESSION_ID to sessionId,
                    NotificationReplyWorker.KEY_TEXT to text,
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
    }
}

/**
 * 把通知里的回复送进聊天链路：用户消息先落库（ChatRepository.send 内部完成），
 * 再流式生成她的回复。不做重试，避免同一条回复重复入库。
 */
class NotificationReplyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT)?.trim().orEmpty()
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (text.isEmpty() || sessionId <= 0) return Result.success()
        val ep = EntryPointAccessors.fromApplication(applicationContext, NotificationReplyEntryPoint::class.java)
        ep.settingsRepository().ensureLoaded()
        var failed: String? = null
        runCatching {
            ep.chatRepository().send(sessionId, text).collect { event ->
                if (event is ChatEvent.Failed) failed = event.message
            }
        }.onFailure { Log.w(TAG, "通知直回生成失败: ${it.message}") }
        if (failed != null) Log.w(TAG, "通知直回失败: $failed")
        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_TEXT = "reply_text"
    }
}
