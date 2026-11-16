package com.xiaoyu.app.ui.theme

import java.io.File

/**
 * 硬编码扫描器（执行书 v3 H-24 / 14.2 的轻量实现）。
 *
 * 完整自定义 Lint 规则需要独立 lint module + Registry，成本较高。
 * 这里提供一个可在单测 / 调试页调用的扫描器：扫描 ui 目录下的 Kotlin 文件，
 * 报告直接写死的 `.dp` / `.sp` 数值（排除 Tokens.kt 自身与注释）。
 *
 * 用法：`HardcodeCheck.scan(projectDir)` 返回违规列表。
 */
object HardcodeCheck {

    data class Violation(
        val file: String,
        val line: Int,
        val code: String,
    )

    /** 匹配形如 `16.dp` / `8.sp` 但不以 Tokens 开头的字面量。 */
    private val hardcodedDimen = Regex("""(?<!\w)(\d+)\.(dp|sp)\b""")

    fun scan(uiDir: File): List<Violation> {
        if (!uiDir.exists()) return emptyList()
        val violations = mutableListOf<Violation>()
        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "Tokens.kt" && it.name != "HardcodeCheck.kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, raw ->
                    val line = stripComments(raw)
                    // 排除 Tokens.xxx 和 import 行
                    if (line.trimStart().startsWith("import")) return@forEachIndexed
                    hardcodedDimen.findAll(line).forEach { match ->
                        // 排除紧跟在 Tokens 后面的（如 Tokens.SpacingSm）
                        val before = line.substring(0, match.range.first).trimEnd()
                        if (before.endsWith("Tokens.")) return@forEach
                        violations += Violation(
                            file = file.path,
                            line = idx + 1,
                            code = match.value,
                        )
                    }
                }
            }
        return violations
    }

    /** 去掉行内注释（// 之后），避免误报注释里的数字。 */
    private fun stripComments(line: String): String {
        val idx = line.indexOf("//")
        return if (idx >= 0) line.substring(0, idx) else line
    }
}
