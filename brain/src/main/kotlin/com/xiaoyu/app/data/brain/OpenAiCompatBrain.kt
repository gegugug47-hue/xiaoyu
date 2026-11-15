package com.xiaoyu.app.data.brain

import com.xiaoyu.app.data.settings.ProviderConfig
import com.xiaoyu.app.data.settings.ProviderKind
import com.xiaoyu.soul.core.BrainChunk
import com.xiaoyu.soul.core.BrainException
import com.xiaoyu.soul.core.BrainProvider
import com.xiaoyu.soul.core.Capability
import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.core.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * 任何 OpenAI 兼容端点的统一实现（PRD 2.4.1）。灵魂引擎不感知厂商差异。
 */
open class OpenAiCompatBrain(
    private val config: ProviderConfig,
    private val client: OkHttpClient,
    private val supportsJsonMode: Boolean,
    override val providerId: String,
) : BrainProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val capability: Set<Capability>
        get() = buildSet {
            add(Capability.STREAMING)
            if (supportsJsonMode) add(Capability.JSON_MODE)
        }

    private fun endpoint(path: String): String = config.baseUrl.trimEnd('/') + path

    private fun headers(builder: Request.Builder): Request.Builder {
        builder.header("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey}")
        }
        return builder
    }

    override fun chat(messages: List<SoulMessage>, stream: Boolean): Flow<BrainChunk> = flow {
        val payload = buildJsonObject {
            put("model", config.model)
            put("stream", stream)
            put("temperature", 0.85)
            put("max_tokens", 900)
            if (stream) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            put("messages", buildJsonArray {
                messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role.name.lowercase())
                        put("content", message.content)
                    })
                }
            })
        }

        val request = runCatching {
            headers(Request.Builder().url(endpoint("/chat/completions")))
                .post(payload.toString().toRequestBody(JSON))
                .build()
        }.getOrElse {
            throw BrainException("bad url", "模型地址填写有误，请到设置页检查 Base URL", cause = it)
        }

        val response = try {
            withContext(Dispatchers.IO) { client.newCall(request).execute() }
        } catch (e: IOException) {
            throw BrainException(
                message = "network error",
                userMessage = "网络连接失败：检查手机网络，或家里的电脑 / Ollama 是否在线。",
                cause = e,
            )
        }

        response.use { resp ->
            if (!resp.isSuccessful) throw httpError(resp)
            if (!stream) {
                val text = resp.body.string()
                val content = parseFullContent(text)
                if (content.isNullOrEmpty()) {
                    throw BrainException("empty response", "模型没有返回内容，请稍后重试。")
                }
                val usage = parseUsage(json.parseToJsonElement(text).jsonObject)
                emit(BrainChunk(delta = content, usage = usage))
                emit(BrainChunk(finished = true))
                return@use
            }

            val source = resp.body.source()
            var finished = false
            while (true) {
                val line = withContext(Dispatchers.IO) { source.readUtf8Line() } ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    finished = true
                    break
                }
                val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                val usage = obj["usage"]?.takeIf { it is JsonObject }?.let { parseUsage(it.jsonObject) }
                val choice = (obj["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
                val deltaObj = choice?.get("delta") as? JsonObject
                val delta = deltaObj?.get("content")?.jsonPrimitive?.contentOrNull
                if (!delta.isNullOrEmpty() || usage != null) {
                    emit(BrainChunk(delta = delta ?: "", usage = usage))
                }
                val finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
                if (finishReason != null) finished = true
            }
            if (!finished) {
                // 流式中断：把已生成部分留给 UI，由“继续”按钮续写（PRD 4.1）
                throw BrainException(
                    message = "stream interrupted",
                    userMessage = "回复中断了，已保留生成的部分，可以点“继续”。",
                )
            }
            emit(BrainChunk(finished = true))
        }
    }

    override suspend fun completeJson(system: String?, user: String): String? {
        val payload = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.2)
            put("max_tokens", 1200)
            if (supportsJsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            put("messages", buildJsonArray {
                if (!system.isNullOrBlank()) {
                    add(buildJsonObject {
                        put("role", MessageRole.SYSTEM.name.lowercase())
                        put("content", system)
                    })
                }
                add(buildJsonObject {
                    put("role", MessageRole.USER.name.lowercase())
                    put("content", user)
                })
            })
        }
        val request = runCatching {
            headers(Request.Builder().url(endpoint("/chat/completions")))
                .post(payload.toString().toRequestBody(JSON))
                .build()
        }.getOrNull() ?: return null
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    parseFullContent(resp.body.string())
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val request = runCatching {
            headers(Request.Builder().url(endpoint("/models"))).get().build()
        }.getOrNull() ?: return@withContext false
        try {
            client.newCall(request).execute().use { it.isSuccessful || it.code == 405 }
        } catch (e: IOException) {
            false
        }
    }

    private fun parseFullContent(text: String): String? = runCatching {
        val obj = json.parseToJsonElement(text).jsonObject
        (obj["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun parseUsage(obj: JsonObject): TokenUsage? {
        val usage = obj["usage"]?.takeIf { it is JsonObject }?.jsonObject ?: return null
        val prompt = usage["prompt_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val completion = usage["completion_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        if (prompt == 0 && completion == 0) return null
        return TokenUsage(prompt, completion)
    }

    private fun httpError(response: Response): BrainException {
        val code = response.code
        val body = runCatching { response.body.string().take(300) }.getOrDefault("")
        val userMessage = when (code) {
            401, 403 -> "API Key 无效或没有权限，请到设置页检查。"
            402 -> "账户余额不足，请到模型厂商控制台充值。"
            404 -> "接口地址或模型名不对（HTTP 404），请检查设置里的 Base URL / 模型名。"
            429 -> "请求太频繁或额度用完了，稍后再试。"
            in 500..599 -> "模型服务端异常（HTTP $code），稍后再试或切换备用模型。"
            else -> "模型请求失败（HTTP $code）。"
        }
        return BrainException("HTTP $code: $body", userMessage)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class DeepSeekProvider(config: ProviderConfig, client: OkHttpClient) :
    OpenAiCompatBrain(config, client, true, "deepseek")

class DashScopeProvider(config: ProviderConfig, client: OkHttpClient) :
    OpenAiCompatBrain(config, client, true, "dashscope")

class OllamaProvider(config: ProviderConfig, client: OkHttpClient) :
    OpenAiCompatBrain(config, client, false, "ollama")

class CustomProvider(config: ProviderConfig, client: OkHttpClient) :
    OpenAiCompatBrain(config, client, false, "custom")

object BrainProviderFactory {
    fun create(config: ProviderConfig, client: OkHttpClient): BrainProvider =
        when (config.kindEnum) {
            ProviderKind.DEEPSEEK -> DeepSeekProvider(config, client)
            ProviderKind.DASHSCOPE -> DashScopeProvider(config, client)
            ProviderKind.OLLAMA -> OllamaProvider(config, client)
            ProviderKind.CUSTOM -> CustomProvider(config, client)
        }
}
