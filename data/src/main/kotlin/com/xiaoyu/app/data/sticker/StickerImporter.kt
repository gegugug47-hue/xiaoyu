package com.xiaoyu.app.data.sticker

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Movie
import android.net.Uri
import com.xiaoyu.app.data.R
import com.xiaoyu.app.data.archive.SafeZip
import com.xiaoyu.app.data.media.ImageCompressor
import com.xiaoyu.soul.safety.InjectionGuard
import com.xiaoyu.soul.safety.PromptZone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** 单枚导入结果，直接映射成 [com.xiaoyu.app.data.db.StickerEntity]。 */
data class StickerImportItem(
    val stickerId: String,
    val name: String,
    val filePath: String,
    val thumbnailPath: String?,
    val mime: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val animated: Boolean,
)

/** 被拒绝的文件与可读原因，导入结束后原样展示给用户。 */
data class RejectedSticker(
    val fileName: String,
    val reason: String,
)

data class StickerImportResult(
    val items: List<StickerImportItem> = emptyList(),
    val rejected: List<RejectedSticker> = emptyList(),
    val totalBytes: Long = 0,
    val autoTagSkipped: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty() && rejected.isEmpty()

    /** 通知栏与页面共用的一句话总结。 */
    fun summary(context: Context): String = buildString {
        append(context.getString(R.string.data_sticker_import_summary_imported, items.size))
        if (rejected.isNotEmpty()) {
            append(context.getString(R.string.data_sticker_import_summary_rejected, rejected.size))
        }
        if (autoTagSkipped) append(context.getString(R.string.data_sticker_import_summary_no_vision))
    }
}

/**
 * 表情包导入管线（执行书 7.4.3 六步）：
 * 选择图片/zip → 复制到临时目录 → 解码探测 → 体积与格式校验 → 转码落盘 + 128px 缩略图 → 返回入库数据。
 * 全程在 [Dispatchers.IO]，调用方（StickerImportWorker）负责入库与进度通知。
 */
