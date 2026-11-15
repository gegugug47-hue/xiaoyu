package com.xiaoyu.app.ui

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
 */
@Composable
fun ChatBackground(
    file: File,
    blurDp: Float,
    dim: Float,
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
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clipToBounds().then(blurModifier),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.85f))),
        )
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
        Image(bitmap = bitmap, contentDescription = null, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun rememberFileImage(file: File, maxEdge: Int = MAX_DISPLAY_EDGE): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, file.path, file.lastModified(), maxEdge) {
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
                )?.asImageBitmap()
            }.getOrNull()
        }
    }

private const val MAX_DISPLAY_EDGE = 1440
