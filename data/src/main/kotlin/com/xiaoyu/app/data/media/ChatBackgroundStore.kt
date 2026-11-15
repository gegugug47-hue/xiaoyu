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
 * 聊天背景图仓库：选中的图片会压缩后复制到应用私有目录，
 * 设置里只保存文件名。图片不会离开这台设备，也不需要任何存储权限。
 */
@Singleton
class ChatBackgroundStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun dir(): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun fileFor(name: String?): File? {
        if (name.isNullOrBlank()) return null
        return File(dir(), name).takeIf { it.isFile }
    }

    /** 读取相册图片 → 按 EXIF 摆正 → 缩放到最长边 [MAX_EDGE] → 存为 JPEG，返回文件名。 */
    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = decodeSampled(uri) ?: return@runCatching null
            val upright = applyExifRotation(uri, source)
            if (upright !== source) source.recycle()
            val scaled = scaleDown(upright)
            if (scaled !== upright) upright.recycle()

            val name = "chat-bg-${System.currentTimeMillis()}.jpg"
            File(dir(), name).outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            scaled.recycle()
            name
        }.getOrNull()
    }

    suspend fun delete(name: String?) = withContext(Dispatchers.IO) {
        if (!name.isNullOrBlank()) File(dir(), name).delete()
    }

    /** 清理历史遗留的图片，只保留当前正在使用的那张。 */
    suspend fun trim(keep: String?) = withContext(Dispatchers.IO) {
        dir().listFiles()?.forEach { file ->
            if (file.name != keep) file.delete()
        }
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        // 只解到略大于目标尺寸，避免 4K 原图一次性占满内存
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val DIR_NAME = "backgrounds"
        const val MAX_EDGE = 1440
        const val JPEG_QUALITY = 88
    }
}
