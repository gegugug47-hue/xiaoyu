package com.xiaoyu.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.RoomDatabase
import com.xiaoyu.app.data.db.XiaoyuDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地备份/恢复（PRD 11.3）：
 * 导出 .xiaoyu（zip）＝ 数据库 + 用户改过的提示词；恢复采用“下次启动前替换”，避免运行中损坏数据库。
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: XiaoyuDatabase,
) {
    private val dbFile: File get() = context.getDatabasePath(DB_NAME)
    private val promptDir: File get() = File(context.filesDir, "prompts")
    fun backupDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }
    private fun pendingFile(): File = File(context.filesDir, "restore_pending.db")

    /**
     * 创建备份包（执行书 M5-01）。
     * 传入非空口令时，整个 zip 用 PBKDF2+AES-GCM 加密，产物以 "XYBK" 魔数开头；
     * 不传口令保持明文 zip，兼容旧版。
     */
    suspend fun createBackupFile(password: String? = null): File? = withContext(Dispatchers.IO) {
        runCatching {
            checkpoint()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
            val target = File(backupDir(), "xiaoyu-$stamp.xiaoyu")
            val payload = buildZipBytes()
            val out = if (password.isNullOrBlank()) payload else BackupCrypto.encrypt(payload, password)
            target.writeBytes(out)
            rotate()
            target
        }.getOrNull()
    }

    suspend fun exportTo(uri: Uri, password: String? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            checkpoint()
            val payload = buildZipBytes()
            val out = if (password.isNullOrBlank()) payload else BackupCrypto.encrypt(payload, password)
            context.contentResolver.openOutputStream(uri)?.use { it.write(out) } != null
        }.getOrDefault(false)
    }

    private fun buildZipBytes(): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        ZipOutputStream(bos.buffered()).use { zip ->
            if (dbFile.exists()) {
                zip.putNextEntry(ZipEntry(DB_NAME))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            promptDir.listFiles()?.filter { it.extension == "md" }?.forEach { file ->
                zip.putNextEntry(ZipEntry("prompts/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /**
     * 从备份恢复（M5-01 / M5-02）。若备份以 "XYBK" 开头，视为加密包，
     * 必须用正确口令解密；口令错误返回 false（由调用方提示）。
     */
    suspend fun importFrom(uri: Uri, password: String? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching false
            val payload = if (raw.copyOfRange(0, BackupCrypto.MAGIC.length)
                    .contentEquals(BackupCrypto.MAGIC.toByteArray(Charsets.UTF_8))
            ) {
                BackupCrypto.decrypt(raw, password ?: "") ?: return@runCatching false
            } else raw
            var foundDb = false
            ZipInputStream(payload.inputStream().buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == DB_NAME -> {
                            pendingFile().outputStream().use { out -> zip.copyTo(out) }
                            foundDb = true
                        }
                        entry.name.startsWith("prompts/") && entry.name.endsWith(".md") -> {
                            if (!promptDir.exists()) promptDir.mkdirs()
                            File(promptDir, File(entry.name).name).outputStream().use { out -> zip.copyTo(out) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            foundDb
        }.getOrDefault(false)
    }

    fun listBackups(): List<File> =
        backupDir().listFiles { file -> file.extension == "xiaoyu" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private fun rotate() {
        listBackups().drop(KEEP_COUNT).forEach { it.delete() }
    }

    private fun checkpoint() {
        runCatching {
            db.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        }
    }

    companion object {
        const val DB_NAME = "xiaoyu.db"
        const val KEEP_COUNT = 4
        const val PENDING_NAME = "restore_pending.db"
    }
}
