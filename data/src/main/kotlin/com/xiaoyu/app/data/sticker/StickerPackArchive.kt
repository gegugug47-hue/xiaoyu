package com.xiaoyu.app.data.sticker

import android.content.Context
import android.graphics.BitmapFactory
import com.xiaoyu.app.data.archive.SafeZip
import com.xiaoyu.app.data.archive.UnsafeArchiveException
import com.xiaoyu.app.data.db.StickerArchiveDao
import com.xiaoyu.app.data.db.StickerEntity
import com.xiaoyu.app.data.db.StickerPackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `.xystickers` 表情包归档（执行书 7.4 / M7-07）。
 *
 * 包结构（v1）：
 * ```
 * manifest.json           # 元信息 + 表情清单
 * stickers/01.webp        # 文件按清单顺序编号
 * preview.png              # 可选封面
 * ```
 * 导入时所有路径都经过 SafeZip 校验，拒绝 `..`、绝对路径、盘符与超大文件；
 * 同名（同作者）的包会被视为已存在，重复导入直接返回既有包，不产生重复数据。
 */
@Singleton
class StickerPackArchive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: StickerArchiveDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class Manifest(
        val format: Int = FORMAT_VERSION,
        val name: String,
        val author: String = "",
        val version: String = "1.0.0",
        val license: String = "Unknown",
        @SerialName("source_url") val sourceUrl: String = "",
        @SerialName("created_at") val createdAt: String = "",
        val cover: String? = null,
        val stickers: List<Item> = emptyList(),
    )

    @Serializable
    data class Item(
        val file: String,
        val name: String = "",
        val tags: List<String> = emptyList(),
        @SerialName("mood_tags") val moodTags: List<String> = emptyList(),
        val mime: String = "image/webp",
    )

    data class ImportResult(
        val packId: String,
        val packName: String,
        val imported: Int,
        val skipped: Int,
        val warnings: List<String> = emptyList(),
        val alreadyExists: Boolean = false,
    )

    /** 导出表情包；默认写到 cache/share 下，便于 FileProvider 分享。 */
    suspend fun export(packId: String, target: File? = null): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val pack = dao.pack(packId) ?: error("表情包不存在")
            val stickers = dao.stickers(packId)
                .filter { it.filePath.isNotBlank() && !it.filePath.startsWith("builtin://") }
                .filter { File(it.filePath).isFile }
            if (stickers.isEmpty()) error("这个表情包没有可导出的图片文件")

            val items = stickers.mapIndexed { index, sticker ->
                Item(
                    file = "stickers/${(index + 1).toString().padStart(2, '0')}.${extensionOf(sticker.filePath, sticker.mime)}",
                    name = sticker.name,
                    tags = sticker.tags.csvToList(),
                    moodTags = sticker.moodTags.csvToList(),
                    mime = sticker.mime,
                )
            }
            val outFile = target ?: File(shareDir(), "xystickers-${sanitize(pack.name)}-${pack.id.take(8)}.zip")
            outFile.parentFile?.mkdirs()

            var total = 0L
            ZipOutputStream(FileOutputStream(outFile).buffered()).use { zipOut ->
                val coverSticker = stickers.firstOrNull { it.id == pack.coverStickerId } ?: stickers.first()
                val manifest = Manifest(
                    name = pack.name,
                    author = pack.author.orEmpty(),
                    createdAt = isoTime(pack.createdAt),
                    stickers = items,
                )
                zipOut.putNextEntry(ZipEntry(MANIFEST_NAME))
                zipOut.write(json.encodeToString(manifest).toByteArray())
                zipOut.closeEntry()

                // 封面按规范固定叫 preview.png（内容沿用封面表情的原始字节）
                val coverBytes = File(coverSticker.filePath).readBytes()
                total += coverBytes.size
                zipOut.putNextEntry(ZipEntry(PREVIEW_NAME))
                zipOut.write(coverBytes)
                zipOut.closeEntry()

                stickers.forEachIndexed { index, sticker ->
                    val bytes = File(sticker.filePath).readBytes()
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) error("表情包体积超过 30MB，建议拆包后再导出")
                    zipOut.putNextEntry(ZipEntry(items[index].file))
                    zipOut.write(bytes)
                    zipOut.closeEntry()
                }
            }
            outFile
        }
    }

    /** 导入 `.xystickers`。 */
    suspend fun import(zipFile: File): Result<ImportResult> = withContext(Dispatchers.IO) {
        var packDir: File? = null
        try {
            val zip = SafeZip.open(zipFile)
            zip.use {
                val manifestBytes = SafeZip.readBytes(zip, MANIFEST_NAME, 256L * 1024)
                val manifest = json.decodeFromString(Manifest.serializer(), manifestBytes.decodeToString())
                validate(manifest)

                val existing = dao.packByName(manifest.name.trim())
                if (existing != null && existing.author.orEmpty() == manifest.author) {
                    return@withContext Result.success(
                        ImportResult(
                            packId = existing.id,
                            packName = existing.name,
                            imported = 0,
                            skipped = manifest.stickers.size,
                            warnings = listOf("已存在同名表情包，本次没有重复导入"),
                            alreadyExists = true,
                        )
                    )
                }

                val packId = "zip-${UUID.randomUUID().toString().take(8)}"
                val dir = File(stickersRoot(), packId).apply { mkdirs() }
                packDir = dir
                val now = System.currentTimeMillis()
                val entities = mutableListOf<StickerEntity>()
                val warnings = mutableListOf<String>()
                if (manifest.license.equals("Unknown", ignoreCase = true) || manifest.license.isBlank()) {
                    warnings += "这个包没有声明许可证（license=Unknown），使用前请自行确认版权"
                }
                var skipped = 0
                var totalBytes = 0L
                manifest.stickers.forEachIndexed { index, item ->
                    val entryName = SafeZip.validateName(item.file)
                    if (zip.getEntry(entryName) == null) {
                        error("清单里的 ${item.file} 不存在，整包已拒绝")
                    }
                    val ext = extensionOf(entryName, item.mime)
                    if (ext !in ALLOWED_EXTENSIONS) {
                        error("${item.file} 格式不支持（仅支持 WEBP/PNG/JPG/GIF），整包已拒绝")
                    }
                    val fileName = "${(index + 1).toString().padStart(2, '0')}-${sanitize(item.name.ifBlank { "sticker" })}.$ext"
                    val dest = File(dir, fileName)
                    val size = SafeZip.copyTo(zip, entryName, dir, dest, SafeZip.MAX_SINGLE_BYTES)
                    totalBytes += size
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        error("表情包解压后超过 30MB，整包已拒绝")
                    }
                    val (width, height) = bitmapSize(dest)
                    entities += StickerEntity(
                        id = UUID.randomUUID().toString(),
                        packId = packId,
                        name = item.name.ifBlank { fileName },
                        tags = item.tags.joinToString(","),
                        moodTags = item.moodTags.joinToString(","),
                        filePath = dest.absolutePath,
                        mime = mimeOf(ext),
                        width = width,
                        height = height,
                        bytes = size,
                        sort = index,
                        enabled = true,
                        createdAt = now,
                    )
                }
                if (entities.isEmpty()) error("压缩包里没有可用的表情图片")

                val sort = (dao.maxPackSort() ?: 0) + 1
                val coverId = entities.first().id
                val pack = StickerPackEntity(
                    id = packId,
                    name = manifest.name.trim(),
                    author = manifest.author.ifBlank { null },
                    source = "zip",
                    coverStickerId = coverId,
                    sort = sort,
                    enabled = true,
                    isBuiltin = false,
                    dirPath = dir.absolutePath,
                    createdAt = now,
                    updatedAt = now,
                )
                dao.insertPack(pack)
                dao.insertStickers(entities)
                Result.success(
                    ImportResult(
                        packId = packId,
                        packName = pack.name,
                        imported = entities.size,
                        skipped = skipped,
                        warnings = warnings,
                    )
                )
            }
        } catch (e: UnsafeArchiveException) {
            packDir?.deleteRecursively()
            Result.failure(IllegalArgumentException("导入失败：${e.message}", e))
        } catch (e: Exception) {
            packDir?.deleteRecursively()
            Result.failure(IllegalArgumentException("导入失败：${e.message ?: "压缩包格式不正确"}", e))
        }
    }

    /**
     * Telegram 静态表情批量导入（WEBP / PNG / JPG / GIF）。
     * Telegram 的 .webm 视频表情、.tgs 动态表情暂不支持，会在 warnings 里说明并跳过。
     */
    suspend fun importTelegram(
        files: List<File>,
        packName: String,
        author: String? = null,
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        var packDir: File? = null
        try {
            if (packName.isBlank()) error("表情包名字不能为空")
            if (files.isEmpty()) error("没有选择文件")
            val now = System.currentTimeMillis()
            val packId = "tg-${UUID.randomUUID().toString().take(8)}"
            val uniqueName = uniquePackName(packName.trim())
            val dir = File(stickersRoot(), packId).apply { mkdirs() }
            packDir = dir
            val entities = mutableListOf<StickerEntity>()
            val warnings = mutableListOf<String>()
            var skipped = 0
            var total = 0L
            files.take(MAX_STICKERS).forEachIndexed { index, file ->
                val ext = file.extension.lowercase()
                when {
                    !file.isFile -> {
                        skipped++
                    }
                    ext == "webm" -> {
                        skipped++
                        warnings += "${file.name} 是视频表情（WEBM），暂不支持"
                    }
                    ext == "tgs" -> {
                        skipped++
                        warnings += "${file.name} 是动态表情（TGS），暂不支持"
                    }
                    ext !in ALLOWED_EXTENSIONS -> {
                        skipped++
                        warnings += "${file.name} 格式不支持，已跳过"
                    }
                    file.length() > SafeZip.MAX_SINGLE_BYTES -> {
                        skipped++
                        warnings += "${file.name} 超过 16MB，已跳过"
                    }
                    total + file.length() > MAX_TOTAL_BYTES -> {
                        skipped++
                        warnings += "已达 30MB 上限，剩余文件跳过"
                    }
                    else -> {
                        total += file.length()
                        val name = "${(index + 1).toString().padStart(2, '0')}-${sanitize(file.nameWithoutExtension)}.$ext"
                        val dest = File(dir, name)
                        file.copyTo(dest, overwrite = true)
                        val size = dest.length()
                        val (width, height) = bitmapSize(dest)
                        entities += StickerEntity(
                            id = UUID.randomUUID().toString(),
                            packId = packId,
                            name = file.nameWithoutExtension,
                            tags = "",
                            moodTags = "",
                            filePath = dest.absolutePath,
                            mime = mimeOf(ext),
                            width = width,
                            height = height,
                            bytes = size,
                            sort = index,
                            enabled = true,
                            createdAt = now,
                        )
                    }
                }
            }
            if (files.size > MAX_STICKERS) {
                warnings += "一次最多导入 $MAX_STICKERS 张，超出部分已忽略"
            }
            if (entities.isEmpty()) error("没有可导入的静态表情图片")

            val pack = StickerPackEntity(
                id = packId,
                name = uniqueName,
                author = author?.takeIf { it.isNotBlank() },
                source = "telegram",
                coverStickerId = entities.first().id,
                sort = (dao.maxPackSort() ?: 0) + 1,
                enabled = true,
                isBuiltin = false,
                dirPath = dir.absolutePath,
                createdAt = now,
                updatedAt = now,
            )
            dao.insertPack(pack)
            dao.insertStickers(entities)
            Result.success(
                ImportResult(
                    packId = packId,
                    packName = uniqueName,
                    imported = entities.size,
                    skipped = skipped,
                    warnings = warnings,
                )
            )
        } catch (e: Exception) {
            packDir?.deleteRecursively()
            Result.failure(IllegalArgumentException("导入失败：${e.message ?: "文件读写异常"}", e))
        }
    }

    /** 分享导出的包（FileProvider，Provider 声明见交付说明）。 */
    fun shareIntent(file: File): android.content.Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun validate(manifest: Manifest) {
        if (manifest.format !in 1..FORMAT_VERSION) error("不支持的包格式版本：${manifest.format}")
        if (manifest.name.isBlank() || manifest.name.length > 32) error("表情包名字不合法（1~32 字）")
        if (manifest.stickers.isEmpty()) error("表情包里没有表情")
        if (manifest.stickers.size > MAX_STICKERS) error("表情数量超过 $MAX_STICKERS 张")
    }

    private suspend fun uniquePackName(base: String): String {
        val packs = runCatching { dao.packs() }.getOrDefault(emptyList())
        if (packs.none { it.name == base }) return base
        var index = 2
        while (packs.any { it.name == "$base ($index)" }) index++
        return "$base ($index)"
    }

    private fun stickersRoot(): File = File(context.filesDir, "stickers").apply { mkdirs() }

    private fun shareDir(): File = File(context.cacheDir, "share").apply { mkdirs() }

    private fun isoTime(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(time))

    private fun String.csvToList(): List<String> =
        split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

    private fun extensionOf(pathOrName: String, mime: String): String {
        val ext = pathOrName.substringAfterLast('.', "").lowercase()
        if (ext in ALLOWED_EXTENSIONS) return ext
        return when (mime.lowercase()) {
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> "webp"
        }
    }

    private fun mimeOf(ext: String): String = when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        else -> "image/webp"
    }

    private fun sanitize(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            .trim('.')
            .take(40)
        return cleaned.ifBlank { "sticker" }
    }

    private fun bitmapSize(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }
        return options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val MANIFEST_NAME = "manifest.json"
        const val PREVIEW_NAME = "preview.png"
        const val MAX_STICKERS = 100
        const val MAX_TOTAL_BYTES = 30L * 1024 * 1024
        private val ALLOWED_EXTENSIONS = setOf("webp", "png", "jpg", "jpeg", "gif")
    }
}
