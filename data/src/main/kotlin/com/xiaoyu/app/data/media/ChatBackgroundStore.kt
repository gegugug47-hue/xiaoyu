package com.xiaoyu.app.data.media

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 聊天背景图仓库（执行书 v3 §14.6.1 / §14.6.3 / §14.6.6）。
 *
 * - 原图复制到 `files/bg/<sha1>.jpg`，不依赖相册 URI 的长期权限；
 * - 单张 ≤12MB（超出先降采样到 2560 长边），全局最多 3 张，按最旧淘汰；
 * - API 26–30 预渲染模糊缓存 `files/bg/cache/<sha1>-<tier>-<edge>-<d|l>.webp`，
 *   缓存总量 ≤40MB，LRU 淘汰；缓存键含深色标志（§14.6.3）。
 */
@Singleton
class ChatBackgroundStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun dir(): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun cacheDir(): File = File(dir(), CACHE_DIR).apply { mkdirs() }

    private fun legacyDir(): File = File(context.filesDir, LEGACY_DIR_NAME)

    /**
     * 按 sha1 取原图；也兼容旧版 `backgrounds/<name>.jpg` 的文件名（只读，不迁移）。
     */
    fun fileFor(name: String?): File? {
        if (name.isNullOrBlank()) return null
        val modernName = if (name.endsWith(".jpg", ignoreCase = true)) name else "$name.jpg"
        File(dir(), modernName).takeIf { it.isFile }?.let { return it }
        return File(legacyDir(), name).takeIf { it.isFile }
    }

    /**
     * 读取相册图片 → EXIF 摆正 → 超 12MB 时降采样到 2560 长边 → 存 `files/bg/<sha1>.jpg`。
     *
     * @return 新图的 sha1；失败返回 null。
     */
    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = decodeSampled(uri, MAX_EDGE_SOFT) ?: return@runCatching null
            val upright = applyExifRotation(uri, source)
            if (upright !== source) source.recycle()

            var bytes = encodeJpeg(upright)
            if (bytes.size > MAX_BYTES) {
                val scaled = scaleDown(upright, SMALL_EDGE)
                bytes = encodeJpeg(scaled)
                if (scaled !== upright) scaled.recycle()
            }
            upright.recycle()

            val sha1 = sha1Of(bytes)
            val target = File(dir(), "$sha1.jpg")
            if (!target.isFile || target.length() <= 0L) target.writeBytes(bytes)
            sha1
        }.getOrNull()
    }

    /**
     * 旧版 settings.chatBackground 文件名 → 复制进 `files/bg/<sha1>.jpg` 并返回 sha1。
     * 旧文件与旧键都保留，只是把内容收纳到新目录（迁移兼容）。
     */
    suspend fun adoptLegacy(name: String?): String? = withContext(Dispatchers.IO) {
        if (name.isNullOrBlank()) return@withContext null
        runCatching {
            val modernName = if (name.endsWith(".jpg", ignoreCase = true)) name else "$name.jpg"
            val modern = File(dir(), modernName)
            if (modern.isFile) return@runCatching modern.nameWithoutExtension
            val legacy = File(legacyDir(), name)
            if (!legacy.isFile) return@runCatching null
            val bytes = legacy.readBytes()
            val sha1 = sha1Of(bytes)
            val target = File(dir(), "$sha1.jpg")
            if (!target.isFile || target.length() <= 0L) target.writeBytes(bytes)
            sha1
        }.getOrNull()
    }

    /** 全局最多 3 张原图，超出按最旧淘汰（[keep] 里的在用图优先保留）。 */
    suspend fun enforceQuota(keep: Collection<String>) = withContext(Dispatchers.IO) {
        val files = dir().listFiles { file -> file.isFile && file.extension == "jpg" }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList() ?: return@withContext
        while (files.size > MAX_IMAGES) {
            val victim = files.firstOrNull { it.nameWithoutExtension !in keep } ?: files.first()
            victim.delete()
            files.remove(victim)
        }
    }

    /**
     * 取（必要时生成）预渲染模糊缓存，仅 API < 31 使用。
     *
     * @return 缓存文件；[blurTier] 为 0 或失败时返回原图 / null。
     */
    suspend fun ensureCache(
        sha1: String,
        blurTier: Int,
        edgePx: Int,
        dark: Boolean,
    ): File? = withContext(Dispatchers.IO) {
        if (blurTier <= 0) return@withContext fileFor(sha1)
        val target = cacheFile(sha1, blurTier, edgePx, dark)
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        runCatching {
            val source = fileFor(sha1) ?: return@runCatching null
            val bitmap = decodeSampled(source, edgePx) ?: return@runCatching null
            val scaled = scaleDown(bitmap, edgePx)
            if (scaled !== bitmap) bitmap.recycle()
            val radius = blurRadiusPx(blurTier, edgePx)
            val blurred = if (radius > 0) boxBlur(scaled, radius) else scaled
            if (blurred !== scaled) scaled.recycle()
            if (!cacheDir().exists()) cacheDir().mkdirs()
            target.outputStream().use { out ->
                blurred.compress(androidCompressFormat(), CACHE_QUALITY, out)
            }
            blurred.recycle()
            trimCache()
            target
        }.getOrNull()
    }

    /** 导入/应用后预热六档缓存（§14.6.3：0/6/12/18/24/32dp）。 */
    suspend fun warmCache(sha1: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@withContext
        val edge = renderEdgePx()
        BLUR_TIERS.forEach { tier ->
            if (tier > 0) ensureCache(sha1, tier, edge, dark = false)
        }
    }

    /** 缓存占用（字节），设置页存储信息用。 */
    fun cacheSizeBytes(): Long =
        cacheDir().listFiles()?.sumOf { it.length() } ?: 0L

    suspend fun delete(sha1: String?) = withContext(Dispatchers.IO) {
        if (sha1.isNullOrBlank()) return@withContext
        File(dir(), "$sha1.jpg").delete()
        cacheDir().listFiles { file -> file.name.startsWith("$sha1-") }?.forEach { it.delete() }
    }

    /** 只保留指定图片，其余删除（旧版设置页的清理语义，保留以免破坏调用方）。 */
    suspend fun trim(keep: String?) = withContext(Dispatchers.IO) {
        val keepFile = fileFor(keep)
        dir().listFiles { file -> file.isFile && file.extension == "jpg" }?.forEach { file ->
            if (file != keepFile) file.delete()
        }
    }

    fun isLowRam(): Boolean = runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }.getOrDefault(false)

    fun screenLongEdge(): Int {
        val metrics = context.resources.displayMetrics
        return max(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    }

    /** §14.6.3：预渲染尺寸取 `min(screenLongEdge/2, 1080)`。 */
    fun renderEdgePx(): Int = min(screenLongEdge() / 2, 1080).coerceAtLeast(360)

    private fun cacheFile(sha1: String, tier: Int, edge: Int, dark: Boolean): File =
        File(cacheDir(), "$sha1-$tier-$edge-${if (dark) "d" else "l"}.webp")

    private fun blurRadiusPx(blurDp: Int, edgePx: Int): Int {
        val density = context.resources.displayMetrics.density
        val ratio = edgePx.toFloat() / screenLongEdge()
        return (blurDp * density * ratio).roundToInt().coerceAtLeast(1)
    }

    private fun androidCompressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    private fun decodeSampled(uri: Uri, maxEdge: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeSampled(file: File, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
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

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }

    private fun sha1Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 缓存总量上限 40MB，LRU（按 lastModified）淘汰。 */
    private fun trimCache() {
        val files = cacheDir().listFiles { file -> file.isFile }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        var index = 0
        while (total > MAX_CACHE_BYTES && index < files.size - 1) {
            val victim = files[index++]
            total -= victim.length()
            victim.delete()
        }
    }

    /**
     * 3 趟盒式模糊（§14.6.3），O(w·h) 滑窗实现。
     * 先横向再纵向算一趟，重复 [BOX_PASSES] 次。
     */
    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source
        val width = source.width
        val height = source.height
        var pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(BOX_PASSES) {
            pixels = blurPass(pixels, width, height, radius)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun blurPass(input: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val horizontal = IntArray(input.size)
        val denominator = radius * 2 + 1
        for (y in 0 until height) {
            val offset = y * width
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (i in -radius..radius) {
                val pixel = input[offset + i.coerceIn(0, width - 1)]
                sumA += pixel ushr 24 and 0xFF
                sumR += pixel ushr 16 and 0xFF
                sumG += pixel ushr 8 and 0xFF
                sumB += pixel and 0xFF
            }
            for (x in 0 until width) {
                horizontal[offset + x] = pack(
                    sumA / denominator,
                    sumR / denominator,
                    sumG / denominator,
                    sumB / denominator,
                )
                val outPixel = input[offset + (x - radius).coerceIn(0, width - 1)]
                val inPixel = input[offset + (x + radius + 1).coerceIn(0, width - 1)]
                sumA += (inPixel ushr 24 and 0xFF) - (outPixel ushr 24 and 0xFF)
                sumR += (inPixel ushr 16 and 0xFF) - (outPixel ushr 16 and 0xFF)
                sumG += (inPixel ushr 8 and 0xFF) - (outPixel ushr 8 and 0xFF)
                sumB += (inPixel and 0xFF) - (outPixel and 0xFF)
            }
        }

        val output = IntArray(input.size)
        for (x in 0 until width) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (i in -radius..radius) {
                val pixel = horizontal[i.coerceIn(0, height - 1) * width + x]
                sumA += pixel ushr 24 and 0xFF
                sumR += pixel ushr 16 and 0xFF
                sumG += pixel ushr 8 and 0xFF
                sumB += pixel and 0xFF
            }
            for (y in 0 until height) {
                output[y * width + x] = pack(
                    sumA / denominator,
                    sumR / denominator,
                    sumG / denominator,
                    sumB / denominator,
                )
                val outPixel = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val inPixel = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                sumA += (inPixel ushr 24 and 0xFF) - (outPixel ushr 24 and 0xFF)
                sumR += (inPixel ushr 16 and 0xFF) - (outPixel ushr 16 and 0xFF)
                sumG += (inPixel ushr 8 and 0xFF) - (outPixel ushr 8 and 0xFF)
                sumB += (inPixel and 0xFF) - (outPixel and 0xFF)
            }
        }
        return output
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        (a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)

    private companion object {
        const val DIR_NAME = "bg"
        const val CACHE_DIR = "cache"
        const val LEGACY_DIR_NAME = "backgrounds"
        /** 单张上限 12MB（超出降采样到 2560 长边）。 */
        const val MAX_BYTES = 12 * 1024 * 1024
        const val SMALL_EDGE = 2560
        const val MAX_EDGE_SOFT = 4096
        const val JPEG_QUALITY = 88
        const val CACHE_QUALITY = 85
        const val MAX_IMAGES = 3
        const val MAX_CACHE_BYTES = 40L * 1024 * 1024
        const val BOX_PASSES = 3
        /** 六档模糊（§14.6.3），48dp 不进预渲染缓存（低端机上不会用到）。 */
        val BLUR_TIERS = listOf(0, 6, 12, 18, 24, 32)
    }
}