@Singleton
class StickerImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressor: ImageCompressor,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun import(
        packId: String,
        uris: List<Uri>,
        startIndex: Int = 1,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): StickerImportResult = withContext(Dispatchers.IO) {
        val runDir = File(context.cacheDir, "sticker_import_${System.currentTimeMillis()}").apply { mkdirs() }
        val rejected = mutableListOf<RejectedSticker>()
        try {
            val candidates = collectCandidates(uris, runDir, rejected)
            val limited = candidates.take(MAX_FILES)
            candidates.drop(MAX_FILES).forEach {
                rejected += RejectedSticker(
                    it.name,
                    context.getString(R.string.data_sticker_import_skipped_over_limit, MAX_FILES),
                )
            }

            val items = mutableListOf<StickerImportItem>()
            var totalBytes = 0L
            limited.forEachIndexed { index, file ->
                onProgress(index, limited.size, file.name)
                val label = file.name.ifBlank { "表情${startIndex + index}" }
                val remaining = MAX_TOTAL_BYTES - totalBytes
                val item = runCatching { processFile(packId, file, startIndex + index) }
                    .getOrElse { error ->
                        rejected += RejectedSticker(
                            label,
                            error.message ?: context.getString(R.string.data_sticker_import_process_failed),
                        )
                        null
                    }
                if (item != null) {
                    if (item.bytes > remaining) {
                        File(item.filePath).delete()
                        item.thumbnailPath?.let { File(it).delete() }
                        rejected += RejectedSticker(
                            label,
                            context.getString(
                                R.string.data_sticker_import_total_too_large,
                                (MAX_TOTAL_BYTES / 1024 / 1024).toInt(),
                            ),
                        )
                    } else {
                        items += item
                        totalBytes += item.bytes
                    }
                }
            }
            onProgress(limited.size, limited.size, "")
            StickerImportResult(items = items, rejected = rejected, totalBytes = totalBytes)
        } finally {
            runDir.deleteRecursively()
        }
    }

    /** 删除单枚表情的原图与缩略图；文件不存在时静默返回。 */
    fun deleteFiles(filePath: String) {
        runCatching {
            val file = File(filePath)
            StickerPaths.thumbnailFor(file).delete()
            file.delete()
        }
    }

    /** 删除整个表情包目录。 */
    fun deletePackFiles(packId: String) {
        runCatching { StickerPaths.packDir(context, packId).deleteRecursively() }
    }

    /** 把 URI 或 zip 展开成待处理的临时图片文件。 */
    private fun collectCandidates(
        uris: List<Uri>,
        runDir: File,
        rejected: MutableList<RejectedSticker>,
    ): List<File> {
        val files = mutableListOf<File>()
        uris.forEachIndexed { index, uri ->
            val label = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "第 ${index + 1} 个文件"
            val raw = copyToTemp(uri, File(runDir, "raw_$index")) ?: run {
                rejected += RejectedSticker(label, context.getString(R.string.data_sticker_import_unreadable))
                return@forEachIndexed
            }
            if (isZip(raw)) {
                val target = File(runDir, "zip_$index").apply { mkdirs() }
                val extracted = runCatching { unzipSafely(raw, target) }.getOrElse { error ->
                    rejected += RejectedSticker(
                        label,
                        error.message ?: context.getString(R.string.data_sticker_import_zip_failed),
                    )
                    emptyList()
                }
                if (extracted.isEmpty() && rejected.none { it.fileName == label }) {
                    rejected += RejectedSticker(label, context.getString(R.string.data_sticker_import_zip_empty))
                } else {
                    files += extracted
                }
            } else {
                files += raw
            }
        }
        return files
    }

    /**
     * Zip Slip 防护（执行书 7.4.3 / 7.6）：复用 [SafeZip] 的条目名校验与 canonicalPath 边界检查，
     * 拒绝绝对路径、`..`、盘符与控制字符，并限制条目数 / 单文件 / 总解压体积，防 zip bomb。
     */
    private fun unzipSafely(zipFile: File, targetDir: File): List<File> {
        val output = mutableListOf<File>()
        val zip = SafeZip.open(zipFile)
        zip.use {
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                // 只为校验与后缀判断；实际读取仍用原始条目名，避免反斜杠条目找不到
                val relative = SafeZip.validateName(entry.name)
                if (!isSupportedExtension(relative)) continue
                val target = SafeZip.resolve(targetDir, entry.name)
                val size = SafeZip.copyTo(zip, entry.name, targetDir, target)
                if (size > 0L) output += target
            }
        }
        return output.sortedBy { it.name }
    }

    /** 单个文件：校验格式/尺寸/体积 → 转码落盘 → 生成缩略图。 */
    private fun processFile(packId: String, source: File, index: Int): StickerImportItem {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException(context.getString(R.string.data_sticker_import_unknown_format))
        }
        val mime = normalizeMime(bounds.outMimeType, source.name)
        val animated = mime == MIME_GIF
        if (mime == null || mime !in SUPPORTED_MIMES) {
            throw IllegalStateException(
                context.getString(
                    R.string.data_sticker_import_unsupported_format,
                    mime ?: context.getString(R.string.data_sticker_import_format_unspecified),
                )
            )
        }
        if (animated) {
            return processAnimated(packId, source, index, bounds.outWidth, bounds.outHeight)
        }
        return processStill(packId, source, index)
    }

    @Suppress("DEPRECATION")
    private fun processStill(packId: String, source: File, index: Int): StickerImportItem {
        val stickerId = newStickerId(index)
        // 统一走 ImageCompressor：两段解码 + EXIF 摆正 + 长边 512（B-17）。
        // 表情包保留透明通道，编码仍是 WEBP，路径与命名规则不变。
        val bitmap = imageCompressor.decodeUpright(source.path, MAX_STATIC_EDGE)
            ?: throw IllegalStateException(context.getString(R.string.data_sticker_import_decode_failed))
        val file = StickerPaths.stickerFile(context, packId, stickerId, "webp")
        var bytes = 0L
        // 体积超限时按 88 → 70 → 55 逐级降质重编码（执行书 7.4.3）
        for (quality in intArrayOf(88, 70, 55)) {
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out) }
            bytes = file.length()
            if (bytes <= MAX_STATIC_BYTES) break
        }
        val resultWidth = bitmap.width
        val resultHeight = bitmap.height
        if (bytes > MAX_STATIC_BYTES) {
            bitmap.recycle()
            file.delete()
            throw IllegalStateException(
                context.getString(
                    R.string.data_sticker_import_static_too_large,
                    (MAX_STATIC_BYTES / 1024 / 1024).toInt(),
                )
            )
        }
        val thumb = writeThumbnail(packId, stickerId, bitmap)
        bitmap.recycle()
        return StickerImportItem(
            stickerId = stickerId,
            name = InjectionGuard.sanitizeForeign(source.nameWithoutExtension, PromptZone.STICKER_NAME)
                .take(10)
                .ifBlank { "表情$index" },
            filePath = file.path,
            thumbnailPath = thumb,
            mime = MIME_WEBP,
            width = resultWidth,
            height = resultHeight,
            bytes = bytes,
            animated = false,
        )
    }

    @Suppress("DEPRECATION")
    private fun processAnimated(packId: String, source: File, index: Int, width: Int, height: Int): StickerImportItem {
        if (max(width, height) > MAX_ANIMATED_EDGE) {
            throw IllegalStateException(
                context.getString(R.string.data_sticker_import_animated_edge, MAX_ANIMATED_EDGE)
            )
        }
        if (source.length() > MAX_ANIMATED_BYTES) {
            throw IllegalStateException(
                context.getString(
                    R.string.data_sticker_import_animated_too_large,
                    (MAX_ANIMATED_BYTES / 1024 / 1024).toInt(),
                )
            )
        }
        val duration = runCatching { Movie.decodeFile(source.path)?.duration() ?: 0 }.getOrDefault(0)
        if (duration > MAX_GIF_DURATION_MS) {
            throw IllegalStateException(
                context.getString(
                    R.string.data_sticker_import_animated_too_long,
                    MAX_GIF_DURATION_MS / 1000,
                )
            )
        }
        val stickerId = newStickerId(index)
        val target = StickerPaths.stickerFile(context, packId, stickerId, "gif")
        source.copyTo(target, overwrite = true)
        val thumbBitmap = decodeScaled(source.path, THUMB_EDGE)
        val thumb = thumbBitmap?.let { writeThumbnail(packId, stickerId, it) }
        thumbBitmap?.recycle()
        return StickerImportItem(
            stickerId = stickerId,
            name = InjectionGuard.sanitizeForeign(source.nameWithoutExtension, PromptZone.STICKER_NAME)
                .take(10)
                .ifBlank { "表情$index" },
            filePath = target.path,
            thumbnailPath = thumb,
            mime = MIME_GIF,
            width = width,
            height = height,
            bytes = target.length(),
            animated = true,
        )
    }

    @Suppress("DEPRECATION")
    private fun writeThumbnail(packId: String, stickerId: String, bitmap: Bitmap): String? = runCatching {
        val thumb = StickerPaths.thumbnailFor(StickerPaths.stickerFile(context, packId, stickerId, "webp"))
        val scaled = scaleToMaxEdge(bitmap, THUMB_EDGE)
        thumb.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.WEBP, THUMB_QUALITY, out) }
        if (scaled !== bitmap) scaled.recycle()
        thumb.takeIf { it.isFile && it.length() > 0 }?.path
    }.getOrNull()

    /** 只解到略大于目标边长，避免 4K 原图一次性吃满内存。 */
    private fun decodeScaled(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        return scaleToMaxEdge(decoded, maxEdge)
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun copyToTemp(uri: Uri, target: File): File? = runCatching {
        val input: InputStream = resolver.openInputStream(uri) ?: return null
        input.use { source -> target.outputStream().use { out -> source.copyTo(out) } }
        target.takeIf { it.isFile && it.length() > 0 }
    }.getOrNull()

    private fun isZip(file: File): Boolean {
        if (file.extension.equals("zip", true) || file.extension.equals("xystickers", true)) return true
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header)
                header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun isSupportedExtension(name: String): Boolean =
        SUPPORTED_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    private fun normalizeMime(outMime: String?, fileName: String): String? = when {
        outMime != null && outMime.isNotBlank() -> outMime.lowercase()
        fileName.endsWith(".gif", true) -> MIME_GIF
        fileName.endsWith(".webp", true) -> MIME_WEBP
        fileName.endsWith(".png", true) -> "image/png"
        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
        else -> null
    }

    private fun newStickerId(index: Int): String =
        "st-${System.currentTimeMillis()}-$index-${UUID.randomUUID().toString().take(4)}"

    private companion object {
        /** 一次最多导入 30 张（执行书 7.4.3 入口 1）。 */
        const val MAX_FILES = 30
        const val MAX_STATIC_EDGE = ImageCompressor.STICKER_EDGE
        const val MAX_ANIMATED_EDGE = 480
        const val MAX_STATIC_BYTES = 1L * 1024 * 1024
        const val MAX_ANIMATED_BYTES = 2L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 30L * 1024 * 1024
        const val MAX_GIF_DURATION_MS = 8_000
        const val THUMB_EDGE = 128
        const val THUMB_QUALITY = 80

        const val MIME_GIF = "image/gif"
        const val MIME_WEBP = "image/webp"

        val SUPPORTED_MIMES = setOf(MIME_WEBP, MIME_GIF, "image/png", "image/jpeg")
        val SUPPORTED_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif")
    }
}
