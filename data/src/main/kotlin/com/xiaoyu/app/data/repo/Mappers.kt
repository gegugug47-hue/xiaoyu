package com.xiaoyu.app.data.repo

import com.xiaoyu.app.data.db.DiaryEntity
import com.xiaoyu.app.data.db.FactCardEntity
import com.xiaoyu.app.data.db.IdentityEntity
import com.xiaoyu.app.data.db.LifeFragmentEntity
import com.xiaoyu.app.data.db.MemoryEntity
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.db.StyleEntity
import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.memory.FactCard
import com.xiaoyu.soul.memory.MemoryFragment
import com.xiaoyu.soul.memory.MemoryTier
import com.xiaoyu.soul.memory.MemoryType
import com.xiaoyu.soul.persona.PersonaIdentity
import com.xiaoyu.soul.persona.PersonaStyle

const val ROLE_USER = "user"
const val ROLE_ASSISTANT = "assistant"
const val ROLE_NOTICE = "system_notice"

/** 消息类型（执行书 7.4.4）。 */
const val TYPE_TEXT = "text"
const val TYPE_STICKER = "sticker"
/** v3 ADR-15 / 7.4.5：结构化卡片消息（日记/约定/她的一天/记忆回溯），content 为 JSON。 */
const val TYPE_CARD = "card"

/** 消息发送状态（执行书 M1-09）。 */
const val STATUS_SENDING = "sending"
const val STATUS_SENT = "sent"
const val STATUS_FAILED = "failed"

/** 撤回时效（执行书 M3-07）：2 分钟内可撤回自己的消息。 */
const val RETRACT_WINDOW_MS = 2 * 60 * 1000L

/** 气泡内的动作描写行前缀（执行书 3.3.1 `[xy:act=...]`），UI 渲染为斜体灰字。 */
const val ACTION_PREFIX = "\u2063"

fun IdentityEntity.toSoul(): PersonaIdentity = PersonaIdentity(id, name, contentMd)

fun StyleEntity.toSoul(): PersonaStyle = PersonaStyle(id, name, contentMd)

fun MemoryEntity.toSoul(): MemoryFragment = MemoryFragment(
    id = id,
    tier = runCatching { MemoryTier.valueOf(tier) }.getOrDefault(MemoryTier.SHORT),
    content = content,
    type = MemoryType.from(type),
    tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    importance = importance,
    strength = strength,
    reviewCount = reviewCount,
    createdAt = createdAt,
    lastAccessAt = lastAccess,
    sourceSessionId = sourceSessionId,
    promotedToCardId = promotedToCardId,
    binAt = binAt,
)

fun FactCardEntity.toSoul(): FactCard = FactCard(
    id = id,
    category = category,
    content = content,
    tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    strength = strength,
    expireAt = expireAt,
    source = source,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceMemoryIds = sourceMemoryIds.split(",").mapNotNull { it.trim().toLongOrNull() },
)

fun MessageEntity.toSoulMessage(stickerName: String? = null): SoulMessage {
    val isSticker = type == TYPE_STICKER
    val content = when {
        isSticker -> {
            val who = if (role == ROLE_USER) "用户发送了表情" else "她发送了表情"
            if (stickerName.isNullOrBlank()) "[$who]" else "[$who：$stickerName]"
        }
        // 动作行只是表现层，注入上下文时不带进去
        else -> content.split("\n").filterNot { it.startsWith(ACTION_PREFIX) }.joinToString("\n")
    }
    return SoulMessage(
        role = when (role) {
            ROLE_USER -> MessageRole.USER
            ROLE_ASSISTANT -> MessageRole.ASSISTANT
            else -> MessageRole.SYSTEM
        },
        content = content,
    )
}

fun LifeFragmentEntity.toInjection(): String = content

fun DiaryEntity.triggerLabels(): List<String> =
    triggerType.split(",").filter { it.isNotBlank() }
