package com.xiaoyu.app.ui

import android.graphics.Movie
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaoyu.app.R
import com.xiaoyu.app.data.db.StickerEntity
import com.xiaoyu.app.data.db.StickerPackEntity
import com.xiaoyu.app.data.repo.BUILTIN_STICKER_PREFIX
import com.xiaoyu.app.data.repo.MIME_BUILTIN_CANVAS
import com.xiaoyu.app.data.sticker.StickerPaths
import com.xiaoyu.app.ui.theme.Tokens
import com.xiaoyu.app.ui.theme.yuSpring
import com.xiaoyu.app.ui.theme.yuTween
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.min

private val BodyColor = Color(0xFF8FDBF5)
private val BodyDark = Color(0xFF46AACB)
private val BellyColor = Color(0xFFD9F3FF)
private val Blush = Color(0xFFF7A8C0)
private val Ink = Color(0xFF2F7A93)

/**
 * 渲染一枚表情：内置小鱼用 Canvas 画，用户导入的读本地文件（执行书 7.4.4）。
 * 面板 / 列表传 [useThumbnail] = true 只加载 128px 缩略图；聊天预览用原图但限制解码边长。
 * 动图受同屏播放预算限制（[GifPlaybackBudget]），超出预算只显示首帧静态图。
 */
@Composable
fun StickerArt(
    sticker: StickerEntity?,
    size: Dp = 132.dp,
    modifier: Modifier = Modifier,
    useThumbnail: Boolean = false,
    playGif: Boolean = true,
) {
    if (sticker == null) {
        Box(modifier.size(size))
        return
    }
    if (sticker.mime == MIME_BUILTIN_CANVAS) {
        FishSticker(
            kind = sticker.filePath.removePrefix(BUILTIN_STICKER_PREFIX),
            modifier = modifier.size(size),
        )
        return
    }
    val original = remember(sticker.filePath) { File(sticker.filePath) }
    if (!original.exists()) {
        Box(modifier.size(size))
        return
    }
    val animated = playGif && sticker.mime == MIME_GIF
    if (animated && rememberGifSlot(true)) {
        AnimatedGif(file = original, modifier = modifier.size(size))
        return
    }
    val thumbnail = remember(sticker.filePath) {
        File(StickerPaths.thumbnailPath(sticker.filePath)).takeIf { it.isFile }
    }
    val usingThumbnail = useThumbnail && thumbnail != null
    val file = if (usingThumbnail) thumbnail!! else original
    FileImage(
        file = file,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        // 缩略图不需要再按大图解码，限制解码尺寸等价于 Coil 的 size() 约束
        maxEdge = if (usingThumbnail) THUMBNAIL_EDGE else PREVIEW_MAX_EDGE,
    )
}

private const val MIME_GIF = "image/gif"
private const val THUMBNAIL_EDGE = 128
private const val PREVIEW_MAX_EDGE = 480

/**
 * 同屏 GIF 播放上限（执行书 7.4.5）：最多 8 个同时解码播放，
 * 其余条目只显示缩略图/首帧，滚动与列表滑动时不会因解码排队掉帧。
 */
private object GifPlaybackBudget {
    private const val LIMIT = 8
    private var active = 0

    @Synchronized
    fun acquire(): Boolean {
        if (active >= LIMIT) return false
        active++
        return true
    }

    @Synchronized
    fun release() {
        if (active > 0) active--
    }
}

@Composable
private fun rememberGifSlot(enabled: Boolean): Boolean {
    var holding by remember(enabled) { mutableStateOf(false) }
    DisposableEffect(enabled) {
        holding = enabled && GifPlaybackBudget.acquire()
        onDispose {
            if (holding) GifPlaybackBudget.release()
            holding = false
        }
    }
    return holding
}

