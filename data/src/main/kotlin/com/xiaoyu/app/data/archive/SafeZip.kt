package com.xiaoyu.app.data.archive

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** 归档文件结构不安全（Zip Slip / 超大包 / 条目过多）时抛出，调用方转成可读提示。 */
class UnsafeArchiveException(message: String) : Exception(message)

/**
 * 安全的 ZIP 读取工具（M7-07 / M7-09 共用）。
 * 导入的包可能来自网络或聊天文件，必须假设它是恶意的：
 * - 拒绝 `..`、绝对路径、盘符、控制字符；
 * - 解压前先做 canonical path 校验，杜绝 Zip Slip；
 * - 限制条目数与单文件/总解压体积，防 zip bomb。
 */
object SafeZip {

    const val MAX_ENTRIES = 600
    const val MAX_TOTAL_BYTES = 40L * 1024 * 1024
    const val MAX_SINGLE_BYTES = 16L * 1024 * 1024

    fun open(file: File): ZipFile {
        if (!file.isFile || file.length() == 0L) {
            throw UnsafeArchiveException("压缩包不存在或为空")
        }
        val zip = ZipFile(file)
        var entries = 0
        var declared = 0L
        try {
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                validateName(entry.name)
                entries++
                if (entries > MAX_ENTRIES) throw UnsafeArchiveException("压缩包文件数量过多")
                if (!entry.isDirectory) {
                    if (entry.size > MAX_SINGLE_BYTES) throw UnsafeArchiveException("压缩包里有超大文件")
                    if (entry.size > 0) declared += entry.size
                }
            }
            if (declared > MAX_TOTAL_BYTES) throw UnsafeArchiveException("压缩包解压后体积过大")
            return zip
        } catch (e: Exception) {
            zip.close()
            throw e
        }
    }

    /** 校验单个条目名；返回规范化后的相对路径（正斜杠）。 */
    fun validateName(raw: String): String {
        if (raw.isBlank()) throw UnsafeArchiveException("压缩包里有空文件名")
        if (raw.contains('\u0000')) throw UnsafeArchiveException("压缩包文件名包含非法字符")
        val name = raw.replace('\\', '/')
        if (name.startsWith("/")) throw UnsafeArchiveException("压缩包包含绝对路径")
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) throw UnsafeArchiveException("压缩包包含盘符路径")
        val segments = name.split('/')
        if (segments.any { it == ".." }) throw UnsafeArchiveException("压缩包包含越界路径")
        if (segments.any { it.isEmpty() && name != "/" } && name.contains("//")) {
            throw UnsafeArchiveException("压缩包路径格式不正确")
        }
        return name
    }

    /** 把条目安全地解析到 root 之下，解析后必须仍在 root 内。 */
    fun resolve(root: File, entryName: String): File {
        val relative = validateName(entryName)
        val rootPath = root.canonicalFile
        val target = File(rootPath, relative).canonicalFile
        val rootPrefix = rootPath.path + File.separator
        if (!target.path.startsWith(rootPrefix) && target.path != rootPath.path) {
            throw UnsafeArchiveException("压缩包包含越界路径")
        }
        return target
    }

    fun readBytes(zip: ZipFile, name: String, maxBytes: Long = MAX_SINGLE_BYTES): ByteArray {
        val entry = zip.getEntry(name) ?: throw UnsafeArchiveException("压缩包里缺少 $name")
        if (entry.isDirectory) throw UnsafeArchiveException("$name 不是文件")
        return zip.getInputStream(entry).use { readLimited(it, maxBytes) }
    }

    fun readOptionalBytes(zip: ZipFile, name: String, maxBytes: Long = MAX_SINGLE_BYTES): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        if (entry.isDirectory) return null
        return zip.getInputStream(entry).use { readLimited(it, maxBytes) }
    }

    fun copyTo(
        zip: ZipFile,
        entryName: String,
        root: File,
        target: File,
        maxBytes: Long = MAX_SINGLE_BYTES,
    ): Long {
        val entry = zip.getEntry(entryName) ?: throw UnsafeArchiveException("压缩包里缺少 $entryName")
        if (entry.isDirectory) throw UnsafeArchiveException("$entryName 不是文件")
        // 即使调用方传错 target，也先做一次边界校验
        resolve(root, entryName)
        target.parentFile?.mkdirs()
        return zip.getInputStream(entry).use { input ->
            FileOutputStream(target).use { output -> copyLimited(input, output, maxBytes) }
        }
    }

    fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        copyLimited(input, out, maxBytes)
        return out.toByteArray()
    }

    fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) throw UnsafeArchiveException("压缩包里的文件过大")
            output.write(buffer, 0, read)
        }
        return total
    }
}
