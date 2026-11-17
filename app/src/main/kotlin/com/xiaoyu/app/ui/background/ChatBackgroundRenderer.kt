package com.xiaoyu.app.ui.background

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Fill
import com.xiaoyu.app.data.repo.BackgroundRenderInput
import com.xiaoyu.app.data.settings.ChatBackgroundSpec
import com.xiaoyu.app.ui.LegibilityGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 气泡层参数（第 5 层），由渲染器通过 CompositionLocal 下发给气泡组件。 */
@Immutable
data class BubbleVisual(
    /** 气泡背景不透明度（0.55~1.0）。 */
    val alpha: Float = 0.88f,
    /** 白色/主题色底衬（只在「气泡透明」调低时有意义）。 */
    val underlay: Boolean = true,
    /** 气泡 backdrop 模糊（仅 API 33+，默认 0）。 */
    val blurDp: Float = 0f,
    /** 深色底浅色字时的 0.5dp 阴影（守卫第③步）。 */
    val textShadow: Boolean = false,
)

val LocalBubbleVisual = compositionLocalOf { BubbleVisual() }

/** 守卫结果（设置页 / 聊天页提示用）。 */
val LocalLegibility = staticCompositionLocalOf<LegibilityGuard.Result?> { null }

/**
 * 五层聊天背景渲染器（执行书 v3 §14.6.2 / §14.6.3）。
 *
 * ① 素材层 theme/solid/gradient/image/texture（cover/contain/tile、zoom、锚点）
 * ② 效果层 blur → brightness → saturation
 * ③ 遮罩层 scrim 色 + alpha + 可选上下渐变
 * ④ 暗角 vignette（只压边缘）
 * ⑤ 气泡层：通过 [LocalBubbleVisual] 交给气泡组件
 *
 * API ≥31 用 `Modifier.blur`；26–30 用 [BackgroundRenderInput.preRenderedFile]；
 * 低端机模糊上限 18dp 且禁用视差。
 */
@Composable
fun ChatBackgroundRenderer(
    input: BackgroundRenderInput?,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    parallaxOffsetPx: State<Float> = remember { mutableFloatStateOf(0f) },
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = input?.spec ?: ChatBackgroundSpec()
    val imageFile = input?.imageFile
    val renderFile = input?.preRenderedFile ?: imageFile
    val image by rememberBitmap(renderFile)

    val surfaceLum = LegibilityGuard.luminanceOf(MaterialTheme.colorScheme.surface.toArgb())
    val luminance = rememberLuminance(spec, image, surfaceLum)
    val legibility = remember(spec, luminance, darkTheme) {
        LegibilityGuard.evaluate(spec, luminance, darkTheme)
    }

    val scrimAlpha = legibility.scrimAlpha
    val bubbleAlpha = legibility.bubbleAlpha
    val bubbleBlur = if (
        spec.bubbleBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && input?.lowRam != true
    ) {
        BUBBLE_BLUR_DP
    } else {
        0f
    }

    val parallax = if (input?.lowRam == true || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        0
    } else {
        spec.parallax
    }

    CompositionLocalProvider(
        LocalBubbleVisual provides BubbleVisual(
            alpha = bubbleAlpha / 100f,
            underlay = spec.bubbleUnderlay,
            blurDp = bubbleBlur,
            textShadow = legibility.textShadow,
        ),
        LocalLegibility provides legibility,
    ) {
        Box(modifier) {
            MaterialLayer(
                spec = spec,
                image = image,
                input = input,
                darkTheme = darkTheme,
                parallax = parallax,
                parallaxOffsetPx = parallaxOffsetPx,
            )
            ScrimLayer(spec = spec, scrimAlpha = scrimAlpha)
            VignetteLayer(spec = spec)
            content()
        }
    }
}

