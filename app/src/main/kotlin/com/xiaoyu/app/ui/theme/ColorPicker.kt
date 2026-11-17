package com.xiaoyu.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xiaoyu.app.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun parseHexColor(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return Color(
        red = ((rgb shr 16) and 0xFF).toFloat() / 255f,
        green = ((rgb shr 8) and 0xFF).toFloat() / 255f,
        blue = (rgb and 0xFF).toFloat() / 255f,
        alpha = 1f,
    )
}

fun colorToHex(color: Color): String = String.format(
    Locale.US,
    "#%02X%02X%02X",
    (color.red.coerceIn(0f, 1f) * 255f).roundToInt(),
    (color.green.coerceIn(0f, 1f) * 255f).roundToInt(),
    (color.blue.coerceIn(0f, 1f) * 255f).roundToInt(),
)

/** HEX + HSV 饱和度/明度二维板 + 色相滑杆 + 预设色板。 */
@Composable
fun YuColorPicker(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hex by remember { mutableStateOf(colorToHex(initialColor)) }

    fun publish(color: Color) {
        hex = colorToHex(color)
        onColorChange(color)
    }

    fun publishHsv() = publish(hsvToColor(hue, saturation, value))

    fun updateBoard(offset: Offset, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        saturation = (offset.x / width).coerceIn(0f, 1f)
        value = (1f - offset.y / height).coerceIn(0f, 1f)
        publishHsv()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Tokens.SpacingMd)) {
        OutlinedTextField(
            value = hex,
            onValueChange = { input ->
                hex = input
                parseHexColor(input)?.let { color ->
                    val hsv = colorToHsv(color)
                    hue = hsv[0]
                    saturation = hsv[1]
                    value = hsv[2]
                    onColorChange(color)
                }
            },
            label = { Text(stringResource(R.string.settings_custom_seed_hex)) },
            singleLine = true,
            isError = parseHexColor(hex) == null,
            modifier = Modifier.fillMaxWidth(),
        )
        val pureHue = hsvToColor(hue, 1f, 1f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(Tokens.SizeIllustration)
                .pointerInput(hue) {
                    detectTapGestures { offset -> updateBoard(offset, size.width, size.height) }
                }
                .pointerInput(hue) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        updateBoard(change.position, size.width, size.height)
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val center = Offset(saturation * size.width, (1f - value) * size.height)
            drawCircle(
                color = Color.White,
                radius = Tokens.SpacingSm.toPx(),
                center = center,
                style = Stroke(width = Tokens.SpacingXxs.toPx()),
            )
        }
        Text(stringResource(R.string.settings_custom_seed_hue), style = LocalYuType.current.meta)
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                publishHsv()
            },
            valueRange = 0f..360f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) {
            YuThemePreset.entries.forEach { preset ->
                val description = preset.seedHex
                Box(
                    Modifier
                        .size(Tokens.SizeMinTouch)
                        .clip(CircleShape)
                        .background(preset.seed)
                        .clickable {
                            val hsv = colorToHsv(preset.seed)
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                            publish(preset.seed)
                        }
                        .semantics { contentDescription = description }
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_custom_seed_preview),
            color = hsvToColor(hue, saturation, value),
            style = LocalYuType.current.caption,
        )
    }
}

private fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxValue = max(r, max(g, b))
    val minValue = min(r, min(g, b))
    val delta = maxValue - minValue
    val hue = when {
        delta == 0f -> 0f
        maxValue == r -> 60f * (((g - b) / delta) % 6f)
        maxValue == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (maxValue == 0f) 0f else delta / maxValue
    return floatArrayOf(hue, saturation, maxValue)
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val chroma = v * s
    val x = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val (r1, g1, b1) = when {
        h < 60f -> Triple(chroma, x, 0f)
        h < 120f -> Triple(x, chroma, 0f)
        h < 180f -> Triple(0f, chroma, x)
        h < 240f -> Triple(0f, x, chroma)
        h < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = v - chroma
    return Color(r1 + m, g1 + m, b1 + m, 1f)
}
