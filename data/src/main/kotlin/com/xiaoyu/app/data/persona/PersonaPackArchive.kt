package com.xiaoyu.app.data.persona

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.xiaoyu.app.data.R
import com.xiaoyu.app.data.archive.SafeZip
import com.xiaoyu.app.data.archive.UnsafeArchiveException
import com.xiaoyu.app.data.db.IdentityDao
import com.xiaoyu.app.data.db.IdentityEntity
import com.xiaoyu.app.data.db.StyleDao
import com.xiaoyu.app.data.db.StyleEntity
import com.xiaoyu.app.data.prompt.PromptAssetSource
import com.xiaoyu.soul.safety.InjectionGuard
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `.xypersona` 人格包（执行书 11.6 / M7-09）。
 *
 * 包结构（v1）：
 * ```
 * manifest.json          # 格式版本 / 作者 / 许可证 / 清单
 * identity.md            # 身份设定（人格核心）
 * styles/01-xxx.md       # 可选的表达风格
 * prompts/NAME.md        # 可选：该人格的自定义模板（导入后不覆盖全局，见下）
 * avatar.png             # 可选头像
 * ```
 *
 * 导入策略：
 * - 身份按「同名 + 正文完全一致」去重，重复导入直接复用既有记录；
 * - 风格按「同名 + 正文一致」去重；
 * - 自定义模板**不会**静默覆盖全局模板，统一释放到 files/persona_prompts/<包名>/ 并提示用户手动应用；
 * - 身份正文超过 4000 字时先返回 requiresConfirmation，调用方确认后带 confirmLargeIdentity = true 重试。
 */
