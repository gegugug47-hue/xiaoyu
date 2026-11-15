package com.xiaoyu.soul.core

import kotlinx.coroutines.flow.Flow

/** 模型在灵魂中的“工种”（PRD 2.4.2）。 */
enum class BrainRole { CHAT, EXTRACT, VISION }

enum class MessageRole { SYSTEM, USER, ASSISTANT }

data class SoulMessage(val role: MessageRole, val content: String)

enum class Capability { STREAMING, JSON_MODE, TOOL_CALL, VISION }

data class TokenUsage(val promptTokens: Int = 0, val completionTokens: Int = 0) {
    val total: Int get() = promptTokens + completionTokens
}

data class BrainChunk(
    val delta: String = "",
    val usage: TokenUsage? = null,
    val finished: Boolean = false,
)

/** 统一的模型调用抽象：任何 OpenAI 兼容端点实现该接口即可接入（PRD 2.4.1）。 */
interface BrainProvider {
    val providerId: String
    val capability: Set<Capability>

    fun chat(messages: List<SoulMessage>, stream: Boolean = true): Flow<BrainChunk>

    /** 结构化任务：只返回模型原始文本，解析失败由调用方宽容处理；失败返回 null。 */
    suspend fun completeJson(system: String?, user: String): String?

    suspend fun healthCheck(): Boolean
}

/**
 * 通道层异常。[userMessage] 是直接展示在聊天报错气泡里的中文原因与建议，
 * 禁止把 Key、URL 明文暴露给 UI（PRD 4.1 / 12）。
 */
class BrainException(
    message: String,
    val userMessage: String = message,
    val role: BrainRole? = null,
    val providerLabel: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
