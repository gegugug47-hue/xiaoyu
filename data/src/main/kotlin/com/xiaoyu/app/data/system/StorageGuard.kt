package com.xiaoyu.app.data.system

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 磁盘空间守卫（执行书 M5-05）：备份、导入、导出前先判断可用空间，
 * 不足时返回可读中文提示，而不是让写入抛 IOException 后静默失败。
 */
@Singleton
class StorageGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 剩余空间（字节）。取应用私有目录所在分区。 */
    fun availableBytes(path: File = context.filesDir): Long = runCatching {
        StatFs(path.path).availableBytes
    }.getOrDefault(0L)

    /** 是否低于安全线；[requiredBytes] 为本次操作预计额外需要的空间。 */
    fun isLow(requiredBytes: Long = 0L): Boolean = availableBytes() < MIN_FREE_BYTES + requiredBytes

    /** 人类可读的剩余空间，如 "1.2 GB"。 */
    fun humanAvailable(): String = formatBytes(availableBytes())

    /** 磁盘空间不足时的可读提示。 */
    fun lowSpaceMessage(requiredBytes: Long = 0L): String =
        "存储空间不足：当前剩余 ${humanAvailable()}，完成本次操作至少需要 " +
            "${formatBytes(MIN_FREE_BYTES + requiredBytes)}。请清理相册缓存或旧备份后再试。"

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) "${value.toLong()} ${units[index]}"
        else String.format(Locale.CHINA, "%.1f %s", value, units[index])
    }

    companion object {
        /** 低于 50MB 视为空间不足，避免写一半失败留下损坏文件。 */
        const val MIN_FREE_BYTES: Long = 50L * 1024 * 1024
    }
}
