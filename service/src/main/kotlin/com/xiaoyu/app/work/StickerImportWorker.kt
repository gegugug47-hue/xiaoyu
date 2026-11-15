package com.xiaoyu.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiaoyu.app.data.sticker.StickerImportResult
import com.xiaoyu.app.service.R
import dagger.hilt.android.EntryPointAccessors

private fun Context.stickerEntryPoint(): WorkerEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)

/**
 * 表情包导入（执行书 M4-03 / 7.4.3）：选图/zip → 复制到私有目录 → 生成缩略图 →
 * 体积校验 → 解压防护 → 落库，全程后台执行并显示进度通知。
 */
class StickerImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val packId = inputData.getString(KEY_PACK_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val uris = inputData.getStringArray(KEY_URIS)?.mapNotNull { it.takeIf(String::isNotBlank)?.let(Uri::parse) }
            .orEmpty()
        if (uris.isEmpty()) return Result.failure()
        val autoTag = inputData.getBoolean(KEY_AUTO_TAG, true)
        val ep = applicationContext.stickerEntryPoint()
        ep.settingsRepository().ensureLoaded()
        val repository = ep.stickerRepository()
        val notifier = StickerImportNotifier(applicationContext)

        val startIndex = repository.packUsage(packId).second + 1
        notifier.showProgress(0, uris.size)
        val importResult = ep.stickerImporter().import(packId, uris, startIndex) { done, total, label ->
            setProgressAsync(
                workDataOf(KEY_PROGRESS to done, KEY_TOTAL to total, KEY_LABEL to label)
            )
            notifier.showProgress(done, total)
        }

        // 自动打标失败不阻塞导入：没有 VISION 时用户可在管理页手填
        val tagBatch = if (autoTag && importResult.items.isNotEmpty()) {
            runCatching { ep.stickerTagger().tag(importResult.items) }.getOrNull()
        } else {
            null
        }
        importResult.items.forEachIndexed { index, item ->
            repository.addImported(packId, item)
            val tag = tagBatch?.results?.getOrNull(index)
            if (tag != null && tag.tagged) {
                repository.updateStickerMeta(item.stickerId, tag.name, tag.tags, tag.moodTags)
            }
        }

        val finalResult = importResult.copy(autoTagSkipped = tagBatch?.degraded == true)
        notifier.showDone(finalResult)
        return Result.success()
    }

    companion object {
        const val KEY_PACK_ID = "pack_id"
        const val KEY_URIS = "uris"
        const val KEY_AUTO_TAG = "auto_tag"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val KEY_LABEL = "label"
        private const val TAG = "sticker-import"

        /** 由界面调用：把相册/文件选择器的结果交给 WorkManager 后台导入。 */
        fun enqueue(context: Context, packId: String, uris: List<Uri>, autoTag: Boolean = true) {
            if (packId.isBlank() || uris.isEmpty()) return
            val request = OneTimeWorkRequestBuilder<StickerImportWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PACK_ID to packId,
                        KEY_URIS to uris.map(Uri::toString).toTypedArray(),
                        KEY_AUTO_TAG to autoTag,
                    )
                )
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("sticker_import-$packId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/** 导入进度通知（复用 Notifier 的思路，单独一条低优先级渠道，不打扰聊天通知）。 */
private class StickerImportNotifier(private val context: Context) {

    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_IMPORT,
            "表情包导入",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "导入表情时的后台进度，完成后自动消失"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun canNotify(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    fun showProgress(done: Int, total: Int) {
        if (!canNotify()) return
        ensureChannel()
        val safeTotal = total.coerceAtLeast(1)
        val notification = NotificationCompat.Builder(context, CHANNEL_IMPORT)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle("正在导入表情")
            .setContentText("$done / $safeTotal")
            .setProgress(safeTotal, done.coerceIn(0, safeTotal), false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun showDone(result: StickerImportResult) {
        if (!canNotify()) return
        ensureChannel()
        val text = if (result.rejected.isEmpty()) {
            result.summary()
        } else {
            // 只列前两条，避免通知被折叠得看不清
            val detail = result.rejected.take(2).joinToString("；") { "${it.fileName}：${it.reason}" }
            "${result.summary()}（$detail${if (result.rejected.size > 2) "…" else ""}）"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_IMPORT)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle("表情导入完成")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_IMPORT = "xiaoyu_sticker_import"
        const val NOTIFICATION_ID = 4321
    }
}
