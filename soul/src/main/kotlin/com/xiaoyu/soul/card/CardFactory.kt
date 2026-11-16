package com.xiaoyu.soul.card

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 卡片消息工厂（执行书 v3 ADR-15 / 7.4.5）。
 *
 * 卡片是引擎生成的结构化内容，不是模型自由文本，保证关键信息（日记摘要、约定、
 * 生活碎片、记忆回溯）以稳定布局呈现，不随模型措辞漂移。
 *
 * 输出为 JSON 字符串，存进 MessageEntity.content（type=card），UI 端 CardBubble 解析渲染。
 */
object CardFactory {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 卡片类别。 */
    enum class CardKind { DIARY, AGREEMENT, HER_DAY, MEMORY_RECALL }

    @Serializable
    data class CardPayload(
        val kind: String,
        val title: String,
        val lines: List<String> = emptyList(),
        val footer: String? = null,
    )

    /** 生成一张日记卡片：日期 + 心情 + 摘要行。 */
    fun diaryCard(dateLabel: String, mood: String, summaryLines: List<String>): String =
        encode(CardPayload(
            kind = CardKind.DIARY.name,
            title = "$dateLabel · $mood",
            lines = summaryLines,
            footer = "她的日记",
        ))

    /** 生成一张约定卡片：约定内容 + 时间。 */
    fun agreementCard(content: String, timeLabel: String?): String =
        encode(CardPayload(
            kind = CardKind.AGREEMENT.name,
            title = "我们的约定",
            lines = listOf(content),
            footer = timeLabel,
        ))

    /** 生成「她的一天」卡片：今日生活碎片摘要。 */
    fun herDayCard(fragments: List<String>): String =
        encode(CardPayload(
            kind = CardKind.HER_DAY.name,
            title = "她的一天",
            lines = fragments,
            footer = "今天的生活碎片",
        ))

    /** 生成记忆回溯卡片：唤起一段记忆。 */
    fun memoryRecallCard(memory: String, hint: String? = null): String =
        encode(CardPayload(
            kind = CardKind.MEMORY_RECALL.name,
            title = "想起了一件事",
            lines = listOf(memory),
            footer = hint,
        ))

    private fun encode(payload: CardPayload): String = json.encodeToString(payload)

    /** UI 端解析卡片内容。 */
    fun parse(raw: String): CardPayload? =
        runCatching { json.decodeFromString<CardPayload>(raw) }.getOrNull()
}