/** ① 素材层 + ② 效果层（模糊 / 亮度 / 饱和）。 */
@Composable
private fun MaterialLayer(
    spec: ChatBackgroundSpec,
    image: Bitmap?,
    input: BackgroundRenderInput?,
    darkTheme: Boolean,
    parallax: Int,
    parallaxOffsetPx: State<Float>,
) {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val layoutDirection = LocalLayoutDirection.current

    val effectiveBlur = input?.effectiveBlurDp ?: spec.blurDp
    val blurModifier = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && effectiveBlur > 0 && image != null &&
        input?.fromCache != true
    ) {
        Modifier.blur(
            effectiveBlur.toFloat().dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
    } else {
        Modifier
    }
    val textureTile = remember(spec.texture, onSurface, darkTheme) {
        TexturePainter.tile(spec.texture, fg = onSurface, bg = Color.Transparent)
    }
    val gradient = remember(spec.gradientJson, primary) {
        parseGradient(spec.gradientJson, primary, Color.Transparent)
    }
    val zoom = spec.zoom / 100f
    val colorFilter = remember(spec.brightness, spec.saturation) {
        effectColorFilter(spec.brightness, spec.saturation)
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                if (parallax > 0) {
                    translationY = -parallaxOffsetPx.value * (parallax / 100f) * 0.15f
                    scaleX = 1.12f
                    scaleY = 1.12f
                }
            }
            .then(blurModifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (spec.source) {
                ChatBackgroundSpec.SOURCE_SOLID -> {
                    drawRect(transformColor(parseColor(spec.solidHex, surface), spec.brightness, spec.saturation))
                    drawTexture(textureTile, alpha = 0.08f)
                }

                ChatBackgroundSpec.SOURCE_GRADIENT -> {
                    drawRect(gradientBrush(gradient, spec, surface, primary))
                    drawTexture(textureTile, alpha = 0.05f)
                }

                ChatBackgroundSpec.SOURCE_TEXTURE -> {
                    drawRect(transformColor(surface, spec.brightness, spec.saturation))
                    drawTexture(textureTile, alpha = 0.9f)
                }

                ChatBackgroundSpec.SOURCE_IMAGE -> {
                    drawRect(transformColor(surface, spec.brightness, spec.saturation))
                    if (image != null) {
                        drawImageLayer(image, spec, zoom, layoutDirection, colorFilter)
                    } else {
                        drawTexture(textureTile, alpha = 0.08f)
                    }
                }

                else -> {
                    drawRect(transformColor(surface, spec.brightness, spec.saturation))
                    drawTexture(textureTile, alpha = 0.06f)
                }
            }
        }
    }
}

private fun DrawScope.drawTexture(tile: ImageBitmap, alpha: Float) {
    drawRect(
        brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)),
        alpha = alpha,
    )
}

private fun DrawScope.drawImageLayer(
    bitmap: Bitmap,
    spec: ChatBackgroundSpec,
    zoom: Float,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    colorFilter: ColorFilter,
) {
    val image = bitmap.asImageBitmap()
    val srcSize = IntSize(bitmap.width, bitmap.height)
    val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
    val alignment = anchorOf(spec.anchor)
    when (spec.scale) {
        ChatBackgroundSpec.SCALE_CONTAIN -> {
            val scale = min(
                dstSize.width.toFloat() / srcSize.width,
                dstSize.height.toFloat() / srcSize.height,
            ) * zoom
            val drawSize = IntSize(
                (srcSize.width * scale).roundToInt().coerceAtLeast(1),
                (srcSize.height * scale).roundToInt().coerceAtLeast(1),
            )
            val offset = alignment.align(drawSize, dstSize, layoutDirection)
            drawImage(
                image = image,
                dstOffset = offset,
                dstSize = drawSize,
                colorFilter = colorFilter,
                style = Fill,
            )
        }

        ChatBackgroundSpec.SCALE_TILE -> {
            val tileW = (srcSize.width * zoom).roundToInt().coerceAtLeast(1)
            val tileH = (srcSize.height * zoom).roundToInt().coerceAtLeast(1)
            var y = 0
            while (y < size.height) {
                var x = 0
                while (x < size.width) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(x, y),
                        dstSize = IntSize(tileW, tileH),
                        colorFilter = colorFilter,
                        style = Fill,
                    )
                    x += tileW
                }
                y += tileH
            }
        }

        else -> {
            val scale = max(
                dstSize.width.toFloat() / srcSize.width,
                dstSize.height.toFloat() / srcSize.height,
            ) * zoom
            val drawSize = IntSize(
                (srcSize.width * scale).roundToInt().coerceAtLeast(1),
                (srcSize.height * scale).roundToInt().coerceAtLeast(1),
            )
            val offset = alignment.align(drawSize, dstSize, layoutDirection)
            drawImage(
                image = image,
                dstOffset = offset,
                dstSize = drawSize,
                colorFilter = colorFilter,
                style = Fill,
            )
        }
    }
}

/** ③ 遮罩层：颜色 + alpha + 可选上下渐变。 */
@Composable
private fun ScrimLayer(spec: ChatBackgroundSpec, scrimAlpha: Int) {
    val base = when (spec.scrimTone) {
        ChatBackgroundSpec.SCRIM_LIGHT -> Color.White
        ChatBackgroundSpec.SCRIM_CUSTOM -> parseColor(spec.scrimHex, Color.Black)
        else -> Color.Black
    }
    val alpha = (scrimAlpha / 100f).coerceIn(0f, 0.8f)
    if (alpha <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base.copy(alpha = alpha))
        // 渐变 scrim：顶部/底部各加一档（§14.6.2 第 3 层）
        val band = size.height * 0.22f
        drawRect(
            Brush.verticalGradient(
                colors = listOf(base.copy(alpha = alpha * 0.6f), Color.Transparent),
                startY = 0f,
                endY = band,
            ),
        )
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, base.copy(alpha = alpha * 0.6f)),
                startY = size.height - band,
                endY = size.height,
            ),
        )
    }
}

/** ④ 暗角：径向渐变，只压边缘。 */
@Composable
private fun VignetteLayer(spec: ChatBackgroundSpec) {
    val strength = (spec.vignette / 100f).coerceIn(0f, 0.6f)
    if (strength <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val radius = max(size.width, size.height)
        drawRect(
            Brush.radialGradient(
                0.72f to Color.Transparent,
                1f to Color.Black.copy(alpha = strength),
                center = center,
                radius = radius,
            ),
        )
    }
}