@Singleton
class PersonaPackArchive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityDao: IdentityDao,
    private val styleDao: StyleDao,
    private val promptSource: PromptAssetSource,
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
        val description: String = "",
        val minAppVersion: String = "1.0.0",
        @SerialName("identity_file") val identityFile: String = IDENTITY_NAME,
        /** 与执行书 11.6 一致：风格文件相对路径列表，如 styles/default.md */
        val styles: List<String> = emptyList(),
        val avatar: String? = null,
        val variables: Map<String, String> = emptyMap(),
        @SerialName("content_warning") val contentWarning: String = "无",
        val tags: List<String> = emptyList(),
        @SerialName("created_at") val createdAt: String = "",
    )

    data class ExportResult(val file: File, val bytes: Long)

    data class ImportResult(
        val identityId: Long?,
        val identityName: String,
        val styleIds: List<Long> = emptyList(),
        val promptFiles: List<String> = emptyList(),
        val avatarPath: String? = null,
        val skippedDuplicates: Int = 0,
        val requiresConfirmation: Boolean = false,
        val warnings: List<String> = emptyList(),
    )

    /** 导出人格包；默认写 cache/share，便于 FileProvider 分享。 */
    suspend fun export(
        identityId: Long,
        styleIds: List<Long> = emptyList(),
        author: String = "",
        license: String = "Unknown",
        description: String = "",
        contentWarning: String = "无",
        tags: List<String> = emptyList(),
        includePrompts: Boolean = true,
        target: File? = null,
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val identity = identityDao.byId(identityId)
                ?: error(context.getString(R.string.data_persona_archive_identity_not_found))
            val styles = if (styleIds.isEmpty()) {
                styleDao.all()
            } else {
                styleIds.mapNotNull { styleDao.byId(it) }
            }
            val promptOverrides = if (includePrompts) promptSource.userOverrides() else emptyMap()
            val styleFiles = styles.mapIndexed { index, style ->
                "styles/${(index + 1).toString().padStart(2, '0')}-${sanitize(style.name)}.md"
            }
            val manifest = Manifest(
                name = identity.name,
                author = author,
                license = license,
                description = description,
                contentWarning = contentWarning,
                tags = tags,
                createdAt = isoTime(System.currentTimeMillis()),
                styles = styleFiles,
            )
            val outFile = target ?: File(shareDir(), "xypersona-${sanitize(identity.name)}-${System.currentTimeMillis()}.zip")
            outFile.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(outFile).buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(json.encodeToString(manifest).toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(IDENTITY_NAME))
                zip.write(identity.contentMd.toByteArray())
                zip.closeEntry()

                styles.forEachIndexed { index, style ->
                    zip.putNextEntry(ZipEntry(styleFiles[index]))
                    zip.write(style.contentMd.toByteArray())
                    zip.closeEntry()
                }
                promptOverrides.forEach { (key, content) ->
                    zip.putNextEntry(ZipEntry("prompts/$key.md"))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
            ExportResult(outFile, outFile.length())
        }
    }

    suspend fun import(zipFile: File, confirmLargeIdentity: Boolean = false): Result<ImportResult> =
        withContext(Dispatchers.IO) {
            try {
                val zip = SafeZip.open(zipFile)
                zip.use {
                    val manifest = json.decodeFromString(
                        Manifest.serializer(),
                        SafeZip.readBytes(zip, MANIFEST_NAME, 256L * 1024).decodeToString(),
                    )
                    if (manifest.format !in 1..FORMAT_VERSION) {
                        error(
                            context.getString(
                                R.string.data_persona_archive_bad_version,
                                manifest.format,
                            )
                        )
                    }
                    if (manifest.name.isBlank()) {
                        error(context.getString(R.string.data_persona_archive_missing_name))
                    }

                    val identityText = SafeZip.readBytes(zip, SafeZip.validateName(manifest.identityFile), 1024L * 1024)
                        .decodeToString().trim()
                    if (identityText.isBlank()) {
                        error(context.getString(R.string.data_persona_archive_empty_identity))
                    }
                    // v3 §8.3 / H-7：人格包内容进 prompt 前清洗协议 token，注入模式需人工确认
                    val sanitizedIdentity = InjectionGuard.sanitize(identityText)
                        ?: error(context.getString(R.string.data_persona_archive_suspicious))
                    if (sanitizedIdentity.length > MAX_IDENTITY_CHARS && !confirmLargeIdentity) {
                        return@withContext Result.success(
                            ImportResult(
                                identityId = null,
                                identityName = manifest.name,
                                requiresConfirmation = true,
                                warnings = listOf(
                                    context.getString(
                                        R.string.data_persona_archive_large_identity,
                                        sanitizedIdentity.length,
                                        MAX_IDENTITY_CHARS,
                                    ),
                                ),
                            )
                        )
                    }

                    val now = System.currentTimeMillis()
                    val warnings = mutableListOf<String>()
                    var skipped = 0

                    val existingIdentity = identityDao.all().firstOrNull {
                        it.name == manifest.name && it.contentMd.trim() == sanitizedIdentity
                    }
                    val identityId = if (existingIdentity != null) {
                        skipped++
                        warnings += context.getString(R.string.data_persona_archive_identity_exists)
                        existingIdentity.id
                    } else {
                        identityDao.insert(
                            IdentityEntity(
                                name = manifest.name,
                                contentMd = sanitizedIdentity,
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                    }

                    val allStyles = styleDao.all()
                    val styleIds = mutableListOf<Long>()
                    manifest.styles.forEach { styleFile ->
                        val entryName = runCatching { SafeZip.validateName(styleFile) }.getOrNull() ?: return@forEach
                        val content = runCatching { SafeZip.readBytes(zip, entryName, 256L * 1024).decodeToString() }
                            .getOrNull()
                            ?.trim()
                        if (content.isNullOrBlank()) {
                            skipped++
                            return@forEach
                        }
                        val name = entryName.substringAfterLast('/')
                            .substringBeforeLast('.')
                            .replace(Regex("^\\d+-"), "")
                            .ifBlank { "导入风格" }
                        // v3 §8.3 / H-7：风格文本同样清洗协议 token
                        val sanitized = InjectionGuard.sanitize(content) ?: run {
                            skipped++
                            warnings += context.getString(
                                R.string.data_persona_archive_suspicious_style,
                                name,
                            )
                            return@forEach
                        }
                        val duplicate = allStyles.firstOrNull {
                            it.name == name && it.contentMd.trim() == sanitized
                        }
                        if (duplicate != null) {
                            skipped++
                            styleIds += duplicate.id
                        } else {
                            styleIds += styleDao.insert(
                                StyleEntity(name = name, contentMd = sanitized, createdAt = now, updatedAt = now)
                            )
                        }
                    }

                    val promptFiles = extractPrompts(zip, manifest.name)
                    val avatarPath = manifest.avatar?.let { path ->
                        runCatching { extractAvatar(zip, SafeZip.validateName(path), manifest.name) }.getOrNull()
                    }

                    Result.success(
                        ImportResult(
                            identityId = identityId,
                            identityName = manifest.name,
                            styleIds = styleIds,
                            promptFiles = promptFiles,
                            avatarPath = avatarPath,
                            skippedDuplicates = skipped,
                            warnings = warnings,
                        )
                    )
                }
            } catch (e: UnsafeArchiveException) {
                Result.failure(
                    IllegalArgumentException(
                        context.getString(R.string.data_import_failed, e.message),
                        e,
                    )
                )
            } catch (e: Exception) {
                Result.failure(
                    IllegalArgumentException(
                        context.getString(
                            R.string.data_import_failed,
                            e.message ?: context.getString(R.string.data_persona_archive_bad_pack),
                        ),
                        e,
                    )
                )
            }
        }

    /** 分享人格包（需要 FileProvider，authority 默认 applicationId + .fileprovider）。 */
    fun shareIntent(file: File, authority: String = "${context.packageName}.fileprovider"): Intent {
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 提示词只释放到独立目录，绝不静默改写全局模板。 */
    private fun extractPrompts(zip: java.util.zip.ZipFile, packName: String): List<String> {
        val targetDir = File(context.filesDir, "persona_prompts/${sanitize(packName)}").apply { mkdirs() }
        val written = mutableListOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val name = SafeZip.validateName(entry.name)
            if (!name.startsWith("prompts/") || !name.endsWith(".md")) continue
            val fileName = name.substringAfterLast('/')
            if (fileName.isBlank() || fileName.contains("..")) continue
            val dest = File(targetDir, fileName)
            zip.getInputStream(entry).use { input ->
                FileOutputStream(dest).use { output ->
                    SafeZip.copyLimited(input, output, 256L * 1024)
                }
            }
            written += dest.absolutePath
        }
        return written
    }

    private fun extractAvatar(zip: java.util.zip.ZipFile, entryName: String, packName: String): String {
        val ext = entryName.substringAfterLast('.', "png").lowercase().filter { it.isLetterOrDigit() }
        val dir = File(context.filesDir, "personas/${sanitize(packName)}").apply { mkdirs() }
        val dest = File(dir, "avatar.${ext.ifBlank { "png" }}")
        SafeZip.copyTo(zip, entryName, dir, dest, 4L * 1024 * 1024)
        return dest.absolutePath
    }

    private fun shareDir(): File = File(context.cacheDir, "share").apply { mkdirs() }

    private fun sanitize(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^\\p{L}\\p{N}._\\- ]"), "_")
            .trim('.')
            .take(40)
        return cleaned.ifBlank { "persona" }
    }

    private fun isoTime(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(time))

    companion object {
        const val FORMAT_VERSION = 1
        const val MANIFEST_NAME = "manifest.json"
        const val IDENTITY_NAME = "identity.md"
        const val MAX_IDENTITY_CHARS = 4000
    }
}
