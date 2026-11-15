package com.xiaoyu.app.data.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.xiaoyu.app.data.brain.BrainGateway
import com.xiaoyu.app.data.repo.VisionRepository
import com.xiaoyu.soul.core.BrainRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** 单枚表情的打标结果，直接写回 stickers.name / tags / mood_tags。 */
data class StickerTagResult(
    val index: Int,
    val name: String,
    val tags: String,
    val moodTags: String,
    val tagged: Boolean,
)

/**
 * 自动打标（执行书 M4-04 / 7.4.3 第 4 步）：走 VISION 角色批量出 JSON。
 * 图片按 [VisionRepository] 的既有约定压到 160px JPEG + data URI 附带在用户消息里。
 * 未配置视觉模型、请求失败或返回非法 JSON 时**不抛错**，返回默认名称让用户手填。
 */
@Singleton
class StickerTagger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brain: BrainGateway,
    private val vision: VisionRepository,
) {

    /**
     * 批量打标，返回与 [items] 同序的结果；[degraded] 为 true 表示已降级为手动填写。
     * 按 [BATCH_SIZE] 分片请求，避免一次塞 30 张图把上下文撑爆。
     */
    suspend fun tag(items: List<StickerImportItem>): StickerTagBatch = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext StickerTagBatch(emptyList(), degraded = false)
        val defaults = items.mapIndexed { i, item ->
            StickerTagResult(
                index = i + 1,
                name = item.name.take(10).ifBlank { "表情${i + 1}" },
                tags = "",
                moodTags = "",
                tagged = false,
            )
        }
        if (!vision.isVisionConfigured()) {
            return@withContext StickerTagBatch(defaults, degraded = true)
        }
        val system = systemPrompt()
        val results = defaults.toMutableList()
        var anyTagged = false
        var offset = 0
        for (chunk in items.chunked(BATCH_SIZE)) {
            val raw = runCatching {
                brain.completeJson(BrainRole.VISION, system, buildUserPayload(chunk))
            }.getOrNull()
            val parsed = parse(raw).associateBy { it.index }
            for (i in chunk.indices) {
                val hit = parsed[i + 1] ?: continue
                results[offset + i] = merge(defaults[offset + i], hit)
                anyTagged = true
            }
            offset += chunk.size
        }
        StickerTagBatch(results, degraded = !anyTagged)
    }

    private fun merge(fallback: StickerTagResult, hit: TagDto): StickerTagResult {
        val name = hit.name?.trim()?.take(10)?.takeIf { it.isNotBlank() } ?: fallback.name
        val tags = hit.tags.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct().take(4)
        val moods = hit.moodTags.orEmpty()
            .map { it.trim().lowercase() }
            .filter { MOOD_TAGS.contains(it) }
            .distinct()
            .take(3)
        return StickerTagResult(
            index = fallback.index,
            name = name,
            tags = tags.joinToString(","),
            moodTags = moods.joinToString(","),
            tagged = true,
        )
    }

    /** 模板外置在 assets/prompts，用户改模板无需改代码；读不到时用兜底文案。 */
    private fun systemPrompt(): String = runCatching {
        context.assets.open("prompts/sticker_tagging.md").bufferedReader().use { it.readText() }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_PROMPT

    private fun buildUserPayload(items: List<StickerImportItem>): String = buildString {
        append("[")
        items.forEachIndexed { i, item ->
            if (i > 0) append(",")
            append("{\"index\":").append(i + 1)
            append(",\"file\":\"").append(File(item.filePath).name).append('"')
            append(",\"width\":").append(item.width)
            append(",\"height\":").append(item.height)
            append(",\"animated\":").append(item.animated)
            val dataUri = imageDataUri(item)
            if (dataUri != null) {
                append(",\"image\":\"").append(dataUri).append('"')
            }
            append("}")
        }
        append("]")
    }

    /** 与 VisionRepository 一致：只给视觉端点喂小图，省 token 也避免超长 Base64。 */
    private fun imageDataUri(item: StickerImportItem): String? = runCatching {
        val bytes = File(item.filePath).readBytes()
        val compressed = compressForVision(bytes)
        "data:image/jpeg;base64," + Base64.encodeToString(compressed, Base64.NO_WRAP)
    }.getOrNull()

    private fun compressForVision(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= IMAGE_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return bytes
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest <= IMAGE_EDGE) bitmap else {
            val ratio = IMAGE_EDGE.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        val result = out.toByteArray()
        return if (result.isNotEmpty()) result else bytes
    }

    /** 模型偶尔会包一层代码块或解释文字，这里只截取最外层 JSON 数组。 */
    private fun parse(raw: String?): List<TagDto> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val body = text.substring(start, end + 1)
        return runCatching { json.decodeFromString<List<TagDto>>(body) }.getOrDefault(emptyList())
            .filter { it.index in 1..MAX_ITEMS }
    }

    @Serializable
    private data class TagDto(
        val index: Int = 0,
        val name: String? = null,
        val tags: List<String>? = null,
        @SerialName("mood_tags") val moodTags: List<String>? = null,
    )

    private companion object {
        const val BATCH_SIZE = 6
        const val MAX_ITEMS = 30
        const val IMAGE_EDGE = 160
        const val IMAGE_QUALITY = 60
        val MOOD_TAGS = setOf(
            "joy", "excited", "sad", "shy", "angry", "tease", "tired", "think", "love", "speechless",
        )
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        const val FALLBACK_PROMPT =
            "给用户导入的表情起名并打标签。只输出 JSON 数组，元素含 index、name（≤10 字）、tags（≤4 个）、mood_tags（≤3 个）。"
    }
}

/** 一批打标结果；[degraded] 表示没有可用视觉模型，界面应引导用户手填。 */
data class StickerTagBatch(
    val results: List<StickerTagResult>,
    val degraded: Boolean,
)
