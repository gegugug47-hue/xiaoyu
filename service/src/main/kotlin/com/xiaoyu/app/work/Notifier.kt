package com.xiaoyu.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.xiaoyu.app.service.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 兼容旧调用：启动时一次性创建全部渠道。 */
    fun ensureChannel() = ensureChannels()

    /** 创建 4 个通知渠道（执行书 6.11）：主动消息、后台任务结果、备份、系统提醒。 */
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(CHANNEL_PROACTIVE, context.getString(R.string.notify_channel_proactive), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notify_channel_proactive_desc)
                setShowBadge(true)
            },
            NotificationChannel(CHANNEL_TASK, context.getString(R.string.notify_channel_task), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notify_channel_task_desc)
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_BACKUP, context.getString(R.string.notify_channel_backup), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.notify_channel_backup_desc)
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_SYSTEM, context.getString(R.string.notify_channel_system), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notify_channel_system_desc)
                setShowBadge(true)
            },
            // v3 §7.13 / B-22：声音/震动四个组合各用一个渠道（API 26+ 渠道属性创建后不可改）
            NotificationChannel(
                CHANNEL_PROACTIVE_QUIET,
                context.getString(R.string.notify_channel_proactive_quiet),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notify_channel_proactive_quiet_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            },
            NotificationChannel(
                CHANNEL_PROACTIVE_NO_SOUND,
                context.getString(R.string.notify_channel_proactive_no_sound),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_channel_proactive_no_sound_desc)
                setSound(null, null)
                enableVibration(true)
                setShowBadge(true)
            },
            NotificationChannel(
                CHANNEL_PROACTIVE_NO_VIBRATE,
                context.getString(R.string.notify_channel_proactive_no_vibrate),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_channel_proactive_no_vibrate_desc)
                enableVibration(false)
                setShowBadge(true)
            },
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    /** 通知权限是否可用（POST_NOTIFICATIONS + 系统开关），供后台存活自检复用。 */
    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 主动消息通知。[hideContent] 为 true（关闭「通知显示正文」或开启应用锁）时只显示
     * 「小鱼发来一条消息」，不在通知栏暴露聊天正文（执行书 5.6.3 / 7.13）。
     * 通知体用 MessagingStyle 区分「她」和「你」，并挂 RemoteInput 支持直接回复（执行书 6.11）。
     * [sound] / [vibrate] 来自设置页「通知」分组；[hideOnLockScreen] 为 true 时锁屏上完全不显示。
     * 返回是否真正发出。
     */
    fun notifyProactive(
        text: String,
        sessionId: Long,
        hideContent: Boolean = false,
        userText: String? = null,
        sound: Boolean = true,
        vibrate: Boolean = true,
        hideOnLockScreen: Boolean = false,
    ): Boolean {
        if (!canPostNotifications()) return false
        ensureChannels()

        val notifyId = notificationId(sessionId)
        val pending = launchPendingIntent(sessionId, notifyId)
        val shown = if (hideContent) context.getString(R.string.notify_hidden_text) else text
        val now = System.currentTimeMillis()

        val her = Person.Builder().setName(context.getString(R.string.notify_sender_her)).build()
        val me = Person.Builder().setName(context.getString(R.string.notify_sender_me)).build()
        val style = NotificationCompat.MessagingStyle(her)
            .setConversationTitle(context.getString(R.string.notify_conversation_title))
        // 先你后她：MessagingStyle 按加入顺序展示
        if (!hideContent && !userText.isNullOrBlank()) {
            style.addMessage(userText, now - 1_000, me)
        }
        style.addMessage(shown, now, her)

        val channelId = when {
            sound && vibrate -> CHANNEL_PROACTIVE
            sound -> CHANNEL_PROACTIVE_NO_VIBRATE
            vibrate -> CHANNEL_PROACTIVE_NO_SOUND
            else -> CHANNEL_PROACTIVE_QUIET
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(context.getString(R.string.notify_app_name))
            .setContentText(shown)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction(sessionId, notifyId))

        // v3 §7.13 / B-22：声音与震动开关。API 26+ 走上面的四条渠道，
        // 这里同时照顾 pre-O 机型（通知级 sound/vibrate 仍有效）。
        if (!sound) builder.setSound(null)
        builder.setVibrate(if (vibrate) longArrayOf(0, 180) else null)
        if (!sound && !vibrate) builder.setSilent(true)

        if (hideOnLockScreen) {
            // 锁屏隐藏：锁屏上不出现任何内容
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        } else if (hideContent) {
            // 锁屏上只给公开版本，正文不落到锁屏；点开仍需应用锁
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(pending))
        }

        val notification = builder.build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notifyId, notification)
        }.isSuccess
    }

    /** 后台任务结果（日记 / 生活碎片 / 记忆提炼），低声提醒。 */
    fun notifyTaskResult(title: String, text: String): Boolean =
        notifySimple(CHANNEL_TASK, ID_TASK, title, text, NotificationCompat.PRIORITY_LOW)

    /** 备份完成 / 失败提醒。 */
    fun notifyBackup(title: String, text: String): Boolean =
        notifySimple(CHANNEL_BACKUP, ID_BACKUP, title, text, NotificationCompat.PRIORITY_MIN)

    /** 系统提醒（护栏、低电量等）。 */
    fun notifySystem(title: String, text: String): Boolean =
        notifySimple(CHANNEL_SYSTEM, ID_SYSTEM, title, text, NotificationCompat.PRIORITY_HIGH)

    /** 直接回复的 RemoteInput 动作：PendingIntent 必须 MUTABLE，系统才能写入用户输入（6.11）。 */
    private fun replyAction(sessionId: Long, notificationId: Int): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notify_reply_label))
            .build()
        val intent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = NotificationReplyReceiver.ACTION_REPLY
            putExtra(NotificationReplyReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_fish,
            context.getString(R.string.notify_reply_action),
            pending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    /** 锁屏公开版本：只有标题和笼统文案，不含聊天正文。 */
    private fun publicVersion(pending: PendingIntent?): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_PROACTIVE)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(context.getString(R.string.notify_app_name))
            .setContentText(context.getString(R.string.notify_hidden_text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

    /**
     * 通知点击仍回到 App 并携带 sessionId。
     * 模块内不依赖 app 的 MainActivity：用启动意图回到宿主应用即可。
     */
    private fun launchPendingIntent(sessionId: Long, notificationId: Int): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("sessionId", sessionId)
        }
        return intent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private fun notifySimple(channel: String, id: Int, title: String, text: String, priority: Int): Boolean {
        if (!canPostNotifications()) return false
        ensureChannels()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(launchPendingIntent(0, id))
            .setPriority(priority)
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }.isSuccess
    }

    companion object {
        /** 主动消息渠道（保持旧常量名与旧 id，兼容既有数据）。 */
        const val CHANNEL_PROACTIVE = "xiaoyu_proactive"
        /** 后台任务结果渠道。 */
        const val CHANNEL_TASK = "xiaoyu_task"
        /** 备份渠道。 */
        const val CHANNEL_BACKUP = "xiaoyu_backup"
        /** 系统提醒渠道。 */
        const val CHANNEL_SYSTEM = "xiaoyu_system"
        const val CHANNEL_PROACTIVE_QUIET = "xiaoyu_proactive_quiet"

        /** 仅震动（关闭提示音）。 */
        const val CHANNEL_PROACTIVE_NO_SOUND = "xiaoyu_proactive_no_sound"

        /** 仅提示音（关闭震动）。 */
        const val CHANNEL_PROACTIVE_NO_VIBRATE = "xiaoyu_proactive_no_vibrate"

        /** RemoteInput 回传文本的键。 */
        const val KEY_REPLY_TEXT = "xiaoyu_reply_text"

        private const val ID_TASK = 9001
        private const val ID_BACKUP = 9002
        private const val ID_SYSTEM = 9003

        /** 主动消息沿用 sessionId 作为通知 id，重复推送会合并到同一条。 */
        fun notificationId(sessionId: Long): Int = sessionId.toInt()
    }
}
