package com.xiaoyu.app.data.settings

import kotlinx.serialization.Serializable

/**
 * 自定义聊天背景参数（执行书 v3 §14.6.2 参数表 18 项）。
 *
 * 键名与 `chat_backgrounds` 列一一对应；默认值即文档「柔和」预设。
 * 所有区间校验集中在 [validate]，UI 只负责把滑杆值映射到这里。
 */
@Serializable
data class ChatBackgroundSpec(
    /** 素材来源：theme / solid / gradient / image / texture。 */
    val source: String = SOURCE_THEME,
    /** 图片来源的 sha1 命名（files/bg/<sha1>.jpg）。 */
    val imageSha1: String? = null,
    /** 渐变定义：{"type":"linear|radial","from":"#AABBCC","to":"#DDEEFF","angle":135}。 */
    val gradientJson: String? = null,
    /** 程序化纹理：linen / paper / grid / waves。 */
    val texture: String = TEXTURE_PAPER,
    /** 纯色来源的颜色。 */
    val solidHex: String = "#FFFBFE",
    /** 平铺方式：cover / contain / tile。 */
    val scale: String = SCALE_COVER,
    /** 缩放 100–160%，步进 5。 */
    val zoom: Int = 100,
    /** 锚点：topStart/topCenter/…/bottomEnd。 */
    val anchor: String = ANCHOR_CENTER,
    /** 模糊 0–48dp，吸附六档。 */
    val blurDp: Int = 12,
    /** 亮度 −40%～+20%。 */
    val brightness: Int = -12,
    /** 饱和度 0–120%。 */
    val saturation: Int = 70,
    /** 遮罩色：auto / light / dark / custom。 */
    val scrimTone: String = SCRIM_AUTO,
    /** 自定义遮罩色（scrimTone=custom 时生效）。 */
    val scrimHex: String? = null,
    /** 遮罩浓度 0–80%，步进 5。 */
    val scrimAlpha: Int = 45,
    /** 暗角 0–60%，步进 5。 */
    val vignette: Int = 18,
    /** 气泡不透明度 55–100%。 */
    val bubbleAlpha: Int = 88,
    /** 气泡白色底衬。 */
    val bubbleUnderlay: Boolean = true,
    /** 气泡背景模糊（仅 API 33+）。 */
    val bubbleBlur: Boolean = false,
    /** 视差 0 / 15 / 30%。 */
    val parallax: Int = 0,
    /** 自动保持可读（不合格时按 §14.6.4 五步兜底）。 */
    val legibilityAuto: Boolean = true,
    /** 深色模式单独一套参数（非空即启用）。 */
    val darkJson: String? = null,
) {

    /** 是否真的配置了可见背景；false 时保持原有主题底。 */
    val isEnabled: Boolean
        get() = source != SOURCE_THEME || imageSha1 != null

    /** 区间校验，返回人类可读的问题描述；空表示合法。 */
    fun validate(): List<String> {
        val problems = ArrayList<String>(3)
        if (source !in SOURCES) problems += "来源必须是 ${SOURCES.joinToString("/")}"
        if (blurDp !in BLUR_TIERS) problems += "模糊只能是 ${BLUR_TIERS.joinToString("/")}"
        if (bubbleAlpha !in 55..100) problems += "气泡不透明度需在 55–100"
        if (scrimAlpha !in 0..80) problems += "遮罩浓度需在 0–80"
        if (brightness !in -40..20) problems += "亮度需在 −40%–+20%"
        if (saturation !in 0..120) problems += "饱和度需在 0–120%"
        if (vignette !in 0..60) problems += "暗角需在 0–60%"
        if (zoom !in 100..160) problems += "缩放需在 100–160%"
        if (parallax !in PARALLAX_LEVELS) problems += "视差只能是 ${PARALLAX_LEVELS.joinToString("/")}"
        // §14.6.2 校验：遮罩 + 气泡透出的部分不能超过 88
        if (scrimAlpha + (100 - bubbleAlpha) / 2 > 88) problems += "遮罩与气泡透明度叠加过强"
        return problems
    }

    /** 深色模式使用的一套参数。 */
    fun forDark(enabled: Boolean): ChatBackgroundSpec {
        val json = darkJson
        if (!enabled || json.isNullOrBlank()) return this
        return runCatching { ChatBackgroundCodec.decode(json) }.getOrDefault(this)
    }

    companion object {
        const val SOURCE_THEME = "theme"
        const val SOURCE_SOLID = "solid"
        const val SOURCE_GRADIENT = "gradient"
        const val SOURCE_IMAGE = "image"
        const val SOURCE_TEXTURE = "texture"

        const val SCALE_COVER = "cover"
        const val SCALE_CONTAIN = "contain"
        const val SCALE_TILE = "tile"

        const val TEXTURE_LINEN = "linen"
        const val TEXTURE_PAPER = "paper"
        const val TEXTURE_GRID = "grid"
        const val TEXTURE_WAVES = "waves"

        const val SCRIM_AUTO = "auto"
        const val SCRIM_LIGHT = "light"
        const val SCRIM_DARK = "dark"
        const val SCRIM_CUSTOM = "custom"

        const val ANCHOR_CENTER = "center"

        val SOURCES = listOf(SOURCE_THEME, SOURCE_SOLID, SOURCE_GRADIENT, SOURCE_IMAGE, SOURCE_TEXTURE)
        val TEXTURES = listOf(TEXTURE_LINEN, TEXTURE_PAPER, TEXTURE_GRID, TEXTURE_WAVES)
        /** §14.6.2：模糊半径吸附档位。 */
        val BLUR_TIERS = listOf(0, 6, 12, 18, 24, 32, 48)
        val PARALLAX_LEVELS = listOf(0, 15, 30)

        /** 滑杆吸附到最近档位。 */
        fun snapBlur(value: Int): Int = BLUR_TIERS.minByOrNull { kotlin.math.abs(it - value) } ?: 12
    }
}

/** 三档预设（§14.6.2）：只是滑杆的组合，进页面后仍可微调。 */
enum class ChatBackgroundPreset(val labelZh: String) {
    CLEAR("清透"),
    SOFT("柔和"),
    IMMERSIVE("沉浸"),
    ;

    fun applyTo(base: ChatBackgroundSpec): ChatBackgroundSpec = when (this) {
        CLEAR -> base.copy(blurDp = 6, scrimAlpha = 25, vignette = 0, bubbleAlpha = 92)
        SOFT -> base.copy(blurDp = 12, brightness = -12, saturation = 70, scrimAlpha = 45, vignette = 18, bubbleAlpha = 88)
        IMMERSIVE -> base.copy(blurDp = 24, scrimAlpha = 60, vignette = 35, bubbleAlpha = 80)
    }
}

/** 与 `chat_backgrounds.dark_json` 互转。 */
object ChatBackgroundCodec {
    fun encode(spec: ChatBackgroundSpec): String = kotlinx.serialization.json.Json.encodeToString(
        ChatBackgroundSpec.serializer(),
        spec,
    )

    fun decode(json: String): ChatBackgroundSpec = kotlinx.serialization.json.Json.decodeFromString(
        ChatBackgroundSpec.serializer(),
        json,
    )
}
