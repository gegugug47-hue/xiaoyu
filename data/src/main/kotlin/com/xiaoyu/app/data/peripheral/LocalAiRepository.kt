package com.xiaoyu.app.data.peripheral

import android.content.Context
import com.xiaoyu.app.data.media.ImageCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 本地 AI 网关（DSHA Gateway）的连通状态与可用预设。 */
data class LocalAiHealth(
    val ok: Boolean,
    val comfyAvailable: Boolean,
    val ttsAvailable: Boolean,
    val defaultStyle: String?,
    val defaultVoice: String?,
    val styles: List<String>,
    val voices: List<String>,
)

/**
 * 本地 AI 外设（PRD US-018 生图 / US-019 语音朗读）。
 *
 * 统一走家里电脑的 DSHA Gateway（默认 http://<电脑IP>:8300，Bearer Token 认证）：
 * - 生图：POST /api/prompt/image 扩写 → POST /api/image 提交 → 轮询 /api/task/{id} → GET /api/file/{id}
 * - 语音：POST /api/tts 提交 → 轮询 /api/task/{id} → GET /api/file/{id}
 * 任务异步执行，result.file 是电脑本机路径，远程端一律用 /api/file/{task_id} 下载。
 */
@Singleton
class LocalAiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val imageCompressor: ImageCompressor,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 探测类请求（健康检查 / 预设列表）用短超时，电脑没开时能立刻给出"连不上"，而不是卡在"连接中…"。 */
    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val imageDir: File by lazy {
        File(context.filesDir, "generated-images").apply { mkdirs() }
    }
    private val voiceDir: File by lazy {
        File(context.filesDir, "voices").apply { mkdirs() }
    }

    fun isConfigured(baseUrl: String?): Boolean = !baseUrl.isNullOrBlank()

    // ---------------- 健康检查 / 预设列表 ----------------

    /** GET /api/health（无需认证）。电脑关机/断网时快速返回 null。 */
    suspend fun health(baseUrl: String?): LocalAiHealth? = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl) ?: return@withContext null
        runCatching {
            val request = Request.Builder().url("$base/api/health").get().build()
            probeClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = json.parseToJsonElement(resp.body?.string() ?: return@use null).jsonObject
                val image = root["image"]?.jsonObject
                LocalAiHealth(
                    ok = root["ok"]?.jsonPrimitive?.contentOrNull?.toBoolean() == true,
                    comfyAvailable = root["comfyui"]?.jsonObject?.get("available")
                        ?.jsonPrimitive?.contentOrNull?.toBoolean() == true,
                    ttsAvailable = root["tts"]?.jsonObject?.get("available")
                        ?.jsonPrimitive?.contentOrNull?.toBoolean() == true,
                    defaultStyle = image?.get("default_style")?.jsonPrimitive?.contentOrNull,
                    defaultVoice = root["voices"]?.jsonObject?.get("default")?.jsonPrimitive?.contentOrNull,
                    styles = namesOf(root["styles"]).filterNot { it == "base" },
                    voices = namesOf(root["voices"]),
                )
            }
        }.getOrNull()
    }

    /** GET /api/styles 画风预设名列表。 */
    suspend fun listStyles(baseUrl: String?, token: String?): List<String> =
        listNames("api/styles", baseUrl, token)

    /** GET /api/voices 音色预设名列表。 */
    suspend fun listVoices(baseUrl: String?, token: String?): List<String> =
        listNames("api/voices", baseUrl, token)

    private suspend fun listNames(
        path: String,
        baseUrl: String?,
        token: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl) ?: return@withContext emptyList()
        runCatching {
            val request = Request.Builder().url("$base/$path").applyAuth(token).get().build()
            probeClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<String>()
                val root = json.parseToJsonElement(resp.body?.string() ?: return@use emptyList<String>())
                // 兼容 {"names":[...]} / {"voices":{...}} / 纯数组
                when (root) {
                    is kotlinx.serialization.json.JsonArray ->
                        root.mapNotNull { it.jsonPrimitive.contentOrNull }
                    is JsonObject -> {
                        val arr = root["names"] as? kotlinx.serialization.json.JsonArray
                            ?: root["styles"] as? kotlinx.serialization.json.JsonArray
                        arr?.mapNotNull { it.jsonPrimitive.contentOrNull }
                            ?: (root["voices"] as? JsonObject)?.keys?.toList()
                            ?: emptyList()
                    }
                    else -> emptyList()
                }
            }
        }.getOrDefault(emptyList())
    }

    // ---------------- 语音合成 ----------------

    /**
     * 合成语音并缓存到本地。
     * 缓存键 = 文本 + 音色，切换音色不会串音。
     * 首次调用含模型加载约 50~90s，之后约 20s，轮询超时给 600s。
     */
    suspend fun synthesize(
        text: String,
        voice: String?,
        baseUrl: String?,
        token: String?,
    ): File? = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl) ?: return@withContext null
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext null

        val cached = File(voiceDir, "${digest("${voice.orEmpty()}|$clean")}.wav")
        if (cached.exists() && cached.length() > 0) return@withContext cached

        runCatching {
            val payload = buildJsonObject {
                put("text", clean)
                if (!voice.isNullOrBlank()) put("voice", voice)
                put("timeout_s", 600)
            }.toString()
            val taskId = submit("$base/api/tts", payload, token) ?: return@withContext null
            val result = waitTask(base, taskId, token, timeoutMs = 600_000) ?: return@withContext null
            // 音色不匹配时服务端会回落默认音色，缓存键仍按请求音色，等于该音色下缓存一份
            download(base, taskId, token, cached, expectBytes = result.bytesOrNull) ?: return@withContext null
        }.getOrNull()
    }

    // ---------------- 生图 ----------------

    /**
     * 中文需求 → SDXL 提示词 → 出图。
     * 提示词扩写失败时退回原文直接出图，保证功能可用。
     */
    suspend fun generateImage(
        prompt: String,
        style: String?,
        baseUrl: String?,
        token: String?,
    ): File? = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl) ?: return@withContext null
        val raw = prompt.trim()
        if (raw.isEmpty()) return@withContext null

        runCatching {
            val expanded = expandPrompt(base, raw, token)
            val body = buildJsonObject {
                put("prompt", expanded?.positive ?: raw)
                expanded?.negative?.let { put("negative", it) }
                if (!style.isNullOrBlank()) put("style", style)
                put("timeout_s", 600)
            }.toString()
            val taskId = submit("$base/api/image", body, token) ?: return@withContext null
            val result = waitTask(base, taskId, token, timeoutMs = 660_000) ?: return@withContext null
            // 网关回退到备用模型时如实标注，便于排查"画风不对"
            if (result.fallback == true) {
                android.util.Log.w("LocalAi", "生图回退到备用模型: ${result.fallbackModel}")
            }
            val raw = File(imageDir, "${System.currentTimeMillis()}.png")
            download(base, taskId, token, raw, expectBytes = result.bytesOrNull) ?: return@withContext null
            // 生图入库统一压缩到长边 2048 的 JPEG（B-17），目录不变。
            val compact = imageCompressor.compressTo(
                raw,
                File(imageDir, "${raw.nameWithoutExtension}.jpg"),
                ImageCompressor.IMAGE_EDGE,
                ImageCompressor.DEFAULT_QUALITY,
            )
            if (compact != null) {
                raw.delete()
                compact
            } else {
                raw
            }
        }.getOrNull()
    }

    private data class ExpandedPrompt(val positive: String?, val negative: String?)
    private data class TaskResult(val bytesOrNull: Long?, val fallback: Boolean?, val fallbackModel: String?)

    private fun expandPrompt(base: String, text: String, token: String?): ExpandedPrompt? {
        // 提示词扩写走本地 Qwen，实测 8s 左右；限制在 120s 内，避免撞上 OkHttp 的 180s 读超时
        val payload = buildJsonObject {
            put("text", text)
            put("timeout_s", 120)
        }.toString()
        return runCatching {
            val request = Request.Builder()
                .url("$base/api/prompt/image")
                .applyAuth(token)
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = json.parseToJsonElement(resp.body?.string() ?: return@use null).jsonObject
                ExpandedPrompt(
                    positive = root["positive"]?.jsonPrimitive?.contentOrNull,
                    negative = root["negative"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }.getOrNull()
    }

    // ---------------- 内部：提交 / 轮询 / 下载 ----------------

    private fun submit(url: String, payload: String, token: String?): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .applyAuth(token)
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val root = json.parseToJsonElement(resp.body?.string() ?: return@use null).jsonObject
            root["task_id"]?.jsonPrimitive?.contentOrNull
                ?: root["job_id"]?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()

    private suspend fun waitTask(
        base: String,
        taskId: String,
        token: String?,
        timeoutMs: Long,
    ): TaskResult? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(1_500)
            val task = runCatching {
                val request = Request.Builder()
                    .url("$base/api/task/$taskId")
                    .applyAuth(token)
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    json.parseToJsonElement(resp.body?.string() ?: return@use null).jsonObject
                }
            }.getOrNull() ?: continue

            when (task["status"]?.jsonPrimitive?.contentOrNull) {
                "done" -> {
                    val result = task["result"]?.jsonObject
                    return TaskResult(
                        bytesOrNull = result?.get("bytes")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                        fallback = result?.get("fallback")?.jsonPrimitive?.contentOrNull?.toBoolean(),
                        fallbackModel = result?.get("fallback_model")?.jsonPrimitive?.contentOrNull,
                    )
                }
                "failed", "cancelled" -> return null
            }
        }
        return null
    }

    private fun download(
        base: String,
        taskId: String,
        token: String?,
        target: File,
        expectBytes: Long?,
    ): File? {
        val request = Request.Builder()
            .url("$base/api/file/$taskId")
            .applyAuth(token)
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        val size = target.length()
        if (size <= 0) return null
        if (expectBytes != null && size != expectBytes) {
            android.util.Log.w("LocalAi", "文件长度不符: 期望 $expectBytes 实际 $size")
        }
        return target
    }

    private fun Request.Builder.applyAuth(token: String?): Request.Builder =
        if (token.isNullOrBlank()) this
        else header("Authorization", "Bearer ${token.trim()}")

    private fun normalize(baseUrl: String?): String? {
        var s = baseUrl?.replace("\n", "")?.replace(" ", "")?.trim() ?: return null
        if (s.isEmpty()) return null
        // 容忍只填 "10.x.x.x:8300" 或结尾多带了 /api
        if (!s.startsWith("http://", ignoreCase = true) &&
            !s.startsWith("https://", ignoreCase = true)
        ) {
            s = "http://$s"
        }
        s = s.trimEnd('/').removeSuffix("/api").trimEnd('/')
        return s.ifBlank { null }
    }

    /** 解析 health 里的 {"default":..., "names":[...]} 结构。 */
    private fun namesOf(element: kotlinx.serialization.json.JsonElement?): List<String> {
        val obj = element as? JsonObject ?: return emptyList()
        return obj["names"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    }

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** 清理所有语音缓存。 */
    fun clearVoiceCache() {
        voiceDir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
