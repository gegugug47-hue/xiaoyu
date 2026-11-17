package com.xiaoyu.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 图片统一压缩入口（执行书 v3 §1.5 质量目标 / B-17）。
 *
 * - EXIF 方向纠正（相册图片不会横躺）；
 * - 两段解码：先读边界算 `inSampleSize`，再按需精确缩放，避免 4K 原图一次性吃满内存；
 * - 长边上限：头像 512 / 表情 512 / 生图 2048 / 背景 2560；
 * - 输入超过 [MAX_INPUT_BYTES] 时额外降一档采样；
 * - 输出 JPEG，质量默认 [DEFAULT_QUALITY]（88）。
 *
 * 只负责"压"，不改变调用方原有的目录与文件命名规则：
 * [compress] 写 `files/images/`，[compressTo] 由调用方指定落地路径。
 */
@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 从相册/SAF 的 URI 压缩，产物落在 `files/images/<时间戳>-<随机>.jpg`。
     *
     * @return 压缩后的文件；解码或编码失败返回 null。
     */
    suspend fun compress(
        uri: Uri,
        maxEdge: Int = IMAGE_EDGE,
        quality: Int = DEFAULT_QUALITY,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeUpright(uri, maxEdge) ?: return@runCatching null
            val target = File(
                outputDir(),
                "${System.currentTimeMillis()}-${(0..0xFFFF).random().toString(16)}.jpg",
            )
            writeBitmap(bitmap, target, quality).takeIf { it }?.let { target }
        }.getOrNull()
    }

    /**
     * 从本地文件压缩并写到 [target]（由调用方决定目录与文件名）。
     * 若 [target] 与源文件相同，先写临时文件再覆盖，避免边读边写截断。
     */
    suspend fun compressTo(
        source: File,
        target: File,
        maxEdge: Int = IMAGE_EDGE,
        quality: Int = DEFAULT_QUALITY,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            if (!source.isFile) return@runCatching null
            val bitmap = decodeUpright(source.path, maxEdge) ?: return@runCatching null
            val sameFile = source.absolutePath == target.absolutePath
            val destination = if (sameFile) File(target.parentFile, "${target.name}.tmp") else target
            destination.parentFile?.mkdirs()
            val ok = writeBitmap(bitmap, destination, quality)
            if (!ok) {
                destination.delete()
                return@runCatching null
            }
            if (sameFile) {
                source.delete()
                if (!destination.renameTo(target)) {
                    destination.copyTo(target, overwrite = true)
                    destination.delete()
                }
            }
            target.takeIf { it.isFile && it.length() > 0L }
        }.getOrNull()
    }

    /**
     * 两段解码 + EXIF 摆正 + 长边缩放，返回调用方自行编码的 Bitmap。
     * 表情包需要保留透明通道（WEBP），所以只复用这一步，编码仍由调用方做。
     */
    fun decodeUpright(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(longEdge, maxEdge, File(path).length())
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val upright = applyExifRotation(path, decoded)
        if (upright !== decoded) decoded.recycle()
        return scaleToMaxEdge(upright, maxEdge)
    }

    private fun decodeUpright(uri: Uri, maxEdge: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(longEdge, maxEdge, inputBytes(uri))
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val upright = applyExifRotation(uri, decoded)
        if (upright !== decoded) decoded.recycle()
        return scaleToMaxEdge(upright, maxEdge)
    }

    /** 输入体积未知时按 0 处理，只做常规两段采样。 */
    private fun inputBytes(uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    }.getOrDefault(0L)

    private fun sampleSize(longEdge: Int, maxEdge: Int, inputBytes: Long): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= maxEdge) sample *= 2
        // 超过 12MB 先额外降一档，避免一次解出超大 Bitmap。
        if (inputBytes > MAX_INPUT_BYTES && longEdge / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            exifDegrees(
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                ),
            )
        }.getOrDefault(0f)
        return rotate(bitmap, degrees)
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                exifDegrees(
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    ),
                )
            } ?: 0f
        }.getOrDefault(0f)
        return rotate(bitmap, degrees)
    }

    private fun exifDegrees(orientation: Int): Float = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun writeBitmap(bitmap: Bitmap, target: File, quality: Int): Boolean {
        target.parentFile?.mkdirs()
        val ok = runCatching {
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            }
        }.getOrDefault(false)
        bitmap.recycle()
        return ok && target.isFile && target.length() > 0L
    }

    private fun outputDir(): File = File(context.filesDir, OUTPUT_DIR).apply { mkdirs() }

    companion object {
        /** 头像长边上限。 */
        const val AVATAR_EDGE = 512

        /** 表情包静态图长边上限。 */
        const val STICKER_EDGE = 512

        /** 生图入库长边上限。 */
        const val IMAGE_EDGE = 2048

        /** 聊天背景长边上限（与 ChatBackgroundStore 保持一致）。 */
        const val BACKGROUND_EDGE = 2560

        /** 默认 JPEG 质量。 */
        const val DEFAULT_QUALITY = 88

        /** 输入超过 12MB 时额外降采样。 */
        const val MAX_INPUT_BYTES = 12L * 1024 * 1024

        private const val OUTPUT_DIR = "images"
    }
}