// ---------------- 工具 ----------------

private const val BUBBLE_BLUR_DP = 8f

private fun parseColor(hex: String?, fallback: Color): Color {
    val argb = LegibilityGuard.parseHex(hex) ?: return fallback
    return Color(argb)
}

@Serializable
internal data class GradientDef(
    val type: String = "linear",
    val from: String = "#FFFFFF",
    val to: String = "#000000",
    val angle: Int = 135,
)

private val gradientJson = Json { ignoreUnknownKeys = true }

internal fun parseGradient(json: String?, fallbackFrom: Color, fallbackTo: Color): GradientDef =
    runCatching { gradientJson.decodeFromString<GradientDef>(json.orEmpty()) }
        .getOrElse { GradientDef(from = fallbackFrom.toHex(), to = fallbackTo.toHex()) }

private fun DrawScope.gradientBrush(
    def: GradientDef,
    spec: ChatBackgroundSpec,
    fallbackFrom: Color,
    fallbackTo: Color,
): Brush {
    val from = transformColor(parseColor(def.from, fallbackFrom), spec.brightness, spec.saturation)
    val to = transformColor(parseColor(def.to, fallbackTo), spec.brightness, spec.saturation)
    return when (def.type) {
        "radial" -> Brush.radialGradient(
            colors = listOf(from, to),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = max(size.width, size.height) / 1.4f,
        )
        else -> {
            val radians = Math.toRadians(def.angle.toDouble())
            val dx = cos(radians).toFloat()
            val dy = sin(radians).toFloat()
            val half = Offset(size.width / 2f, size.height / 2f)
            val start = Offset(half.x - dx * size.width / 2f, half.y - dy * size.height / 2f)
            val end = Offset(half.x + dx * size.width / 2f, half.y + dy * size.height / 2f)
            Brush.linearGradient(colors = listOf(from, to), start = start, end = end)
        }
    }
}

/** 亮度 / 饱和度近似（用于纯色与渐变；图片走 ColorMatrix）。 */
private fun transformColor(color: Color, brightness: Int, saturation: Int): Color {
    if (brightness == 0 && saturation == 100) return color
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * (saturation / 100f)).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * (1f + brightness / 100f)).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun effectColorFilter(brightness: Int, saturation: Int): ColorFilter {
    val matrix = ColorMatrix()
    matrix.setToSaturation((saturation / 100f).coerceIn(0f, 1.2f))
    val scale = 1f + brightness / 100f
    for (row in 0..2) {
        for (column in 0..4) {
            matrix[row, column] = matrix[row, column] * scale
        }
    }
    return ColorFilter.colorMatrix(matrix)
}

private fun anchorOf(anchor: String): Alignment = when (anchor) {
    "topStart" -> Alignment.TopStart
    "topCenter" -> Alignment.TopCenter
    "topEnd" -> Alignment.TopEnd
    "centerStart" -> Alignment.CenterStart
    "centerEnd" -> Alignment.CenterEnd
    "bottomStart" -> Alignment.BottomStart
    "bottomCenter" -> Alignment.BottomCenter
    "bottomEnd" -> Alignment.BottomEnd
    else -> Alignment.Center
}

private fun Color.toHex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

@Composable
private fun rememberBitmap(file: File?): State<Bitmap?> =
    produceState<Bitmap?>(null, file?.path, file?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            if (file == null || !file.isFile) return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_EDGE) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(
                    file.path,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
        }
    }

@Composable
private fun rememberLuminance(
    spec: ChatBackgroundSpec,
    bitmap: Bitmap?,
    surfaceLuminance: Double,
): Double {
    val estimate = when (spec.source) {
        ChatBackgroundSpec.SOURCE_IMAGE -> null
        ChatBackgroundSpec.SOURCE_SOLID ->
            LegibilityGuard.luminanceOf(spec.solidHex.toArgbFallback())
        ChatBackgroundSpec.SOURCE_GRADIENT -> {
            val def = parseGradient(spec.gradientJson, Color.White, Color.Black)
            val from = LegibilityGuard.luminanceOf(LegibilityGuard.parseHex(def.from) ?: 0xFFFFFFFF.toInt())
            val to = LegibilityGuard.luminanceOf(LegibilityGuard.parseHex(def.to) ?: 0xFF000000.toInt())
            (from + to) / 2
        }
        else -> surfaceLuminance
    }
    return remember(spec.source, spec.solidHex, spec.gradientJson, bitmap, estimate) {
        if (spec.source == ChatBackgroundSpec.SOURCE_IMAGE) {
            LegibilityGuard.averageLuminance(bitmap)
        } else {
            estimate ?: surfaceLuminance
        }
    }
}

private fun String.toArgbFallback(): Int = LegibilityGuard.parseHex(this) ?: 0xFFFFFFFF.toInt()

private const val MAX_DECODE_EDGE = 1440