/** GIF 播放：只循环 3 遍（执行书 7.4.4），播完停在最后一帧，省电也避免满屏乱动。 */
@Suppress("DEPRECATION")
@Composable
private fun AnimatedGif(file: File, modifier: Modifier = Modifier) {
    val movie = remember(file.path, file.lastModified()) {
        runCatching { Movie.decodeFile(file.path) }.getOrNull()
    }
    var tick by remember(movie) { mutableIntStateOf(0) }
    if (movie == null) {
        // 少数 GIF 用 Movie 解不出来，退回首帧静态图，避免空白
        FileImage(file = file, modifier = modifier, contentScale = ContentScale.Fit, maxEdge = PREVIEW_MAX_EDGE)
        return
    }
    val duration = movie.duration()
    LaunchedEffect(movie) {
        if (duration <= 0) return@LaunchedEffect
        val start = SystemClock.uptimeMillis()
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - start
            if (elapsed > duration * REPEAT_COUNT) {
                movie.setTime(duration - 1)
                tick++
                break
            }
            movie.setTime((elapsed % duration).toInt())
            tick++
            delay(FRAME_INTERVAL_MS)
        }
    }
    Canvas(modifier) {
        // 读取 tick 让动画帧推进时触发重绘
        if (tick < 0) return@Canvas
        val gif = movie
        if (gif.width() <= 0 || gif.height() <= 0) return@Canvas
        val scale = min(size.width / gif.width(), size.height / gif.height())
        val dx = (size.width - gif.width() * scale) / 2f
        val dy = (size.height - gif.height() * scale) / 2f
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                save()
                translate(dx, dy)
                scale(scale, scale)
                gif.draw(this, 0f, 0f)
                restore()
            }
        }
    }
}

private const val REPEAT_COUNT = 3L
private const val FRAME_INTERVAL_MS = 33L

/**
 * 内置小鱼的代码绘制（CC0 / 无二进制素材，R11）。
 * 8 种情绪：happy / sad / angry / cute / speechless / night / think / heart。
 */
