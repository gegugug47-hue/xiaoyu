package com.xiaoyu.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoyu.app.ui.isLastInGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesignSystemTest {
    @Test
    fun `eleven semantic text styles use the specified scale`() {
        val type = YuType.Default
        assertEquals(36.sp, type.display.fontSize)
        assertEquals(44.sp, type.display.lineHeight)
        assertEquals(22.sp, type.title.fontSize)
        assertEquals(17.sp, type.section.fontSize)
        assertEquals(16.sp, type.body.fontSize)
        assertEquals(25.sp, type.chat.lineHeight)
        assertEquals(13.sp, type.caption.fontSize)
        assertEquals(12.sp, type.meta.fontSize)
        assertEquals(18.sp, type.emotion.fontSize)
        assertEquals(20.sp, type.emoji.fontSize)
        assertEquals(15.sp, type.herDay.fontSize)
        assertEquals(18.sp, type.diary.fontSize)
        assertEquals(28.sp, type.diary.lineHeight)
    }

    @Test
    fun `all four presets expose light and dark schemes generated from their seeds`() {
        assertEquals(listOf("#8FB6D9", "#4A90D9", "#5BA58C", "#5DADE2"), YuThemePreset.entries.map { it.seedHex })
        YuThemePreset.entries.forEach { preset ->
            assertNotEquals(preset.lightColors, preset.darkColors)
            assertEquals(preset.seedHex, colorToHex(parseHexColor(preset.seedHex)!!))
        }
    }

    @Test
    fun `presets carry Chinese labels for end-user display`() {
        assertEquals("晨雾", YuThemePreset.MIST.labelZh)
        assertEquals("昼白", YuThemePreset.DAY.labelZh)
        assertEquals("暮青", YuThemePreset.DUSK.labelZh)
        assertEquals("夜航", YuThemePreset.NIGHT.labelZh)
        assertTrue(YuThemePreset.entries.all { it.tagline.isNotBlank() })
    }

    @Test
    fun `hex parser accepts six digits and rejects invalid input`() {
        assertEquals("#4A90D9", colorToHex(parseHexColor("4a90d9")!!))
        assertEquals("#8FB6D9", colorToHex(parseHexColor("#8FB6D9")!!))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("not-a-color"))
    }

    @Test
    fun `motion durations become zero for user or system reduction`() {
        assertEquals(80, resolveMotionDurations(false, 1f, 1f).flash)
        assertEquals(600, resolveMotionDurations(false, 1f, 1f).emphasis)
        assertEquals(0, resolveMotionDurations(true, 1f, 1f).normal)
        assertEquals(0, resolveMotionDurations(false, 0f, 1f).page)
        assertEquals(0, resolveMotionDurations(false, 1f, 0f).slow)
    }

    @Test
    fun `bubble tail belongs only to the last message in a group`() {
        assertTrue(isLastInGroup("user", 1000L, null, 0L))
        assertTrue(isLastInGroup("user", 1000L, "assistant", 2000L))
        assertTrue(isLastInGroup("user", 1000L, "user", 121001L))
        assertEquals(false, isLastInGroup("user", 1000L, "user", 2000L))
    }

    @Test
    fun `window breakpoints are compact below 600 medium through 839 and expanded from 840`() {
        assertEquals(WindowSizeClass.COMPACT, windowSizeClassFor(599.dp))
        assertEquals(WindowSizeClass.MEDIUM, windowSizeClassFor(600.dp))
        assertEquals(WindowSizeClass.MEDIUM, windowSizeClassFor(839.dp))
        assertEquals(WindowSizeClass.EXPANDED, windowSizeClassFor(840.dp))
        assertTrue(WindowSizeClass.MEDIUM.supportsDualPane)
        assertTrue(WindowSizeClass.EXPANDED.supportsDualPane)
    }

    @Test
    fun `only expanded settings uses a secondary panel`() {
        assertEquals(false, WindowSizeClass.COMPACT.supportsSettingsSecondaryPane)
        assertEquals(false, WindowSizeClass.MEDIUM.supportsSettingsSecondaryPane)
        assertTrue(WindowSizeClass.EXPANDED.supportsSettingsSecondaryPane)
    }

    @Test
    fun `window adaptive state exposes handedness posture and one-hand offsets`() {
        val defaultState = WindowAdaptiveState()
        assertEquals(WindowPosture.PHONE, defaultState.posture)
        assertEquals(Handedness.TWO_HANDED, defaultState.handedness)
        assertFalse(defaultState.requiresBottomLift)
        assertEquals(0.dp, defaultState.horizontalOffset())

        val leftHand = defaultState.copy(handedness = Handedness.LEFT_HAND)
        assertTrue(leftHand.requiresBottomLift)
        assertEquals(28.dp, leftHand.horizontalOffset())

        val rightHand = defaultState.copy(handedness = Handedness.RIGHT_HAND)
        assertTrue(rightHand.requiresBottomLift)
        assertEquals((-28).dp, rightHand.horizontalOffset())

        val halfOpen = defaultState.copy(posture = WindowPosture.FOLD_HALF_OPEN)
        assertTrue(halfOpen.requiresBottomLift)
    }

    @Test
    fun `new design system files contain no hardcoded dimensions or Color literals`() {
        val uiDir = File("src/main/kotlin/com/xiaoyu/app/ui")
        val checkedFiles = setOf(
            "theme/YuType.kt",
            "theme/YuThemePresets.kt",
            "theme/ColorPicker.kt",
            "theme/YuMotion.kt",
            "theme/YuBreakpoints.kt",
            "theme/YuWindowAdaptive.kt",
            "components/YuComponents.kt",
            "illustrations/YuEmptyIllustrations.kt",
        )
        val violations = HardcodeCheck.scan(uiDir).filter {
            File(it.file).relativeTo(uiDir).invariantSeparatorsPath in checkedFiles
        }
        assertTrue(violations.joinToString("\n") { "${it.file}:${it.line} ${it.code}" }, violations.isEmpty())
    }

    @Test
    fun `hardcode scanner catches Color hex literals outside token files`() {
        val fake = File.createTempFile("color-scan-test", ".kt")
        fake.deleteOnExit()
        fake.writeText(
            """
            package com.xiaoyu.app.ui.theme
            val x = Color(0xFF3E7C8C)
            val y = 16.dp
            """.trimIndent()
        )
        val uiDir = fake.parentFile!!
        val violations = HardcodeCheck.scan(uiDir)
        val colors = violations.filter { it.code.startsWith("Color(") }
        val dims = violations.filter { it.code.endsWith(".dp") || it.code.endsWith(".sp") }
        assertTrue(violations.toString(), colors.any { "Color(0xFF3E7C8C)" in it.code })
        assertTrue(violations.toString(), dims.any { "16.dp" == it.code })
        fake.delete()
    }

    @Test
    fun `palette extractor fallback returns DAY preset seed for empty input`() {
        // 纯黑 bitmap 会被完全跳过（饱和为 0），seed 回落到默认
        val result = PaletteExtractor.extract(defaultBitmap(16, 16, Color.Black))
        assertEquals("#4A90D9", colorToHex(result.seed))
        assertTrue(result.primaries.isNotEmpty())
    }

    private fun defaultBitmap(w: Int, h: Int, color: Color): android.graphics.Bitmap {
        val pixel = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
        val pixels = IntArray(w * h) { pixel }
        return android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
    }
}
