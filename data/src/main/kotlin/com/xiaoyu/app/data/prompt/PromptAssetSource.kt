package com.xiaoyu.app.data.prompt

import android.content.Context
import com.xiaoyu.soul.prompt.PromptKeys
import com.xiaoyu.soul.prompt.TemplateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提示词模板外置（PRD 2.4.4 / US-021）：
 * 首次启动把 assets/prompts 下的模板复制到 files/prompts 目录，之后运行时读文件，用户可编辑，无需改代码。
 */
@Singleton
class PromptAssetSource @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : TemplateSource {

    private val dir: File get() = File(context.filesDir, "prompts")

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (!dir.exists()) dir.mkdirs()
        for (key in PromptKeys.all) {
            val target = File(dir, "$key.md")
            if (target.exists()) continue
            val content = runCatching {
                context.assets.open("prompts/$key.md").bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            target.writeText(content)
        }
    }

    override fun get(key: String): String? {
        val file = File(dir, "$key.md")
        if (file.exists()) {
            return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        return runCatching {
            context.assets.open("prompts/$key.md").bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    suspend fun read(key: String): String = withContext(Dispatchers.IO) {
        get(key) ?: ""
    }

    suspend fun save(key: String, content: String) = withContext(Dispatchers.IO) {
        if (!dir.exists()) dir.mkdirs()
        File(dir, "$key.md").writeText(content)
    }

    suspend fun reset(key: String) = withContext(Dispatchers.IO) {
        val asset = runCatching {
            context.assets.open("prompts/$key.md").bufferedReader().use { it.readText() }
        }.getOrNull()
        val file = File(dir, "$key.md")
        if (asset != null) file.writeText(asset) else file.delete()
    }

    /** 导出人格包时携带用户改过的模板（M7-09）：只返回与内置资产不同的 key -> 正文。 */
    suspend fun userOverrides(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext emptyMap()
        dir.listFiles()
            ?.filter { it.isFile && it.extension == "md" }
            ?.mapNotNull { file ->
                val key = file.nameWithoutExtension
                val content = runCatching { file.readText() }.getOrNull()
                if (content.isNullOrBlank()) return@mapNotNull null
                val asset = assetText(key)
                if (asset != null && asset.trim() == content.trim()) null else key to content
            }
            ?.toMap()
            .orEmpty()
    }

    private fun assetText(key: String): String? = runCatching {
        context.assets.open("prompts/$key.md").bufferedReader().use { it.readText() }
    }.getOrNull()
}
