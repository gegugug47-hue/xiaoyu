package com.xiaoyu.app.data.export

import android.content.Context
import android.net.Uri
import com.xiaoyu.app.data.db.DiaryDao
import com.xiaoyu.app.data.db.MessageDao
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.db.SessionDao
import com.xiaoyu.app.data.db.StickerDao
import com.xiaoyu.app.data.system.StorageGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导出 Markdown（执行书 M5-04 / 6.9 数据管理）：
 * 支持整段会话与单篇日记，表情写成 `[表情:名称]`，图片拷到 `assets/images/` 并用相对路径引用，
 * 产物落在公共下载目录 `Download/小鱼导出/`，可在 PC 上直接阅读。
 */
@Singleton
class MarkdownExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val diaryDao: DiaryDao,
    private val stickerDao: StickerDao,
    private val publicDownloads: PublicDownloads,
    private val storageGuard: StorageGuard,
) {

    sealed interface ExportResult {
        /** [displayPath] 形如 `Download/小鱼导出/chat-xxx.md`。 */
        data class Success(val displayPath: String, val images: Int) : ExportResult

        data class Failure(val message: String) : ExportResult
    }

    /** 导出整段会话（隐藏消息不导出，撤回消息保留占位）。 */
    suspend fun exportSession(sessionId: Long): ExportResult = withContext(Dispatchers.IO) {
        val session = sessionDao.byId(sessionId)
            ?: return@withContext ExportResult.Failure("没有找到这段会话。")
        if (storageGuard.isLow()) {
            return@withContext ExportResult.Failure(storageGuard.lowSpaceMessage())
        }
        val messages = messageDao.visibleForSession(sessionId)
        if (messages.isEmpty()) {
            return@withContext ExportResult.Failure("这段会话还没有可导出的消息。")
        }

        val exportedAt = Date()
        val builder = StringBuilder()
        val title = session.title.ifBlank { "未命名会话" }
        builder.appendLine("# 小鱼 · 会话导出")
        builder.appendLine()
        builder.appendLine("- 导出时间：${formatDateTime(exportedAt.time)}")
        builder.appendLine("- 会话标题：$title")
        builder.appendLine("- 消息条数：${messages.size}")
        builder.appendLine("- 时间范围：${formatDateTime(messages.first().createdAt)} ~ ${formatDateTime(messages.last().createdAt)}")
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()

        var imageCount = 0
        messages.forEach { message ->
            if (message.retracted) {
                builder.appendLine("**${roleLabel(message)}** · ${formatDateTime(message.createdAt)}")
                builder.appendLine()
                builder.appendLine("_[消息已撤回]_")
                builder.appendLine()
                return@forEach
            }
            val imagePath = message.imageUri?.let { exportImage(message, it) }
            if (imagePath != null) imageCount++
            val body = renderBody(message, imagePath)
            builder.appendLine("**${roleLabel(message)}** · ${formatDateTime(message.createdAt)}")
            builder.appendLine()
            builder.appendLine(body)
            builder.appendLine()
        }

        val fileName = "chat-${safeName(title)}-${fileStamp(exportedAt)}.md"
        val saved = publicDownloads.saveText(EXPORT_DIR, fileName, "text/markdown", builder.toString())
            ?: return@withContext ExportResult.Failure("导出失败：无法写入下载目录。")
        ExportResult.Success(saved.displayPath, imageCount)
    }

    /** 导出单篇日记（含元信息头与心情快照）。 */
    suspend fun exportDiary(diaryId: Long): ExportResult = withContext(Dispatchers.IO) {
        val diary = diaryDao.byId(diaryId)
            ?: return@withContext ExportResult.Failure("没有找到这篇日记。")
        if (storageGuard.isLow()) {
            return@withContext ExportResult.Failure(storageGuard.lowSpaceMessage())
        }
        val exportedAt = Date()
        val builder = StringBuilder()
        builder.appendLine("# 小鱼 · 日记（${diary.eventDate}）")
        builder.appendLine()
        builder.appendLine("- 导出时间：${formatDateTime(exportedAt.time)}")
        builder.appendLine("- 日记日期：${diary.eventDate}")
        builder.appendLine("- 心情快照：${diary.moodSnapshot}")
        builder.appendLine("- 好感快照：${diary.favorabilitySnapshot}")
        builder.appendLine("- 加密日记：${if (diary.isSecret) "是" else "否"}")
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()
        builder.appendLine(diary.contentMd.trim())
        builder.appendLine()

        val fileName = "diary-${diary.eventDate}-${fileStamp(exportedAt)}.md"
        val saved = publicDownloads.saveText(EXPORT_DIR, fileName, "text/markdown", builder.toString())
            ?: return@withContext ExportResult.Failure("导出失败：无法写入下载目录。")
        ExportResult.Success(saved.displayPath, 0)
    }

    private suspend fun renderBody(message: MessageEntity, imagePath: String?): String = when {
        message.type == "sticker" -> {
            val name = message.stickerId?.let { stickerDao.byId(it)?.name } ?: "未知"
            "[表情:$name]"
        }
        imagePath != null && message.content.isBlank() -> "![图片]($imagePath)"
        imagePath != null -> "${message.content}\n\n![图片]($imagePath)"
        message.voiceUri != null -> "[语音] ${message.content}".trim()
        else -> message.content
    }

    /** 图片复制到 `Download/小鱼导出/assets/images/`，Markdown 里只写相对路径。 */
    private fun exportImage(message: MessageEntity, raw: String): String? {
        val bytes = readImageBytes(raw) ?: return null
        if (bytes.isEmpty()) return null
        val extension = imageExtension(raw)
        val fileName = "msg-${message.id}.$extension"
        val saved = publicDownloads.saveBytes(IMAGE_DIR, fileName, mimeOf(extension), bytes) ?: return null
        // 只保留相对目录，便于整个「小鱼导出」文件夹拷到电脑后仍能显示。
        return "assets/images/${saved.displayPath.substringAfterLast('/')}"
    }

    private fun readImageBytes(raw: String): ByteArray? = runCatching {
        when {
            raw.startsWith("/") -> File(raw).takeIf { it.exists() }?.readBytes()
            raw.startsWith("file:") -> Uri.parse(raw).path?.let { path -> File(path).takeIf { it.exists() }?.readBytes() }
            else -> context.contentResolver.openInputStream(Uri.parse(raw))?.use { it.readBytes() }
        }
    }.getOrNull()

    private fun imageExtension(raw: String): String {
        val name = runCatching { Uri.parse(raw).lastPathSegment }.getOrNull() ?: raw
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (extension in IMAGE_EXTENSIONS) extension else "jpg"
    }

    private fun mimeOf(extension: String): String = when (extension) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        else -> "image/jpeg"
    }

    private fun roleLabel(message: MessageEntity): String = when (message.role) {
        "user" -> "我"
        "system_notice" -> "系统"
        else -> "她"
    }

    private fun formatDateTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))

    private fun fileStamp(date: Date): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(date)

    private fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(40)
        return cleaned.ifBlank { "chat" }
    }

    companion object {
        const val EXPORT_DIR = "小鱼导出"
        private const val IMAGE_DIR = "$EXPORT_DIR/assets/images"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic")
    }
}
