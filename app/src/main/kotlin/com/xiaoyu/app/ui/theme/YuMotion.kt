package com.xiaoyu.app.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

object YuMotion {
    const val Flash = 80
    const val Quick = 150
    const val Normal = 250
    const val Slow = 400
    const val Page = 350
    const val Emphasis = 600

    val FlashEasing: Easing = LinearOutSlowInEasing
    val StandardEasing: Easing = FastOutSlowInEasing
    val EmphasisEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

data class YuMotionDurations(
    val flash: Int = YuMotion.Flash,
    val quick: Int = YuMotion.Quick,
    val normal: Int = YuMotion.Normal,
    val slow: Int = YuMotion.Slow,
    val page: Int = YuMotion.Page,
    val emphasis: Int = YuMotion.Emphasis,
    val reduced: Boolean = false,
) {
    fun resolve(baseDuration: Int): Int = if (reduced) 0 else baseDuration
}

fun resolveMotionDurations(
    reduceMotion: Boolean,
    animatorDurationScale: Float,
    transitionAnimationScale: Float,
): YuMotionDurations {
    val reduced = reduceMotion || animatorDurationScale == 0f || transitionAnimationScale == 0f
    return if (reduced) {
        YuMotionDurations(0, 0, 0, 0, 0, 0, reduced = true)
    } else {
        YuMotionDurations()
    }
}

val LocalYuMotion = staticCompositionLocalOf { YuMotionDurations() }

@Composable
fun rememberYuMotion(reduceMotion: Boolean): YuMotionDurations {
    val context = LocalContext.current
    val systemReduced = remember { mutableStateOf(readSystemMotionReduced(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemReduced.value = readSystemMotionReduced(context)
            }
        }
        val uris = listOf(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
        )
        uris.forEach { resolver.registerContentObserver(it, false, observer) }
        systemReduced.value = readSystemMotionReduced(context)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val animatorScale = if (systemReduced.value) 0f else 1f
    return remember(reduceMotion, systemReduced.value) {
        resolveMotionDurations(reduceMotion, animatorScale, 1f)
    }
}

private fun readSystemMotionReduced(context: android.content.Context): Boolean {
    val resolver = context.contentResolver
    val animator = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val transition = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }.getOrDefault(1f)
    return animator == 0f || transition == 0f
}

@Composable
fun <T> yuTween(
    durationMillis: Int,
    easing: Easing = YuMotion.StandardEasing,
): TweenSpec<T> = tween(LocalYuMotion.current.resolve(durationMillis), easing = easing)

@Composable
fun <T> yuSpring(
    dampingRatio: Float = 0.72f,
    stiffness: Float = 420f,
): AnimationSpec<T> = if (LocalYuMotion.current.reduced) {
    snap()
} else {
    spring(dampingRatio = dampingRatio, stiffness = stiffness)
}
