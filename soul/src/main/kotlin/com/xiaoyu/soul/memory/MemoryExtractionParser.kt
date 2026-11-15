package com.xiaoyu.soul.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/**
 * EXTRACT 产物的宽容解析（PRD 2.4.4）：
 * 剥 markdown 围栏 → 截取最外层数组/对象 → 逐字段容错，坏条目跳过，不让整批失败。
 */
object MemoryExtractionParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseMemories(raw: String?): List<ExtractedMemory> {
        val array = extractArray(raw) ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val content = obj.str("content")?.trim().orEmpty()
            if (content.isEmpty()) return@mapNotNull null
            val importance = (obj.int("importance", 3)).coerceIn(1, 5)
            val tags = obj.stringList("tags")
            ExtractedMemory(
                content = content,
                type = MemoryType.from(obj.str("type")),
                importance = importance,
                tags = tags.ifEmpty { emptyList() },
            )
        }.distinctBy { it.content }
    }

    fun parseCardMerge(raw: String?): ExtractedCard? {
        val obj = extractObject(raw) ?: return null
        val content = obj.str("content")?.trim().orEmpty()
        if (content.isEmpty()) return null
        val category = obj.str("category")?.trim().orEmpty().ifEmpty { CARD_CATEGORY_PREFERENCE }
        val expire = obj.str("expire_at")?.trim()
        val expireMillis = expire?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
                ?: runCatching { it.toLong() }.getOrNull()
        }
        return ExtractedCard(
            category = category,
            content = content,
            tags = obj.stringList("tags"),
            expireAt = expireMillis,
        )
    }

    fun parseMoodDelta(raw: String?): MoodSignal? {
        val obj = extractObject(raw) ?: return null
        val delta = obj.int("mood_delta", 0).coerceIn(-30, 30)
        val energy = obj.int("energy_delta", 0).coerceIn(-20, 20)
        return MoodSignal(delta, energy)
    }

    /**
     * 显式记忆指令确认（PRD 5.2.1）：{"remember":true,"content":"...","tags":[...],"category":"..."}
     * 解析失败时返回 remember=false，由调用方走“先回应、后重试”的兜底。
     */
    fun parseExplicitMemory(raw: String?): ExplicitMemoryResult {
        val obj = extractObject(raw) ?: return ExplicitMemoryResult(false, null)
        val remember = obj.str("remember")?.equals("true", ignoreCase = true) == true
        if (!remember) return ExplicitMemoryResult(false, null)
        val content = obj.str("content")?.trim().orEmpty()
        if (content.isEmpty()) return ExplicitMemoryResult(false, null)
        val category = obj.str("category")?.trim().orEmpty().ifEmpty { CARD_CATEGORY_PROFILE }
        return ExplicitMemoryResult(
            remember = true,
            card = ExtractedCard(category = category, content = content, tags = obj.stringList("tags")),
        )
    }

    fun parseLifeFragments(raw: String?): List<String> {
        val array = extractArray(raw) ?: return emptyList()
        return array.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.trim()
                is JsonObject -> element.str("content")?.trim()
                else -> null
            }?.takeIf { it.isNotEmpty() }
        }.distinct().take(6)
    }

    fun parseText(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val unfenced = stripFence(text)
        return unfenced.trim().ifEmpty { null }
    }

    private fun stripFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val lastFence = trimmed.lastIndexOf("```")
        return if (firstNewline >= 0 && lastFence > firstNewline) {
            trimmed.substring(firstNewline + 1, lastFence)
        } else {
            trimmed.removePrefix("```")
        }
    }

    private fun extractArray(raw: String?): JsonArray? {
        val text = raw?.let { stripFence(it) } ?: return null
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val slice = text.substring(start, end + 1)
        return runCatching { json.parseToJsonElement(slice) as? JsonArray }.getOrNull()
    }

    private fun extractObject(raw: String?): JsonObject? {
        val text = raw?.let { stripFence(it) } ?: return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val slice = text.substring(start, end + 1)
        return runCatching { json.parseToJsonElement(slice) as? JsonObject }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String, def: Int): Int =
        (this[key] as? JsonPrimitive)?.contentOrNull?.let {
            it.toDoubleOrNull()?.toInt() ?: it.toIntOrNull()
        } ?: def

    private fun JsonObject.stringList(key: String): List<String> {
        val element: JsonElement? = this[key]
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            is JsonPrimitive -> element.contentOrNull
                ?.split(",", "，", "、", " ", "#")
                ?.map { it.trim() }
                ?: emptyList()
            else -> emptyList()
        }.filter { it.isNotEmpty() }.distinct()
    }
}

data class MoodSignal(val moodDelta: Int, val energyDelta: Int)

data class ExplicitMemoryResult(val remember: Boolean, val card: ExtractedCard?)