@Composable
fun FishSticker(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()

        // 尾巴
        drawPath(
            Path().apply {
                moveTo(w * 0.10f, h * 0.26f)
                lineTo(w * 0.10f, h * 0.74f)
                lineTo(w * 0.34f, h * 0.50f)
                close()
            },
            color = BodyDark,
        )
        // 背鳍
        drawPath(
            Path().apply {
                moveTo(w * 0.40f, h * 0.20f)
                lineTo(w * 0.52f, h * 0.06f)
                lineTo(w * 0.62f, h * 0.20f)
                close()
            },
            color = BodyDark,
        )
        // 身体
        drawOval(
            color = BodyColor,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.78f, h * 0.68f),
        )
        drawOval(
            color = BodyDark,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.78f, h * 0.68f),
            style = Stroke(width = stroke),
        )
        // 肚皮
        drawOval(
            color = BellyColor,
            topLeft = Offset(w * 0.36f, h * 0.52f),
            size = Size(w * 0.56f, h * 0.28f),
        )
        // 腮红
        drawCircle(Blush.copy(alpha = 0.75f), w * 0.05f, Offset(w * 0.46f, h * 0.58f))
        drawCircle(Blush.copy(alpha = 0.75f), w * 0.05f, Offset(w * 0.86f, h * 0.58f))

        val eyeL = Offset(w * 0.56f, h * 0.42f)
        val eyeR = Offset(w * 0.79f, h * 0.42f)
        val eyeRr = w * 0.085f
        val mouth = Offset(w * 0.70f, h * 0.62f)

        fun openEyes() {
            drawCircle(Color.White, eyeRr, eyeL)
            drawCircle(Color.White, eyeRr, eyeR)
            drawCircle(Ink, eyeRr * 0.52f, Offset(eyeL.x + eyeRr * 0.18f, eyeL.y + eyeRr * 0.12f))
            drawCircle(Ink, eyeRr * 0.52f, Offset(eyeR.x + eyeRr * 0.18f, eyeR.y + eyeRr * 0.12f))
        }

        fun happyEyes() {
            drawArc(
                color = Ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(eyeL.x - eyeRr, eyeL.y - eyeRr * 0.5f),
                size = Size(eyeRr * 2, eyeRr * 1.6f),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(eyeR.x - eyeRr, eyeR.y - eyeRr * 0.5f),
                size = Size(eyeRr * 2, eyeRr * 1.6f),
                style = Stroke(width = stroke),
            )
        }

        fun mouthArc(start: Float, sweep: Float) {
            val mw = w * 0.20f
            val mh = w * 0.12f
            drawArc(
                color = Ink,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(mouth.x - mw / 2, mouth.y - mh / 2),
                size = Size(mw, mh),
                style = Stroke(width = stroke),
            )
        }

        fun text(value: String, x: Float, y: Float, textSize: Float) {
            drawContext.canvas.nativeCanvas.drawText(
                value,
                x,
                y,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(220, 47, 122, 147)
                    this.textSize = textSize
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
            )
        }

        when (kind) {
            "happy" -> {
                happyEyes()
                mouthArc(20f, 140f)
                drawCircle(BodyDark.copy(alpha = 0.6f), w * 0.02f, Offset(w * 0.95f, h * 0.30f))
                drawCircle(BodyDark.copy(alpha = 0.45f), w * 0.032f, Offset(w * 0.99f, h * 0.16f))
            }
            "sad" -> {
                openEyes()
                // 八字眉向中间抬起，才是委屈而不是生气
                drawLine(Ink, Offset(w * 0.45f, h * 0.35f), Offset(w * 0.62f, h * 0.30f), strokeWidth = stroke)
                drawLine(Ink, Offset(w * 0.90f, h * 0.35f), Offset(w * 0.73f, h * 0.30f), strokeWidth = stroke)
                // 眼泪
                drawPath(
                    Path().apply {
                        moveTo(w * 0.50f, h * 0.52f)
                        quadraticBezierTo(w * 0.44f, h * 0.62f, w * 0.50f, h * 0.66f)
                        quadraticBezierTo(w * 0.56f, h * 0.62f, w * 0.50f, h * 0.52f)
                        close()
                    },
                    color = Color(0xFF3FA6E8),
                )
                mouthArc(200f, 140f)
            }
            "angry" -> {
                openEyes()
                drawLine(Ink, Offset(w * 0.45f, h * 0.28f), Offset(w * 0.62f, h * 0.34f), strokeWidth = stroke)
                drawLine(Ink, Offset(w * 0.90f, h * 0.28f), Offset(w * 0.73f, h * 0.34f), strokeWidth = stroke)
                drawPath(
                    Path().apply {
                        moveTo(mouth.x - w * 0.10f, mouth.y)
                        lineTo(mouth.x - w * 0.05f, mouth.y - h * 0.04f)
                        lineTo(mouth.x, mouth.y + h * 0.02f)
                        lineTo(mouth.x + w * 0.05f, mouth.y - h * 0.04f)
                        lineTo(mouth.x + w * 0.10f, mouth.y)
                    },
                    color = Ink,
                    style = Stroke(width = stroke),
                )
                // 怒气泡
                drawCircle(Color(0xFFFF8B8B), w * 0.035f, Offset(w * 0.90f, h * 0.20f))
            }
            "cute" -> {
                fun sparkle(c: Offset) {
                    drawLine(Ink, Offset(c.x - eyeRr, c.y), Offset(c.x + eyeRr, c.y), strokeWidth = stroke)
                    drawLine(Ink, Offset(c.x, c.y - eyeRr), Offset(c.x, c.y + eyeRr), strokeWidth = stroke)
                    drawCircle(Color(0xFFFF9AC0), eyeRr * 0.32f, c)
                }
                sparkle(eyeL)
                sparkle(eyeR)
                drawCircle(Ink, w * 0.035f, Offset(mouth.x, mouth.y))
                drawCircle(Blush.copy(alpha = 0.95f), w * 0.06f, Offset(w * 0.42f, h * 0.60f))
                drawCircle(Blush.copy(alpha = 0.95f), w * 0.06f, Offset(w * 0.90f, h * 0.60f))
            }
            "speechless" -> {
                drawLine(Ink, Offset(eyeL.x - eyeRr, eyeL.y), Offset(eyeL.x + eyeRr, eyeL.y), strokeWidth = stroke)
                drawLine(Ink, Offset(eyeR.x - eyeRr, eyeR.y), Offset(eyeR.x + eyeRr, eyeR.y), strokeWidth = stroke)
                drawLine(Ink, Offset(mouth.x - w * 0.07f, mouth.y), Offset(mouth.x + w * 0.07f, mouth.y), strokeWidth = stroke)
                drawCircle(Ink.copy(alpha = 0.5f), w * 0.016f, Offset(w * 0.66f, h * 0.22f))
                drawCircle(Ink.copy(alpha = 0.65f), w * 0.02f, Offset(w * 0.73f, h * 0.18f))
                drawCircle(Ink.copy(alpha = 0.8f), w * 0.024f, Offset(w * 0.81f, h * 0.14f))
            }
            "night" -> {
                drawArc(
                    color = Ink,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(eyeL.x - eyeRr, eyeL.y - eyeRr * 0.4f),
                    size = Size(eyeRr * 2, eyeRr * 1.5f),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = Ink,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(eyeR.x - eyeRr, eyeR.y - eyeRr * 0.4f),
                    size = Size(eyeRr * 2, eyeRr * 1.5f),
                    style = Stroke(width = stroke),
                )
                mouthArc(20f, 140f)
                drawCircle(Color(0xFFF7D06B), w * 0.035f, Offset(w * 0.86f, h * 0.14f))
                drawCircle(Color(0xFFF7D06B), w * 0.022f, Offset(w * 0.93f, h * 0.22f))
                drawCircle(Color(0xFFF7D06B), w * 0.016f, Offset(w * 0.78f, h * 0.09f))
                text("z", w * 0.66f, h * 0.20f, w * 0.14f)
                text("z", w * 0.72f, h * 0.13f, w * 0.10f)
            }
            "think" -> {
                openEyes()
                drawLine(Ink, Offset(w * 0.72f, h * 0.29f), Offset(w * 0.86f, h * 0.26f), strokeWidth = stroke)
                drawCircle(Ink, w * 0.04f, mouth, style = Stroke(width = stroke))
                text("?", w * 0.86f, h * 0.22f, w * 0.18f)
            }
            "heart" -> {
                drawPath(heartPath(eyeL.x, eyeL.y, eyeRr * 1.15f), color = Color(0xFFFF6E8A))
                drawPath(heartPath(eyeR.x, eyeR.y, eyeRr * 1.15f), color = Color(0xFFFF6E8A))
                mouthArc(20f, 140f)
                drawPath(heartPath(w * 0.26f, h * 0.14f, w * 0.06f), color = Color(0xFFFF8FA6))
            }
            else -> {
                openEyes()
                mouthArc(20f, 140f)
            }
        }
    }
}

