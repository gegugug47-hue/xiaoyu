package com.xiaoyu.app.data.repo

import com.xiaoyu.app.data.brain.BrainGateway
import com.xiaoyu.app.data.db.EmbeddingDao
import com.xiaoyu.app.data.db.EmbeddingEntity
import com.xiaoyu.app.data.db.FactCardDao
import com.xiaoyu.app.data.db.FactCardEntity
import com.xiaoyu.app.data.db.MemoryDao
import com.xiaoyu.app.data.db.MemoryEntity
import com.xiaoyu.app.data.db.MessageDao
import com.xiaoyu.app.data.db.SessionDao
import com.xiaoyu.app.data.memory.VectorRetriever
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.engine.SoulEngine
import com.xiaoyu.soul.memory.FactCard
import com.xiaoyu.soul.memory.FactSource
import com.xiaoyu.soul.memory.ForgettingCurve
import com.xiaoyu.soul.memory.MemoryExtractionParser
import com.xiaoyu.soul.memory.MemoryFragment
import com.xiaoyu.soul.memory.MemoryRecall
import com.xiaoyu.soul.memory.MemoryTier
import com.xiaoyu.soul.memory.MemoryType
import com.xiaoyu.soul.memory.PromotionRule
import com.xiaoyu.soul.safety.CrisisDetector
import com.xiaoyu.soul.safety.CrisisLevel
import com.xiaoyu.soul.safety.InjectionGuard
import com.xiaoyu.soul.safety.PromptZone
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 提炼输出解析失败的次数上限：超过后放弃本轮，避免后台无休止重试。 */
private const val MAX_PARSE_FAILURES = 3

