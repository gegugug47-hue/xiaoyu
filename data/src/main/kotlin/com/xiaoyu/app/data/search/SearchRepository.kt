package com.xiaoyu.app.data.search

import com.xiaoyu.app.data.db.MessageDao
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.db.MessageFtsEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息搜索仓库（执行书 6.8 / M7-04）。
 * - 查询走 message_fts MATCH，支持会话 / 类型 / 时间区间过滤；
 * - 命中位置在应用层计算，供 UI 高亮（DAO 只返回消息本体）；
 * - 首次搜索会把历史消息补建索引，索引失败不影响聊天主链路；
 * - 「基于搜索结果回答」只拼一次性临时上下文，**绝不写入记忆**。
 */
@Singleton
class SearchRepository @Inject constructor(
    private val messageDao: MessageDao,
) {
    private val indexMutex = Mutex()

    @Volatile
    private var indexReady = false

    /** 搜索过滤条件；字段为空表示不过滤。 */
    data class Filter(
        val sessionId: Long? = null,
        val type: String? = null,
        val since: Long? = null,
        val until: Long? = null,
        val limit: Int = 40,
    )

    /** 一条结果：消息本体 + 高亮片段 + 片段内的命中区间。 */
    data class Hit(
        val message: MessageEntity,
        val snippet: String,
        val highlights: List<IntRange>,
    )

    suspend fun search(keyword: String, filter: Filter = Filter()): List<Hit> {
        val query = MessageSearchIndex.buildMatchQuery(keyword) ?: return emptyList()
        ensureIndex()
        val rows = messageDao.searchFts(
            query = query,
            sessionId = filter.sessionId,
            type = filter.type,
            since = filter.since,
            until = filter.until,
            limit = filter.limit.coerceIn(1, 200),
        )
        return rows.map { message ->
            val snippet = MessageSearchIndex.snippet(message.content, keyword)
            Hit(message = message, snippet = snippet.text, highlights = snippet.highlights)
        }
    }

    /**
     * 给「让小渔基于搜索结果回答」拼一次性的临时上下文（最多 8 条）。
     * 调用方把它传给 ChatRepository.send(transientContext = ...)，不进记忆、不落库。
     */
    fun buildTransientContext(hits: List<Hit>, max: Int = 8): String {
        if (hits.isEmpty()) return ""
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val lines = hits
            .sortedBy { it.message.createdAt }
            .take(max)
            .joinToString("\n") { hit ->
                val message = hit.message
                val role = when (message.role) {
                    "user" -> "我"
                    "assistant" -> "她"
                    else -> "系统"
                }
                "[${format.format(Date(message.createdAt))}] $role：${message.content.replace("\n", " ")}"
            }
        return "以下是你们过去的对话片段，仅供参考：\n$lines"
    }

    /**
     * 历史消息补索引：分批处理，避免首次搜索时长时间占用数据库。
     * 用 @Volatile 标记已完成，进程重启后会再核对一次（一条轻量查询）。
     */
    suspend fun ensureIndex(force: Boolean = false) {
        indexMutex.withLock {
            if (indexReady && !force) return
            while (true) {
                val batch = messageDao.messagesWithoutIndex(200)
                if (batch.isEmpty()) break
                for (message in batch) {
                    // 空 content（表情消息）也写一行空索引，避免被反复扫到
                    val text = MessageSearchIndex.buildIndexText(message.content)
                    messageDao.upsertFts(
                        MessageFtsEntity(
                            rowId = message.id,
                            content = message.content,
                            indexText = text,
                        )
                    )
                }
                if (batch.size < 200) break
            }
            indexReady = true
        }
    }
}
