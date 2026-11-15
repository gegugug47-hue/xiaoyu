package com.xiaoyu.app.data.brain

import com.xiaoyu.app.data.settings.ProviderConfig
import com.xiaoyu.app.data.settings.ProviderKind
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.soul.core.BrainChunk
import com.xiaoyu.soul.core.BrainException
import com.xiaoyu.soul.core.BrainProvider
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.core.Capability
import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.util.TokenEstimator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 示例模式：没有配置任何 Key 时也能完整演示界面（PRD 17.2-6）。 */
@Singleton
class DemoBrain @Inject constructor() : BrainProvider {
    override val providerId: String = "demo"
    override val capability: Set<Capability> = setOf(Capability.STREAMING)

    override fun chat(messages: List<SoulMessage>, stream: Boolean): Flow<BrainChunk> = flow {
        val user = messages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val reply = replyFor(user)
        for (piece in reply.chunked(2)) {
            emit(BrainChunk(delta = piece))
            delay(45)
        }
        emit(BrainChunk(finished = true))
    }

    override suspend fun completeJson(system: String?, user: String): String? = null

    override suspend fun healthCheck(): Boolean = true

    private fun replyFor(text: String): String = when {
        text.contains("在吗") || text.contains("你好") || text.contains("hi", true) ->
            "嗯，我在呢。刚刚在窗边发呆，看到消息就过来了。你今天怎么样？"
        text.contains("记住") -> "好，我记下来了。下次不会再问你第二遍。"
        text.contains("画") || text.contains("图") -> "现在还没连上家里的电脑，等我回去再画给你看。"
        text.contains("晚安") || text.contains("睡了") -> "晚安。做个好梦，明天记得跟我讲讲。"
        text.contains("心情") || text.contains("难受") -> "听起来今天不太好过。先别急着解释，我在这儿听你说。"
        text.contains("吃") -> "刚吃过。你呢，别又随便对付一口。"
        else -> "嗯…我在听。你说的这个，我记着了。"
    }
}

data class ProviderHealth(
    val role: BrainRole,
    val label: String,
    val model: String,
    val configured: Boolean,
    val ok: Boolean,
    val message: String,
)

object Pricing {
    /** 粗略估价，仅用于设置页展示（PRD 4.3.1）。 */
    fun estimate(kind: ProviderKind, promptTokens: Int, completionTokens: Int): Double = when (kind) {
        ProviderKind.DEEPSEEK -> promptTokens / 1_000_000.0 * 2.0 + completionTokens / 1_000_000.0 * 8.0
        ProviderKind.DASHSCOPE -> promptTokens / 1_000_000.0 * 0.8 + completionTokens / 1_000_000.0 * 2.0
        else -> 0.0
    }
}

/**
 * 大脑路由：按角色取主/备模型，主模型失败自动降级；已输出内容后不再换厂商（PRD 2.4.3 / 4.1）。
 */
