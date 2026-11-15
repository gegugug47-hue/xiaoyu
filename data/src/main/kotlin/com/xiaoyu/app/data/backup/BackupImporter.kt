package com.xiaoyu.app.data.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaoyu.app.data.db.XiaoyuDatabase
import com.xiaoyu.app.data.system.StorageGuard
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
 * 备份导入与回滚（执行书 M5-02）。
 *
 * 流程：读文件头校验格式/版本 → 口令解密 → 解出数据库到临时文件 → SQLite 自检
 * → 生成 pre-import 快照 → 按策略落库（覆盖=下次启动原子替换；合并=按主键去重插入）。
 * 任何一步失败都不会改动当前数据库。
 */
@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: XiaoyuDatabase,
    private val repository: BackupRepository,
    private val storageGuard: StorageGuard,
) {

    /** 冲突策略：覆盖=整体替换数据库；合并=保留现有数据，按 id 去重插入备份里的行。 */
    enum class Mode { OVERWRITE, MERGE }

    /** 导入进度，便于 UI 展示（回调在 IO 线程触发）。 */
    sealed interface Progress {
        data object Reading : Progress
        data object Decrypting : Progress
        data object Verifying : Progress
        data object Snapshotting : Progress
        data object Writing : Progress
        data class Merging(val table: String? = null) : Progress
        data object Done : Progress
    }

    enum class FailureReason {
        INVALID_FORMAT,
        VERSION_TOO_NEW,
        WRONG_PASSWORD,
        NO_DATABASE,
        CORRUPT_DATABASE,
        STORAGE_LOW,
        SNAPSHOT_FAILED,
        MERGE_FAILED,
        IO_ERROR,
    }

    sealed interface ImportResult {
        data class Success(
            val mode: Mode,
            /** 覆盖模式需要重启 App 才能生效（避免运行中替换 SQLite 文件）。 */
            val restartRequired: Boolean,
            /** 导入前自动生成的快照，失败时可用它回滚。 */
            val preImportSnapshot: File?,
            /** 合并模式实际插入的行数。 */
            val mergedRows: Int,
        ) : ImportResult

        data class Failure(val reason: FailureReason, val message: String) : ImportResult
    }

    /**
     * 从 [uri] 导入备份。[password] 仅加密包需要；
     * [mode] 为合并时立即写库，为覆盖时先暂存、下次启动原子替换。
     */
    suspend fun importFrom(
        uri: Uri,
        password: String? = null,
        mode: Mode = Mode.OVERWRITE,
        onProgress: (Progress) -> Unit = {},
    ): ImportResult = withContext(Dispatchers.IO) {
        val staging = stagingFile()
        try {
            importInternal(uri, password, mode, onProgress, staging)
        } finally {
            runCatching { staging.delete() }
            runCatching { File(staging.path + "-wal").delete() }
            runCatching { File(staging.path + "-shm").delete() }
        }
    }

    private fun importInternal(
        uri: Uri,
        password: String?,
        mode: Mode,
        onProgress: (Progress) -> Unit,
        staging: File,
    ): ImportResult {
        onProgress(Progress.Reading)
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return failure(FailureReason.IO_ERROR, "无法读取备份文件，请确认文件仍然存在。")

        val header = BackupCrypto.peekHeader(raw)
            ?: return failure(FailureReason.INVALID_FORMAT, "不是有效的备份文件（文件头过短）。")

        val payload: ByteArray
        if (header.encrypted) {
            if (header.version > BackupCrypto.FORMAT_VERSION) {
                return versionTooNew(header.version)
            }
            if (header.version <= 0) {
                return failure(FailureReason.INVALID_FORMAT, "备份文件头损坏，无法识别版本。")
            }
            if (password.isNullOrEmpty()) {
                return failure(FailureReason.WRONG_PASSWORD, "该备份已加密，请输入口令后再恢复。")
            }
            onProgress(Progress.Decrypting)
            payload = BackupCrypto.decrypt(raw, password)
                ?: return failure(FailureReason.WRONG_PASSWORD, "口令不对，或备份文件已损坏。")
        } else {
            payload = raw
        }

        if (!isZip(payload)) {
            return failure(FailureReason.INVALID_FORMAT, "不是有效的备份文件（zip 结构损坏）。")
        }

        onProgress(Progress.Verifying)
        var foundDb = false
        var manifestVersion = 0
        val prompts = linkedMapOf<String, ByteArray>()
        runCatching {
            ZipInputStream(payload.inputStream().buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    // 防 Zip Slip：任何含 .. 的条目直接拒绝整包。
                    if (name.contains("..")) throw IllegalArgumentException("zip slip")
                    when {
                        name == BackupRepository.DB_NAME -> {
                            staging.parentFile?.mkdirs()
                            staging.outputStream().use { out -> zip.copyTo(out) }
                            foundDb = true
                        }
                        name == MANIFEST_NAME -> {
                            manifestVersion = parseManifestVersion(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        name.startsWith("prompts/") && name.endsWith(".md") -> {
                            val fileName = File(name).name
                            if (fileName.isNotBlank()) prompts[fileName] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }.onFailure {
            Log.w(TAG, "解包备份失败", it)
            return failure(FailureReason.INVALID_FORMAT, "备份包损坏，无法解压：${it.message ?: "未知错误"}")
        }

        if (manifestVersion > BackupCrypto.FORMAT_VERSION) {
            return versionTooNew(manifestVersion)
        }
        if (!foundDb) {
            return failure(FailureReason.NO_DATABASE, "备份包里没有数据库文件，可能不是小鱼生成的备份。")
        }

        val stagedDb = inspectStagedDatabase(staging)
            ?: return failure(FailureReason.CORRUPT_DATABASE, "备份中的数据库已损坏，无法导入（原数据未改动）。")

        val currentVersion = currentDbVersion()
        if (currentVersion > 0 && stagedDb.version > currentVersion) {
            return failure(
                FailureReason.VERSION_TOO_NEW,
                "备份来自更新版本的 App（数据版本 v${stagedDb.version}，当前仅支持 v$currentVersion），" +
                    "请先升级 App 后再恢复。",
            )
        }

        if (storageGuard.isLow(staging.length())) {
            return failure(FailureReason.STORAGE_LOW, storageGuard.lowSpaceMessage(staging.length()))
        }

        onProgress(Progress.Snapshotting)
        val snapshot = createPreImportSnapshot()
            ?: return failure(FailureReason.SNAPSHOT_FAILED, "无法创建导入前快照，已取消导入（原数据未改动）。")

        return when (mode) {
            Mode.OVERWRITE -> {
                val stageFailure = stageForNextBoot(staging, prompts, onProgress)
                stageFailure ?: ImportResult.Success(
                    mode = Mode.OVERWRITE,
                    restartRequired = true,
                    preImportSnapshot = snapshot,
                    mergedRows = 0,
                )
            }

            Mode.MERGE -> {
                onProgress(Progress.Merging())
                val merged = runCatching { mergeFrom(staging, onProgress) }.getOrElse {
                    Log.e(TAG, "合并导入失败", it)
                    return failure(
                        FailureReason.MERGE_FAILED,
                        "合并导入失败，已回滚（原数据未改动）：${it.message ?: "未知错误"}",
                    )
                }
                applyPrompts(prompts)
                onProgress(Progress.Done)
                Log.i(TAG, "合并导入完成：新增 $merged 行")
                ImportResult.Success(
                    mode = Mode.MERGE,
                    restartRequired = false,
                    preImportSnapshot = snapshot,
                    mergedRows = merged,
                )
            }
        }
    }

    /** 覆盖模式：校验过的库先落到 pending，由下一次启动替换，运行中的 Room 不受影响。 */
    private fun stageForNextBoot(
        staging: File,
        prompts: Map<String, ByteArray>,
        onProgress: (Progress) -> Unit,
    ): ImportResult.Failure? = runCatching {
        onProgress(Progress.Writing)
        val pending = File(context.filesDir, BackupRepository.PENDING_NAME)
        // 先写临时文件再原子改名，避免进程被杀时留下半个数据库被下次启动误用。
        val temp = File(context.filesDir, "${BackupRepository.PENDING_NAME}.tmp")
        staging.copyTo(temp, overwrite = true)
        if (!temp.renameTo(pending)) {
            temp.copyTo(pending, overwrite = true)
            temp.delete()
        }
        applyPrompts(prompts)
        onProgress(Progress.Done)
        Log.i(TAG, "覆盖导入已暂存，重启后生效：${pending.name}")
        null
    }.getOrElse {
        Log.e(TAG, "写入待恢复数据库失败", it)
        failure(FailureReason.IO_ERROR, "写入待恢复文件失败：${it.message ?: "未知错误"}")
    }

    private fun applyPrompts(prompts: Map<String, ByteArray>) {
        if (prompts.isEmpty()) return
        val dir = File(context.filesDir, "prompts")
        dir.mkdirs()
        prompts.forEach { (name, bytes) ->
            runCatching { File(dir, File(name).name).writeBytes(bytes) }
                .onFailure { Log.w(TAG, "恢复提示词失败：$name", it) }
        }
    }

    /** 生成导入前快照（zip 明文，扩展名 .xybak，避免混进手动备份列表）。 */
    private fun createPreImportSnapshot(): File? = runCatching {
        checkpoint()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val target = File(repository.backupDir(), "pre-import-$stamp.xybak")
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val dbFile = context.getDatabasePath(BackupRepository.DB_NAME)
            if (dbFile.exists()) {
                zip.putNextEntry(ZipEntry(BackupRepository.DB_NAME))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            File(context.filesDir, "prompts").listFiles()
                ?.filter { it.extension == "md" }
                ?.forEach { file ->
                    zip.putNextEntry(ZipEntry("prompts/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        if (target.length() <= 0L) {
            target.delete()
            null
        } else {
            rotatePreImport()
            target
        }
    }.onFailure { Log.e(TAG, "创建导入前快照失败", it) }.getOrNull()

    private fun rotatePreImport() {
        repository.backupDir().listFiles { file -> file.name.startsWith("pre-import-") && file.extension == "xybak" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(PRE_IMPORT_KEEP)
            ?.forEach { it.delete() }
    }

    /** 数据库自检：quick_check 通过且含核心表才允许继续。 */
    private fun inspectStagedDatabase(file: File): DbInfo? = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            val quick = sqlite.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (quick == null || !quick.equals("ok", ignoreCase = true)) {
                Log.w(TAG, "备份库自检未通过：$quick")
                null
            } else {
                val version = sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val tables = mutableSetOf<String>()
                sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    while (cursor.moveToNext()) tables += cursor.getString(0)
                }
                // 把 WAL 里的数据合并回主文件，保证只拷主库文件也不会丢行。
                runCatching {
                    sqlite.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                }
                if ("messages" in tables && "sessions" in tables) DbInfo(version) else null
            }
        }
    }.onFailure { Log.w(TAG, "打开备份数据库失败", it) }.getOrNull()

    private fun currentDbVersion(): Int = runCatching {
        db.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }.getOrDefault(0)

    /**
     * 合并导入：逐表逐行 INSERT OR IGNORE，主键/唯一索引冲突时保留本机数据。
     * 不做任何 DELETE，符合 messages 只增不删的红线。
     */
    private fun mergeFrom(staging: File, onProgress: (Progress) -> Unit): Int {
        val source = SQLiteDatabase.openDatabase(staging.path, null, SQLiteDatabase.OPEN_READWRITE)
        val target = db.openHelper.writableDatabase
        var inserted = 0
        try {
            val tables = listTables(source)
            target.beginTransaction()
            try {
                tables.forEach { table ->
                    onProgress(Progress.Merging(table.name))
                    val targetColumns = columnsOf(target, table.name)
                    val sourceColumns = columnsOf(source, table.name).toSet()
                    val common = targetColumns.filter { it in sourceColumns }
                    if (common.isEmpty()) return@forEach
                    val columns = common.toMutableList()
                    // FTS 虚拟表依赖 rowid 与消息 id 对齐，必须显式复制。
                    if (table.virtual && "rowid" !in columns) columns.add(0, "rowid")
                    inserted += copyRows(source, target, table, columns)
                }
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        } finally {
            runCatching { source.close() }
        }
        return inserted
    }

    /** 只复制普通表与 FTS 主表；`message_fts_*` 影子表由 FTS 自行维护，直接写会损坏索引。 */
    private fun listTables(source: SQLiteDatabase): List<TableInfo> {
        val rows = mutableListOf<Pair<String, String?>>()
        source.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getString(0) to cursor.getString(1)
        }
        val virtualTables = rows
            .filter { (_, sql) -> sql?.contains("VIRTUAL TABLE", ignoreCase = true) == true }
            .map { it.first }
            .toSet()
        return rows.mapNotNull { (name, _) ->
            when {
                name.startsWith("sqlite_") -> null
                name == "android_metadata" || name == "room_master_table" -> null
                !SAFE_IDENTIFIER.matches(name) -> null
                virtualTables.any { virtual -> name.startsWith("${virtual}_") } -> null
                else -> TableInfo(name, name in virtualTables)
            }
        }
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<String> {
        val columns = mutableListOf<String>()
        db.query("PRAGMA main.table_info(${quote(table)})").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        return columns
    }

    private fun columnsOf(db: SQLiteDatabase, table: String): List<String> {
        val columns = mutableListOf<String>()
        db.rawQuery("PRAGMA main.table_info(${quote(table)})", null).use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        return columns
    }

    private fun copyRows(
        source: SQLiteDatabase,
        target: SupportSQLiteDatabase,
        table: TableInfo,
        columns: List<String>,
    ): Int {
        val quotedTable = quote(table.name)
        val columnList = columns.joinToString(", ") { quote(it) }
        val placeholders = columns.joinToString(", ") { "?" }
        val insertOrIgnore = "INSERT OR IGNORE INTO main.$quotedTable ($columnList) VALUES ($placeholders)"
        val plainInsert = "INSERT INTO main.$quotedTable ($columnList) VALUES ($placeholders)"
        var inserted = 0
        source.rawQuery("SELECT $columnList FROM $quotedTable", null).use { cursor ->
            while (cursor.moveToNext()) {
                val args = arrayOfNulls<Any?>(columns.size)
                for (index in columns.indices) {
                    args[index] = when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                        else -> cursor.getString(index)
                    }
                }
                if (table.virtual) {
                    // FTS 不支持 OR IGNORE，先判断 rowid 是否已存在。
                    val rowId = args.firstOrNull() as? Long
                    if (rowId != null && !rowExists(target, table.name, rowId)) {
                        target.execSQL(plainInsert, args)
                        inserted++
                    }
                } else {
                    target.execSQL(insertOrIgnore, args)
                    inserted += changedRows(target)
                }
            }
        }
        return inserted
    }

    private fun rowExists(db: SupportSQLiteDatabase, table: String, rowId: Long): Boolean =
        db.query("SELECT 1 FROM ${quote(table)} WHERE rowid = ?", arrayOf<Any?>(rowId)).use { it.moveToFirst() }

    private fun changedRows(db: SupportSQLiteDatabase): Int =
        db.query("SELECT changes()").use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun checkpoint() {
        runCatching {
            db.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        }
    }

    private fun stagingFile(): File {
        val databaseDir = context.getDatabasePath(BackupRepository.DB_NAME).parentFile ?: context.filesDir
        return File(databaseDir, STAGING_NAME)
    }

    private fun parseManifestVersion(json: String): Int =
        Regex("\"version\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun isZip(data: ByteArray): Boolean =
        data.size >= 4 && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte()

    private fun versionTooNew(version: Int): ImportResult.Failure = failure(
        FailureReason.VERSION_TOO_NEW,
        "备份来自更新版本的 App（备份格式 v$version，当前仅支持 v${BackupCrypto.FORMAT_VERSION}），" +
            "请先升级 App 后再恢复。",
    )

    private fun failure(reason: FailureReason, message: String): ImportResult.Failure {
        Log.w(TAG, "导入失败[$reason]：$message")
        return ImportResult.Failure(reason, message)
    }

    private fun quote(identifier: String): String = "\"" + identifier.replace("\"", "\"\"") + "\""

    private data class DbInfo(val version: Int)

    private data class TableInfo(val name: String, val virtual: Boolean)

    companion object {
        private const val TAG = "BackupImporter"
        private const val STAGING_NAME = "restore_staging.db"
        private const val MANIFEST_NAME = "manifest.json"
        private const val PRE_IMPORT_KEEP = 3
        private val SAFE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}



