package com.xiaoyu.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.xiaoyu.app.data.brain.BrainGateway
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.prompt.PromptKeys
import com.xiaoyu.soul.prompt.PromptLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VISION 看图（执行书 M7-05）。
 *
 * 行为约定：
 * 1. 无论有没有视觉模型，图片都会先归档到应用私有目录（files/vision），原图不丢；
 * 2. 有可用模型时，把图片压到最长边 1024 的 JPEG 再 Base64 交给
 *    `BrainGateway.completeJson(BrainRole.VISION, ...)`，提示词来自 assets/prompts/vision_describe.md；
 * 3. 没有配置模型时**不抛异常**，返回 Unsupported，由 UI 自然说一句“当前模型不支持看图”；
 * 4. 任何失败都降级成 Failed，不影响聊天主链路。
 *
 * 说明：当前 BrainGateway 只接受纯文本，多模态图片数据以 data URI 形式附带在用户消息里，
 * 由支持视觉的 OpenAI 兼容端点自行解析；若后续扩展 Gateway 的 image_url 内容块，本类无需改动调用方。
 */
@Singleton
class VisionRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val router: BrainGateway,
    private val prompts: PromptLibrary,
) {

    sealed interface Result {
        val archivePath: String?

        data class Described(
            val description: String,
            val scene: String,
            val moodTags: List<String>,
            val ocr: String,
            override val archivePath: String?,
        ) : Result

        data class Unsupported(
            override val archivePath: String?,
            val message: String = "当前模型不支持看图，图片已经帮你收好了。",
        ) : Result

        data class Failed(
            override val archivePath: String?,
            val message: String,
        ) : Result
    }

    /** 是否配置了可用于看图的模型（与 BrainRouter 的 VISION 降级链保持一致）。 */
    fun isVisionConfigured(): Boolean {
        val brain = settings.current().brain
        return brain.extract.isConfigured ||
            brain.chat.isConfigured ||
            brain.chatFallback?.isConfigured == true ||
            brain.extractFallback?.isConfigured == true
    }

    /** 从 content:// / file:// / 本地路径读取图片并描述。 */
    suspend fun describeImage(
        imageUri: String,
        userNote: String = "",
        herName: String = "小渔",
    ): Result = withContext(Dispatchers.IO) {
        val bytes = runCatching { readBytes(imageUri) }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            return@withContext Result.Failed(null, "图片读不出来，可能已经被移动或删掉了。")
        }
        describeImageBytes(bytes, guessExtension(imageUri), userNote, herName)
    }

    /** 直接处理内存中的图片字节（相机 / 相册选择器均可复用）。 */
    suspend fun describeImageBytes(
        bytes: ByteArray,
        extension: String = "jpg",
        userNote: String = "",
        herName: String = "小渔",
    ): Result = withContext(Dispatchers.IO) {
        val archivePath = runCatching { archive(bytes, extension) }.getOrNull()
        if (!isVisionConfigured()) {
            return@withContext Result.Unsupported(archivePath)
        }
        val payload = runCatching { compressForVision(bytes) }.getOrDefault(bytes)
        val base64 = runCatching { Base64.encodeToString(payload, Base64.NO_WRAP) }.getOrNull()
        if (base64.isNullOrBlank()) {
            return@withContext Result.Failed(archivePath, "图片处理失败，先帮你收好了，下次再试。")
        }
        val prompt = prompts.render(
            PromptKeys.VISION_DESCRIBE,
            mapOf(
                "her_name" to herName,
                "user_note" to userNote.ifBlank { "（没有附言）" },
            ),
        )
        val userText = buildString {
            append(prompt)
            append("\n\n[图片]\ndata:image/jpeg;base64,")
            append(base64)
        }
        val raw = runCatching {
            router.completeJson(BrainRole.VISION, null, userText)
        }.getOrNull()
        if (raw.isNullOrBlank()) {
            return@withContext Result.Failed(archivePath, "她盯着图片看了一会儿，没看清。可以稍后再试，或换一个支持看图的模型。")
        }
        parse(raw, archivePath)
    }

    /** 原图归档：只写应用私有目录，文件名带时间戳与随机串，避免覆盖。 */
    suspend fun archive(bytes: ByteArray, extension: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "vision").apply { mkdirs() }
        val safeExt = extension.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
        val file = File(dir, "vision-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.$safeExt")
        file.writeBytes(bytes)
        file.absolutePath
    }

    private fun parse(raw: String, archivePath: String?): Result {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val obj = runCatching { JSONObject(cleaned) }.getOrNull()
            ?: return Result.Failed(archivePath, "她看懂了，但没能好好说出来。")
        val description = obj.optString("description").trim()
            .ifBlank { obj.optString("scene").trim() }
        if (description.isBlank()) {
            return Result.Failed(archivePath, "她看懂了，但没能好好说出来。")
        }
        return Result.Described(
            description = description,
            scene = obj.optString("scene").trim(),
            moodTags = obj.optJSONArray("mood_tags").toStringList(),
            ocr = obj.optString("ocr").trim(),
            archivePath = archivePath,
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val value = optString(i).trim()
            if (value.isNotEmpty()) list += value
        }
        return list
    }

    private fun readBytes(uri: String): ByteArray {
        if (uri.startsWith("content://") || uri.startsWith("file://")) {
            return context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                ?: ByteArray(0)
        }
        return File(uri).readBytes()
    }

    /** 压到最长边 1024 的 JPEG，既省 token 也避免超长 Base64。 */
    private fun compressForVision(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
        val scaled = scaleToMax(bitmap, 1024)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        val compressed = out.toByteArray()
        return if (compressed.isEmpty()) bytes else compressed
    }

    private fun scaleToMax(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun guessExtension(uri: String): String {
        val path = uri.substringBefore('?').lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".gif") -> "gif"
            path.endsWith(".heic") || path.endsWith(".heif") -> "heic"
            else -> "jpg"
        }
    }
}