private fun heartPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy + r * 0.78f)
    cubicTo(cx - r * 1.25f, cy - r * 0.10f, cx - r * 0.55f, cy - r * 0.95f, cx, cy - r * 0.35f)
    cubicTo(cx + r * 0.55f, cy - r * 0.95f, cx + r * 1.25f, cy - r * 0.10f, cx, cy + r * 0.78f)
    close()
}

/** 输入栏上方的表情面板（执行书 7.4.4）：分包 tab + 四列网格，点一下直接发出去。 */
@Composable
fun StickerPanel(
    stickers: List<StickerEntity>,
    onPick: (StickerEntity) -> Unit,
    modifier: Modifier = Modifier,
    packs: List<StickerPackEntity> = emptyList(),
    onManage: (() -> Unit)? = null,
    onPreview: ((StickerEntity) -> Unit)? = null,
) {
    // null = 全部；只有真的存在多个有内容的分包时才显示 tab
    var selectedPack by remember { mutableStateOf<String?>(null) }
    val usedPacks = remember(packs, stickers) {
        packs.filter { pack -> pack.enabled && stickers.any { it.packId == pack.id } }
    }
    val shown = remember(stickers, selectedPack) {
        if (selectedPack == null) stickers else stickers.filter { it.packId == selectedPack }
    }

    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.sticker_panel_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (onManage != null) {
                    TextButton(
                        onClick = onManage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text(stringResource(R.string.sticker_manage), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (usedPacks.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("all") {
                        PackChip(stringResource(R.string.sticker_pack_all), selected = selectedPack == null) { selectedPack = null }
                    }
                    items(usedPacks, key = { it.id }) { pack ->
                        PackChip(pack.name, selected = selectedPack == pack.id) { selectedPack = pack.id }
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.sticker_panel_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 248.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    gridItems(shown, key = { it.id }) { sticker ->
                        StickerCell(sticker = sticker, onPick = onPick, onPreview = onPreview)
                    }
                }
            }
        }
    }
}

/** 分包筛选胶囊：选中时品牌色填充，切换用颜色过渡而不是硬切。 */
@Composable
private fun PackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        animationSpec = yuTween(durationMillis = 160),
        label = "packChipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = yuTween(durationMillis = 160),
        label = "packChipFg",
    )
    Box(
        modifier = Modifier
            .heightIn(min = Tokens.SizeTouchTarget)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 单格表情：按下缩到 0.92 再弹回，松开才触发发送；长按看大图。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: StickerEntity,
    onPick: (StickerEntity) -> Unit,
    onPreview: ((StickerEntity) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val stickerFallback = stringResource(R.string.chat_a11y_sticker)
    val stickerDescription = sticker.name.trim().ifBlank { stickerFallback }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = yuSpring(dampingRatio = 0.6f, stiffness = 720f),
        label = "stickerScale",
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onPick(sticker) },
                onLongClick = onPreview?.let { { it(sticker) } },
            )
            .semantics { contentDescription = stickerDescription },
        contentAlignment = Alignment.Center,
    ) {
        // 面板只读缩略图、不播动图，保证滚动 60fps（执行书 7.4.5）
        StickerArt(sticker = sticker, size = 58.dp, useThumbnail = true, playGif = false)
    }
}
