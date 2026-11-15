package com.xiaoyu.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiaoyu.app.data.backup.BackupRepository
import com.xiaoyu.app.data.export.PublicDownloads
import com.xiaoyu.app.data.system.StorageGuard
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
    fun backupRepository(): BackupRepository
    fun settingsRepository(): com.xiaoyu.app.data.settings.SettingsRepository
    fun publicDownloads(): PublicDownloads
    fun storageGuard(): StorageGuard
}

/**
 * 每周自动备份（执行书 M5-03 / 8.1）：
 * 生成本地备份包 → 复制到公共目录 `Download/小鱼备份/` → 公共目录与私有目录都只保留最近 4 份。
 * 失败时按指数退避重试，超过次数才判定失败；全程写日志便于自查。
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java)
        val settings = ep.settingsRepository().apply { ensureLoaded() }
        val storage = ep.storageGuard()
        if (storage.isLow()) {
            Log.w(TAG, "自动备份跳过：${storage.lowSpaceMessage()}")
            return retryOrFail()
        }

        val file = ep.backupRepository().createBackupFile()
        if (file == null) {
            Log.e(TAG, "自动备份失败：无法生成备份包")
            return retryOrFail()
        }

        val published = runCatching {
            ep.publicDownloads().saveBytes(PUBLIC_DIR, file.name, MIME, file.readBytes())
        }.onFailure { Log.e(TAG, "写入公共下载目录失败", it) }.getOrNull()
        if (published == null) {
            Log.e(TAG, "自动备份失败：公共目录写入失败（本地备份位于 ${file.parent}）")
            return retryOrFail()
        }

        runCatching { ep.publicDownloads().prune(PUBLIC_DIR, BACKUP_SUFFIX, KEEP_COUNT) }
            .onFailure { Log.w(TAG, "清理旧自动备份失败", it) }
        settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        Log.i(TAG, "自动备份完成：${published.displayPath}")
        return Result.success(workDataOf(KEY_PATH to published.displayPath))
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        private const val TAG = "BackupWorker"
        private const val PUBLIC_DIR = "小鱼备份"
        private const val MIME = "application/octet-stream"
        private const val BACKUP_SUFFIX = ".xiaoyu"
        private const val KEEP_COUNT = 4
        private const val MAX_ATTEMPTS = 5
        const val KEY_PATH = "backup_path"
    }
}