@Singleton
class BrainRouter @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val demo: DemoBrain,
    private val costTable: com.xiaoyu.app.data.usage.CostTable,
) : BrainGateway {
    val isDemoMode: Boolean
        get() {
            val brain = settings.current().brain
            return !brain.chat.isConfigured && brain.chatFallback?.isConfigured != true
        }

    fun configsFor(role: BrainRole): List<ProviderConfig> {
        val brain = settings.current().brain
        return when (role) {
            BrainRole.CHAT -> listOfNotNull(brain.chat, brain.chatFallback)
            BrainRole.EXTRACT -> listOfNotNull(
                brain.extract.takeIf { it.isConfigured } ?: brain.chat,
                brain.extractFallback,
                brain.chatFallback,
            )
            BrainRole.VISION -> listOfNotNull(
                brain.extract.takeIf { it.isConfigured } ?: brain.chat,
                brain.chatFallback,
            )
        }.filter { it.isConfigured }.distinctBy { "${it.kind}-${it.baseUrl}-${it.model}" }
    }

    override fun chat(role: BrainRole, messages: List<SoulMessage>): Flow<BrainChunk> = flow {
        // 成本护栏（执行书 4.6）：超限后只保证聊天可用，后台自动生成直接跳过。
        if (role != BrainRole.CHAT && settings.guardState().active) return@flow
        val configs = configsFor(role)
        if (configs.isEmpty()) {
            demo.chat(messages).collect { emit(it) }
            return@flow
        }
        val output = StringBuilder()
        var recorded = false
        var lastError: BrainException? = null

        for (config in configs) {
            val provider = BrainProviderFactory.create(config, client)
            try {
                provider.chat(messages).collect { chunk ->
                    if (chunk.delta.isNotEmpty()) output.append(chunk.delta)
                    chunk.usage?.let { usage ->
                        recordUsage(role, config, usage.promptTokens, usage.completionTokens)
                        recorded = true
                    }
                    emit(chunk)
                }
                if (!recorded) {
                    recordUsage(
                        role = role,
                        config = config,
                        promptTokens = TokenEstimator.estimateAll(messages.map { it.content }),
                        completionTokens = TokenEstimator.estimate(output.toString()),
                    )
                }
                return@flow
            } catch (e: BrainException) {
                lastError = e
                if (output.isNotEmpty()) throw e
            } catch (e: IOException) {
                lastError = BrainException(
                    "io error",
                    "网络连接失败：检查网络，或家里的电脑 / Ollama 是否在线。",
                    cause = e,
                )
                if (output.isNotEmpty()) throw lastError
            }
        }
        throw lastError ?: BrainException(
            "no provider",
            "还没有可用的模型，请到设置页填写 API Key；也可以先用示例模式随便聊聊。",
        )
    }

    private suspend fun recordUsage(
        role: BrainRole,
        config: ProviderConfig,
        promptTokens: Int,
        completionTokens: Int,
    ) {
        if (config.kindEnum == ProviderKind.OLLAMA) return
        // 单价来自外置 assets/cost_table.json，未知模型按 0 计。
        val cost = costTable.estimate(config.model, promptTokens, completionTokens)
        settings.recordUsage(
            isChat = role == BrainRole.CHAT,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            costYuan = cost,
        )
    }

    override suspend fun completeJson(role: BrainRole, system: String?, user: String): String? {
        if (role != BrainRole.CHAT && settings.guardState().active) return null
        val configs = configsFor(role)
        for (config in configs) {
            val provider = BrainProviderFactory.create(config, client)
            val result = provider.completeJson(system, user)
            if (!result.isNullOrBlank()) {
                recordUsage(
                    role = role,
                    config = config,
                    promptTokens = TokenEstimator.estimate((system ?: "") + user),
                    completionTokens = TokenEstimator.estimate(result),
                )
                return result
            }
        }
        return null
    }

    /** 需要纯文本产物（日记 / 生活碎片）时使用：非流式聚合一次完整回复。 */
    override suspend fun completeText(role: BrainRole, prompt: String): String? {
        val messages = listOf(
            SoulMessage(MessageRole.USER, prompt)
        )
        val buffer = StringBuilder()
        return try {
            chat(role, messages).collect { chunk ->
                if (chunk.delta.isNotEmpty()) buffer.append(chunk.delta)
            }
            buffer.toString().trim().ifEmpty { null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun health(role: BrainRole): List<ProviderHealth> {
        val configs = configsFor(role)
        if (configs.isEmpty()) {
            return listOf(
                ProviderHealth(
                    role = role,
                    label = "示例模式",
                    model = "无需 Key",
                    configured = false,
                    ok = true,
                    message = "当前未配置模型，聊天使用内置示例回复。",
                )
            )
        }
        return configs.map { config ->
            val ok = runCatching { BrainProviderFactory.create(config, client).healthCheck() }
                .getOrDefault(false)
            ProviderHealth(
                role = role,
                label = config.label(),
                model = config.model,
                configured = true,
                ok = ok,
                message = if (ok) "连接正常" else "连接失败：检查 Key / 地址 / 电脑是否开机",
            )
        }
    }
}
