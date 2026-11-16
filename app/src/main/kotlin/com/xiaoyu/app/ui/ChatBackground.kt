package com.xiaoyu.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * 聊天区背景：整块铺满图片 + 可调模糊与暗化蒙层。
 * 模糊仅 Android 12+ 生效，低版本自动退化为暗化蒙层，不影响文字可读性。
 *
 * v3 H-24 / 14.6：暗化蒙层透明度会经 LegibilityGuard 校验，
 * 自动抬到保证 WCAG AA（4.5:1）对比度的下限，避免文字糊在背景里。
 */
@Composable
fun ChatBackground(
    file: File,
    blurDp: Float,
    dim: Float,
    darkTheme: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val image by rememberFileImage(file)
    Box(modifier) {
        val bitmap = image
        if (bitmap != null) {
            val blurModifier = if (blurDp > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(blurDp.dp, edgeTreatment = BlurredEdgeTreatment(RectangleShape))
            } else {
                Modifier
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clipToBounds().then(blurModifier),
            )
            // v3：用原图（非模糊后）计算所需最小暗化，保证文字对比度
            val minDim = LegibilityGuard.minDimForContrast(bitmap, darkTheme)
            val effectiveDim = max(dim, minDim)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = effectiveDim.coerceIn(0f, 0.85f))),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)))
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.85f))),
            )
        }
    }
}

/** 设置页缩略图用：只显示图片，不加蒙层。 */
@Composable
fun FileImage(
    file: File,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxEdge: Int = 720,
) {
    val image by rememberFileImage(file, maxEdge)
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun rememberFileImage(file: File, maxEdge: Int = MAX_DISPLAY_EDGE): State<Bitmap?> =
    produceState<Bitmap?>(null, file.path, file.lastModified(), maxEdge) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(
                    file.path,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
        }
    }

private const val MAX_DISPLAY_EDGE = 1440
