package com.xiaoyu.app

import android.app.Application
import android.util.Log
import com.xiaoyu.app.data.backup.BackupRepository
import com.xiaoyu.app.data.db.DatabaseSelfCheck
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class XiaoyuApp : Application() {

    @Inject
    lateinit var bootstrapper: AppBootstrapper

    override fun onCreate() {
        // 崩溃日志先装，尽量覆盖后续所有初始化异常（M5-05）。
        CrashLogger.install(this)
        applyPendingRestore()
        DatabaseSelfCheck.run(this).let { report ->
            if (!report.healthy) Log.e(TAG, report.message ?: "数据库自检未通过，已按损坏处理")
        }
        super.onCreate()
        bootstrapper.start()
    }

    /**
     * 恢复备份在下次启动时生效（M5-02）：先校验过的 pending 库原子替换当前库；
     * 替换失败则把原库放回，保证“导入失败时原数据完好”。
     */
    private fun applyPendingRestore() {
        val pending = File(filesDir, BackupRepository.PENDING_NAME)
        if (!pending.exists()) return
        val target = getDatabasePath(BackupRepository.DB_NAME)
        target.parentFile?.mkdirs()
        val rollback = File(target.parentFile, "xiaoyu-before-restore.db")
        runCatching {
            if (target.exists()) {
                rollback.delete()
                if (!target.renameTo(rollback)) {
                    target.copyTo(rollback, overwrite = true)
                    target.delete()
                }
            }
            if (!pending.renameTo(target)) {
                pending.copyTo(target, overwrite = true)
                pending.delete()
            }
            File(target.path + "-wal").delete()
            File(target.path + "-shm").delete()
            rollback.delete()
        }.onFailure {
            if (!target.exists() && rollback.exists()) rollback.renameTo(target)
            Log.e(TAG, "应用待恢复备份失败，已保留原数据库", it)
        }
    }

    companion object {
        private const val TAG = "XiaoyuApp"
    }
}
