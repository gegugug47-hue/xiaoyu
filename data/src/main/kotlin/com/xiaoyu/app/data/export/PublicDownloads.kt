package com.xiaoyu.app.data.export

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 公共下载目录写入（执行书 M5-03 / M5-04）。
 *
 * Android 10+ 走 MediaStore（无需存储权限，卸载不丢）；
 * Android 9 及以下走公共 Download 目录，失败时退回应用外部目录，保证导出不因权限直接失败。
 */
@Singleton
class PublicDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 一个已写入公共目录的文件；[displayPath] 是可直接展示给用户的路径。 */
    data class SavedFile(
        val displayPath: String,
        val uri: Uri? = null,
        val file: File? = null,
    )

    /** 写入二进制文件到 `Download/<subDir>/<fileName>`。 */
    fun saveBytes(subDir: String, fileName: String, mime: String, bytes: ByteArray): SavedFile? {
        if (bytes.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(subDir, fileName, mime, bytes) ?: saveLegacy(subDir, fileName, bytes)
        } else {
            saveLegacy(subDir, fileName, bytes)
        }
    }

    /** 写入文本（UTF-8），用于 Markdown 导出。 */
    fun saveText(subDir: String, fileName: String, mime: String, text: String): SavedFile? =
        saveBytes(subDir, fileName, mime, text.toByteArray(Charsets.UTF_8))

    /** 列出某目录下指定后缀的文件，按修改时间新的在前。 */
    fun list(subDir: String, suffix: String): List<SavedFile> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listViaMediaStore(subDir, suffix).ifEmpty { listLegacy(subDir, suffix) }
        } else {
            listLegacy(subDir, suffix)
        }

    /** 只保留最近 [keep] 份，超出部分删除（M5-03 保留策略）。 */
    fun prune(subDir: String, suffix: String, keep: Int) {
        if (keep < 0) return
        list(subDir, suffix).drop(keep).forEach { delete(it) }
    }

    fun delete(entry: SavedFile) {
        val uri = entry.uri
        if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return
        }
        runCatching { entry.file?.delete() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(subDir: String, fileName: String, mime: String, bytes: ByteArray): SavedFile? {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$subDir/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }
            .getOrNull() ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        val clear = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { resolver.update(uri, clear, null, null) }
        val actualName = queryDisplayName(uri) ?: fileName
        return SavedFile(displayPath = "$relativePath$actualName", uri = uri)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listViaMediaStore(subDir: String, suffix: String): List<SavedFile> = runCatching {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$subDir/", "%$suffix")
        val result = mutableListOf<SavedFile>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                val path = cursor.getString(pathIndex) ?: "${Environment.DIRECTORY_DOWNLOADS}/$subDir/"
                result += SavedFile(
                    displayPath = "$path$name",
                    uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                )
            }
        }
        result
    }.getOrDefault(emptyList())

    private fun saveLegacy(subDir: String, fileName: String, bytes: ByteArray): SavedFile? {
        val publicDir = publicLegacyDir(subDir)
        val saved = runCatching {
            if (!publicDir.exists()) publicDir.mkdirs()
            val target = File(publicDir, fileName)
            target.writeBytes(bytes)
            SavedFile(displayPath = "Download/$subDir/${target.name}", file = target)
        }.getOrNull()
        if (saved != null) return saved
        // 没有存储权限时退回应用外部目录，至少保证文件能导出。
        return runCatching {
            val fallback = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                subDir,
            )
            if (!fallback.exists()) fallback.mkdirs()
            val target = File(fallback, fileName)
            target.writeBytes(bytes)
            SavedFile(displayPath = target.absolutePath, file = target)
        }.getOrNull()
    }

    private fun listLegacy(subDir: String, suffix: String): List<SavedFile> = runCatching {
        val directories = listOf(
            publicLegacyDir(subDir),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, subDir),
        )
        directories.flatMap { directory ->
            directory.listFiles { file -> file.isFile && file.name.endsWith(suffix) }?.toList() ?: emptyList()
        }.sortedByDescending { it.lastModified() }.map { file ->
            val display = if (file.absolutePath.contains("/Download/")) {
                "Download/$subDir/${file.name}"
            } else {
                file.absolutePath
            }
            SavedFile(displayPath = display, file = file)
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun publicLegacyDir(subDir: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subDir)
}
