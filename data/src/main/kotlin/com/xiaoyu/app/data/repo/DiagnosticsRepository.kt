package com.xiaoyu.app.data.repo

import android.content.Context
import com.xiaoyu.app.data.db.RequestSnapshotDao
import com.xiaoyu.app.data.db.RequestSnapshotEntity
import com.xiaoyu.soul.prompt.PromptAssembly
import com.xiaoyu.soul.util.TokenEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
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
 * 诊断面板数据源（执行书 6.13）：只在本地 SQLite 保留最近 50 次请求，
 * 不写日志、不上传；导出包自动脱敏 Base URL，且不含真实 Key。
 */
@Singleton
class DiagnosticsRepository @Inject constructor(
    private val dao: RequestSnapshotDao,
    @ApplicationContext private val context: Context,
) {
    fun observeRecent(limit: Int = 50): Flow<List<RequestSnapshotEntity>> = dao.observeRecent(limit)

    suspend fun recent(limit: Int = 50): List<RequestSnapshotEntity> = dao.recent(limit)

    suspend fun clear() = dao.clear()

    /** 记录一次聊天请求。 */
    suspend fun recordChat(
        sessionId: Long,
        provider: String,
        model: String,
        baseUrl: String,
        assembly: PromptAssembly,
        raw: String,
        durationMs: Long,
        promptTokens: Int,
        completionTokens: Int,
        finishReason: String,
        error: String?,
        events: String,
    ) {
        val sections = JSONArray().apply {
            assembly.blocks.forEach { block ->
                put(
                    JSONObject()
                        .put("index", block.index)
                        .put("key", block.key)
                        .put("label", block.label)
                        .put("volatile", block.volatile)
                        .put("tokens", TokenEstimator.estimate(block.content))
                        .put("content", block.content)
                )
            }
        }
        val params = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("temperature", 0.85)
            .put("max_tokens", 900)
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("temperature", 0.85)
            .put("max_tokens", 900)
            .put("messages", JSONArray().apply {
                assembly.messages.forEach { message ->
                    put(
                        JSONObject()
                            .put("role", message.role.name.lowercase(Locale.US))
                            .put("content", message.content)
                    )
                }
            })
        dao.insert(
            RequestSnapshotEntity(
                createdAt = System.currentTimeMillis(),
                traceTag = "chat",
                provider = provider,
                model = model,
                baseUrl = baseUrl,
                paramsJson = params.toString(),
                sectionsJson = sections.toString(),
                requestBody = body.toString(),
                responseRaw = raw,
                eventsText = events,
                durationMs = durationMs,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                finishReason = finishReason,
                error = error,
                sessionId = sessionId,
            )
        )
        dao.trim(50)
    }

    /** 复制到剪贴板的可复现命令，Key 用 $XY_API_KEY 占位（绝不复制真实 Key）。 */
    fun curl(snapshot: RequestSnapshotEntity): String {
        val base = snapshot.baseUrl.trimEnd('/').ifBlank { "https://api.example.com/v1" }
        return buildString {
            append("curl ").append(base).append("/chat/completions \\\n")
            append("  -H \"Content-Type: application/json\" \\\n")
            append("  -H \"Authorization: Bearer \$XY_API_KEY\" \\\n")
            append("  -d '").append(snapshot.requestBody).append("'")
        }
    }

    /** 生成脱敏诊断包：最近 20 次快照 + 版本信息（不含聊天原文与真实 Key）。 */
    suspend fun exportZip(): File {
        val dir = File(context.getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(dir, "xiaoyu-diagnostics-$stamp.zip")
        val snapshots = dao.recent(20)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("app-info.txt", appInfo())
            put("summary.tsv", summary(snapshots))
            snapshots.forEachIndexed { index, snapshot ->
                put("request-${index + 1}.txt", snapshotText(snapshot))
            }
        }
        return target
    }

    private fun appInfo(): String = buildString {
        append("package: ").append(context.packageName).append('\n')
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        append("version: ").append(info.versionName ?: "unknown").append('\n')
        append("code: ").append(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
            } else {
                info.versionCode.toString()
            }
        ).append('\n')
        append("android: ").append(android.os.Build.VERSION.RELEASE).append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
        append("device: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n')
        append("exported: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
    }

    private fun summary(items: List<RequestSnapshotEntity>): String = buildString {
        append("id\ttime\tprovider\tmodel\tprompt_tokens\tcompletion_tokens\tduration_ms\tfinish\n")
        items.forEach { item ->
            append(item.id).append('\t')
                .append(SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(item.createdAt))).append('\t')
                .append(item.provider).append('\t')
                .append(item.model).append('\t')
                .append(item.promptTokens).append('\t')
                .append(item.completionTokens).append('\t')
                .append(item.durationMs).append('\t')
                .append(item.finishReason ?: "").append('\n')
        }
    }

    private fun snapshotText(item: RequestSnapshotEntity): String = buildString {
        append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(item.createdAt))).append('\n')
        append("provider: ").append(item.provider).append(" / ").append(item.model).append('\n')
        append("base_url: (已脱敏)\n")
        append("params: ").append(item.paramsJson).append('\n')
        append("tokens: prompt=").append(item.promptTokens).append(" completion=").append(item.completionTokens).append('\n')
        append("duration_ms: ").append(item.durationMs).append('\n')
        append("finish: ").append(item.finishReason ?: "").append('\n')
        item.error?.let { append("error: ").append(it).append('\n') }
        append("events: ").append(item.eventsText).append("\n\n")
        append("===== prompt sections =====\n")
        runCatching {
            val sections = JSONArray(item.sectionsJson)
            for (i in 0 until sections.length()) {
                val section = sections.getJSONObject(i)
                append("[L").append(section.optInt("index")).append(" ")
                    .append(section.optString("key")).append("] ")
                    .append(section.optString("label")).append(" (~")
                    .append(section.optInt("tokens")).append(" tokens)\n")
                append(section.optString("content")).append("\n\n")
            }
        }
        append("===== raw response =====\n")
        append(item.responseRaw).append('\n')
    }
}
