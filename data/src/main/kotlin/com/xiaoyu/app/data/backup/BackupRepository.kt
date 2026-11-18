package com.xiaoyu.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
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
            BackgroundBackup.addOriginals(context.filesDir, zip)
        }
        return bos.toByteArray()
    }

    /**
     * 从备份恢复（M5-01 / M5-02）。若备份以 "XYBK" 开头，视为加密包，
     * 必须用正确口令解密；口令错误返回 false（由调用方提示）。
     */
    suspend fun importFrom(uri: Uri, password: String? = null): Boolean = withContext(Dispatchers.IO) {
        val pending = pendingFile()
        // 解包到临时文件，校验通过后才原子改名为 pending；失败只清临时文件，
        // 绝不留下未校验的 pending 在下次启动顶掉正式库。
        val temp = File(context.filesDir, "$PENDING_NAME.tmp")
        runCatching {
            temp.delete()
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching false
            val payload = if (raw.size >= BackupCrypto.MAGIC.length &&
                raw.copyOfRange(0, BackupCrypto.MAGIC.length)
                    .contentEquals(BackupCrypto.MAGIC.toByteArray(Charsets.UTF_8))
            ) {
                BackupCrypto.decrypt(raw, password ?: "") ?: return@runCatching false
            } else raw
            var foundDb = false
            val prompts = linkedMapOf<String, ByteArray>()
            val backgrounds = linkedMapOf<String, ByteArray>()
            ZipInputStream(payload.inputStream().buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == DB_NAME -> {
                            temp.outputStream().use { out -> zip.copyTo(out) }
                            foundDb = true
                        }
                        entry.name.startsWith("prompts/") && entry.name.endsWith(".md") -> {
                            prompts[File(entry.name).name] = zip.readBytes()
                        }
                        else -> BackgroundBackup.fileName(entry.name)?.let { name ->
                            backgrounds[name] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (!foundDb || !isUsableDatabase(temp)) {
                Log.w(TAG, "备份导入校验失败，已丢弃暂存库")
                return@runCatching false
            }
            if (!temp.renameTo(pending)) {
                temp.copyTo(pending, overwrite = true)
                temp.delete()
            }
            // 数据库确认可用后再写入提示词和背景，避免半途失败留下不一致状态
            if (prompts.isNotEmpty()) {
                if (!promptDir.exists()) promptDir.mkdirs()
                prompts.forEach { (name, bytes) ->
                    runCatching { File(promptDir, name).writeBytes(bytes) }
                        .onFailure { Log.w(TAG, "恢复提示词失败：$name", it) }
                }
            }
            BackgroundBackup.restore(context.filesDir, backgrounds)
            true
        }.getOrElse {
            runCatching { temp.delete() }
            Log.w(TAG, "备份导入失败，已清理未校验的暂存文件", it)
            false
        }
    }

    /** 暂存库自检：quick_check 通过且含核心表才允许替换 pending。 */
    private fun isUsableDatabase(file: File): Boolean = runCatching {
        if (!file.exists() || file.length() <= 0L) return@runCatching false
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            val quick = sqlite.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (quick == null || !quick.equals("ok", ignoreCase = true)) return@use false
            val tables = mutableSetOf<String>()
            sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            "messages" in tables && "sessions" in tables
        }
    }.getOrElse {
        Log.w(TAG, "暂存备份库无法打开", it)
        false
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
        private const val TAG = "BackupRepository"
        const val DB_NAME = "xiaoyu.db"
        const val KEEP_COUNT = 4
        const val PENDING_NAME = "restore_pending.db"
    }
}
