package com.xiaoyu.app.data.sticker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 表情文件路径规范（执行书 7.4.3）：
 * 原图 `files/stickers/user/<packId>/<stickerId>.webp|.gif`，
 * 缩略图同目录 `_t.webp`。所有落盘路径都经过 [sanitize]，避免路径穿越。
 */
object StickerPaths {
    private const val ROOT_DIR = "stickers"
    private const val THUMB_SUFFIX = "_t"

    /** 用户导入表情的根目录（内置小鱼是代码绘制，不占磁盘）。 */
    fun userRoot(context: Context): File =
        File(File(context.filesDir, ROOT_DIR), "user").apply { mkdirs() }

    fun packDir(context: Context, packId: String): File =
        File(userRoot(context), sanitize(packId)).apply { mkdirs() }

    fun stickerFile(context: Context, packId: String, stickerId: String, extension: String): File =
        File(packDir(context, packId), "${sanitize(stickerId)}.$extension")

    /** 缩略图与原图同目录，只在面板/列表里读，避免解码原图。 */
    fun thumbnailFor(source: File): File =
        File(source.parentFile, "${source.nameWithoutExtension}${THUMB_SUFFIX}.webp")

    /** 缩略图路径（只做字符串换算，不做 IO，可在 UI 线程调用）。 */
    fun thumbnailPath(filePath: String): String {
        val file = File(filePath)
        return File(file.parentFile, "${file.nameWithoutExtension}${THUMB_SUFFIX}.webp").path
    }

    /** 只保留字母数字、`-`、`_`，其余替换为下划线；空白时回退成 pack。 */
    fun sanitize(raw: String): String {
        val cleaned = raw.trim().map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_'
        }.joinToString("")
        return cleaned.ifBlank { "pack" }
    }
}

/** 存储占用（执行书 7.4.5），按表情 / 图片 / 语音 / 备份分类。 */
data class StickerStorageUsage(
    val stickerBytes: Long = 0,
    val imageBytes: Long = 0,
    val voiceBytes: Long = 0,
    val backupBytes: Long = 0,
    val stickerCount: Int = 0,
) {
    val totalBytes: Long get() = stickerBytes + imageBytes + voiceBytes + backupBytes
}

/** 目录体积统计；超过 [STICKER_STORAGE_WARN_MB] 时设置页给黄色提示（执行书 7.4.5）。 */
@Singleton
class StickerStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun usage(stickerCount: Int): StickerStorageUsage = withContext(Dispatchers.IO) {
        StickerStorageUsage(
            stickerBytes = dirBytes(File(context.filesDir, "stickers")),
            imageBytes = dirBytes(File(context.filesDir, "generated-images")) +
                dirBytes(File(context.filesDir, "backgrounds")),
            voiceBytes = dirBytes(File(context.filesDir, "voices")),
            backupBytes = dirBytes(
                File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
            ),
            stickerCount = stickerCount,
        )
    }

    /** 表情总量告警阈值（200MB）。 */
    fun isStickerOverWarn(usage: StickerStorageUsage): Boolean =
        usage.stickerBytes > STICKER_STORAGE_WARN_MB * 1024L * 1024L

    private fun dirBytes(dir: File): Long {
        val files = dir.listFiles() ?: return 0
        return files.sumOf { file -> if (file.isDirectory) dirBytes(file) else file.length() }
    }

    companion object {
        const val STICKER_STORAGE_WARN_MB = 200

        /** 人类可读体积文案，例如 "1.2 MB"。 */
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
            else -> String.format(Locale.CHINA, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        }
    }
}