/**
 * 三级记忆系统（PRD 5）：会话结束提炼 → 短期碎片 → 每晚晋升/衰减 → 长期卡片。
 * 红线：绝不触碰 messages 的删除；遗忘只作用于 memories 表。
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val cardDao: FactCardDao,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val router: BrainGateway,
    private val soul: SoulEngine,
    private val settings: com.xiaoyu.app.data.settings.SettingsRepository,
    private val embeddingDao: EmbeddingDao,
) {
    fun observeShortTerm(): Flow<List<MemoryEntity>> = memoryDao.observeShortTerm()

    fun observeBin(): Flow<List<MemoryEntity>> = memoryDao.observeBin()

    fun observeCards(): Flow<List<FactCardEntity>> = cardDao.observeAll()

    suspend fun cards(): List<FactCard> = cardDao.all().map { it.toSoul() }

    /** 会话结束提炼 / 增量提炼（PRD 5.2 / v3 H-14）；返回是否成功。 */
    suspend fun extractSession(sessionId: Long, title: String): Boolean {
        val session = sessionDao.byId(sessionId) ?: return false
        // v3 H-14：仅提炼游标之后的消息，实现会话内增量提炼
        val cursor = session.extractCursor
        // 撤回的消息正文已被清空，不能再作为记忆提炼的输入
        val allVisible = messageDao.visibleForExtraction(sessionId)
            // v3 §8.2：L2 及以上的危机消息整条跳过，绝不提炼进记忆
            .filterNot {
                it.role == ROLE_USER &&
                    CrisisDetector.level(it.content).ordinal >= CrisisLevel.L2_SELF_HARM_IDEA.ordinal
            }
        val messages = allVisible.filter { it.createdAt > cursor }
        if (messages.isEmpty()) {
            sessionDao.markExtractDone(sessionId)
            // 没有新消息也前移游标，避免重复判定
            allVisible.lastOrNull()?.let { sessionDao.updateExtractCursor(sessionId, it.createdAt) }
            return true
        }
        val prompt = soul.extractMemoriesPrompt(title, messages.map { it.toSoulMessage() })
        val raw = router.completeJson(BrainRole.EXTRACT, null, prompt)
        if (raw == null) {
            sessionDao.markExtractFailed(sessionId)
            return false
        }
        val parsed = MemoryExtractionParser.parseMemories(raw)
        if (!MemoryExtractionParser.hasParsableArray(raw)) {
            // 输出被截断 / 夹带说明 ≠ 确实没有可记内容：游标留在原处，允许下次重试。
            // 累计失败到上限后标 done 停止重排（避免死循环），游标仍不前进，
            // 后续有新消息时这批消息会一起再次参与提炼。
            if (session.extractAttempts + 1 >= MAX_PARSE_FAILURES) {
                sessionDao.markExtractDone(sessionId)
                return true
            }
            sessionDao.markExtractFailed(sessionId)
            return false
        }
        val now = System.currentTimeMillis()
        for (item in parsed) {
            if (item.content.isBlank()) continue
            // v3 §8.3：EXTRACT 产出的记忆会进 prompt，命中注入模式直接丢弃（不落库）
            val safeContent = InjectionGuard.sanitizeForeign(item.content, PromptZone.MEMORY_TEXT)
            if (safeContent.isBlank()) continue
            if (memoryDao.findByContent(safeContent) != null) continue
            if (cardDao.findByContent(safeContent) != null) continue
            memoryDao.insert(
                MemoryEntity(
                    tier = MemoryTier.SHORT.name,
                    content = safeContent,
                    type = item.type.name,
                    tags = item.tags.joinToString(","),
                    importance = item.importance,
                    strength = item.importance * 20,
                    reviewCount = 0,
                    lastAccess = now,
                    createdAt = now,
                    sourceSessionId = sessionId,
                )
            )
        }
        // v3 H-14：提炼完成后游标前移到本轮最后一条消息
        messages.lastOrNull()?.let { sessionDao.updateExtractCursor(sessionId, it.createdAt) }
        sessionDao.markExtractDone(sessionId)
        return true
    }

    /** 显式记忆指令：直写长期卡片，跳过短期碎片（PRD 5.2.1）。 */
    suspend fun writeUserCommandCard(content: String, tags: List<String>, category: String): Long? {
        if (content.isBlank()) return null
        cardDao.findByContent(content)?.let { return it.id }
        val now = System.currentTimeMillis()
        return cardDao.insert(
            FactCardEntity(
                category = category.ifBlank { com.xiaoyu.soul.memory.CARD_CATEGORY_PROFILE },
                content = content,
                tags = tags.joinToString(","),
                strength = 100,
                source = FactSource.USER_COMMAND,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun recallForChat(userText: String, limit: Int = 6): List<MemoryFragment> {
        val all = memoryDao.shortTerm().map { it.toSoul() }
        val now = System.currentTimeMillis()
        return MemoryRecall.recall(all, userText, now, limit)
    }

    // ---- 本地向量召回（M7-10，可选）----

    /** 没有 embedding 模型 / 没有索引数据时返回空列表，调用方按“无结果”降级即可。 */
    suspend fun recallByVector(query: FloatArray?, limit: Int = 6): List<MemoryFragment> {
        if (query == null || query.isEmpty()) return emptyList()
        val rows = embeddingDao.all()
        if (rows.isEmpty()) return emptyList()
        val candidates = rows.mapNotNull { row ->
            val vector = VectorRetriever.decode(row.vector)
            if (vector.isEmpty() || vector.size != query.size) null else VectorRetriever.Record(row.memoryId, vector)
        }
        if (candidates.isEmpty()) return emptyList()
        val scored = VectorRetriever.topK(query, candidates, limit.coerceIn(1, 50))
        if (scored.isEmpty()) return emptyList()
        val byId = memoryDao.byIds(scored.map { it.memoryId }).associateBy { it.id }
        return scored.mapNotNull { byId[it.memoryId]?.toSoul() }
    }

    /** 写入/覆盖一条记忆向量（memoryId + model 唯一）。 */
    suspend fun storeEmbedding(memoryId: Long, vector: FloatArray, model: String) {
        if (vector.isEmpty() || model.isBlank()) return
        embeddingDao.upsert(
            EmbeddingEntity(
                memoryId = memoryId,
                vector = VectorRetriever.encode(vector),
                model = model,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun embeddingCount(): Int = embeddingDao.count()

    suspend fun markRecalled(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        for (memory in memoryDao.byIds(ids)) {
            memoryDao.updateStrength(
                id = memory.id,
                strength = ForgettingCurve.boostOnRecall(memory.strength),
                reviewCount = memory.reviewCount + 1,
                at = now,
            )
        }
    }

    suspend fun userCommandCards(): List<FactCardEntity> =
        cardDao.all().filter { it.source == FactSource.USER_COMMAND }

    suspend fun updateCard(id: Long, content: String, category: String, tags: String) {
        val card = cardDao.byId(id) ?: return
        cardDao.update(card.copy(content = content, category = category, tags = tags, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCard(id: Long) = cardDao.delete(id)

    suspend fun deleteMemory(id: Long) = memoryDao.purge(listOf(id))

    suspend fun restoreMemory(id: Long) = memoryDao.restore(id, 40)

    suspend fun editMemory(id: Long, content: String, type: MemoryType, tags: String, importance: Int) =
        memoryDao.edit(id, content, type.name, tags, importance)

    /** 每晚清算：衰减 → 回收站 → 30 天物理删 → 晋升合并（PRD 5.3/5.4/5.8）。 */
    suspend fun runNightlySettlement(now: Long = System.currentTimeMillis()) {
        val shortTerm = memoryDao.shortTerm()
        for (memory in shortTerm) {
            val strength = ForgettingCurve.nightlyStrength(
                current = memory.strength,
                lastAccess = memory.lastAccess,
                now = now,
                importance = memory.importance,
            )
            // 衰减与推进 last_access 必须同一条 UPDATE：否则下一晚会把已经衰减过的天数
            // 再按累计天数衰减一次（复合衰减），importance=3 的碎片约 3 晚就会进回收站。
            memoryDao.updateStrength(memory.id, strength, memory.reviewCount, now)
            if (ForgettingCurve.shouldBin(strength)) {
                memoryDao.moveToBin(memory.id, now)
            }
        }

        val bin = memoryDao.bin()
        val purged = bin.filter { ForgettingCurve.shouldPurge(it.binAt, now) }.map { it.id }
        if (purged.isNotEmpty()) memoryDao.purge(purged)

        // 长期卡片只做过期检查，永不自动进回收站
        val cards = cardDao.all()
        val expired = cards.filter { it.expireAt != null && it.expireAt < now }
        for (card in expired) {
            cardDao.update(card.copy(content = "曾经${card.content}", expireAt = null, updatedAt = now))
        }

        runPromotions(now)
    }

    /** 短期 → 长期晋升合并（PRD 5.3）。 */
    suspend fun runPromotions(now: Long = System.currentTimeMillis()) {
        val shortTerm = memoryDao.shortTerm().map { it.toSoul() }
        if (shortTerm.isEmpty()) return
        val batches = PromotionRule.promotions(shortTerm, now)
        val existingCards = cardDao.all().map { it.toSoul() }
        for (batch in batches) {
            val prompt = soul.promoteMemoriesPrompt(batch, existingCards)
            val raw = router.completeJson(BrainRole.EXTRACT, null, prompt)
            val merged = MemoryExtractionParser.parseCardMerge(raw) ?: continue
            val entity = FactCardEntity(
                category = merged.category,
                content = merged.content,
                tags = merged.tags.joinToString(","),
                strength = 100,
                expireAt = merged.expireAt,
                source = FactSource.AI_EXTRACT,
                createdAt = now,
                updatedAt = now,
                sourceMemoryIds = batch.memories.joinToString(",") { it.id.toString() },
            )
            val cardId = cardDao.insert(entity)
            memoryDao.markPromoted(batch.memories.map { it.id }, cardId)
        }
    }

    suspend fun countCreatedSince(since: Long): Int = memoryDao.countCreatedSince(since)

    suspend fun createdBetween(start: Long, end: Long): List<MemoryFragment> =
        memoryDao.createdBetween(start, end).map { it.toSoul() }

    suspend fun tagsWithCounts(): Map<String, Int> =
        memoryDao.shortTerm()
            .flatMap { it.tags.split(",").map { tag -> tag.trim() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(12)
            .toMap()

    suspend fun shortMemories(limit: Int = 10): List<String> =
        memoryDao.shortTerm().take(limit).map { it.content }

    /** EXTRACT 确认失败的显式记忆，后台重试（PRD 5.2.1 兜底）。 */
    suspend fun processPendingExplicitFacts(): Boolean {
        val pending = settings.current().pendingExplicitFacts
        if (pending.isEmpty()) return true
        val remaining = mutableListOf<String>()
        for (text in pending) {
            val raw = router.completeJson(BrainRole.EXTRACT, null, soul.explicitMemoryCheckPrompt(text))
            if (raw == null) {
                remaining += text
                continue
            }
            val parsed = MemoryExtractionParser.parseExplicitMemory(raw)
            val card = parsed.card
            if (parsed.remember && card != null) {
                writeUserCommandCard(card.content, card.tags, card.category)
            }
        }
        settings.update { it.copy(pendingExplicitFacts = remaining) }
        return remaining.isEmpty()
    }
}
