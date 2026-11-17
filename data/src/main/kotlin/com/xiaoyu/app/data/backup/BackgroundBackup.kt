package com.xiaoyu.app.data.backup

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 聊天背景原图在备份包中的统一读写；只包含 files/bg 下的 SHA-1 JPG，不包含 cache。 */
internal object BackgroundBackup {
    private const val ENTRY_PREFIX = "media/bg/"
    private val SAFE_NAME = Regex("^[0-9a-f]{40}\\.jpg$")

    fun addOriginals(filesDir: File, zip: ZipOutputStream) {
        File(filesDir, "bg").listFiles()
            ?.filter { it.isFile && SAFE_NAME.matches(it.name) }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                zip.putNextEntry(ZipEntry(ENTRY_PREFIX + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }

    /** 仅接受精确的 media/bg/<sha1>.jpg；子目录、缓存与其他文件名一律忽略。 */
    fun fileName(entryName: String): String? =
        entryName.removePrefix(ENTRY_PREFIX)
            .takeIf { entryName.startsWith(ENTRY_PREFIX) && SAFE_NAME.matches(it) }

    /** 恢复原图，不删除本机其他原图，也不触碰 cache。 */
    fun restore(filesDir: File, images: Map<String, ByteArray>) {
        if (images.isEmpty()) return
        val dir = File(filesDir, "bg").apply { mkdirs() }
        images.toSortedMap().forEach { (name, bytes) ->
            require(SAFE_NAME.matches(name)) { "invalid background file name" }
            val target = File(dir, name)
            val temp = File(dir, ".$name.restore")
            try {
                temp.writeBytes(bytes)
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                }
            } finally {
                temp.delete()
            }
        }
    }
}
