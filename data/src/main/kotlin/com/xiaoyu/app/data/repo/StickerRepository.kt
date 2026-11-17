package com.xiaoyu.app.data.repo

import android.content.Context
import com.xiaoyu.app.data.db.StickerDao
import com.xiaoyu.app.data.db.StickerEntity
import com.xiaoyu.app.data.db.StickerPackEntity
import com.xiaoyu.app.data.sticker.StickerImportItem
import com.xiaoyu.app.data.sticker.StickerImporter
import com.xiaoyu.app.data.sticker.StickerPaths
import com.xiaoyu.app.data.sticker.StickerStorage
import com.xiaoyu.app.data.sticker.StickerStorageUsage
import com.xiaoyu.soul.prompt.StickerCatalogEntry
import com.xiaoyu.soul.safety.InjectionGuard
import com.xiaoyu.soul.safety.PromptZone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 内置表情包 id（执行书 7.4.1）。 */
const val BUILTIN_STICKER_PACK_ID = "builtin-fish"

/** 内置小鱼是 Compose Canvas 代码绘制的，没有二进制素材（R11）。 */
const val MIME_BUILTIN_CANVAS = "builtin/canvas"
const val BUILTIN_STICKER_PREFIX = "builtin://fish/"

/** 表情包来源：用户自建（相册/zip 导入）。 */
const val STICKER_SOURCE_USER = "user"

/**
 * 表情包仓库（执行书 7.4）：内置 8 枚代码绘制的小鱼 + 用户表情的目录、解析与使用统计。
 * 目录编号与注入时的顺序一致，AI 用 `[xy:s=编号]` 引用。
 * 管理页（M4-06）与导入管线（M4-03）的增删改也统一收敛在这里。
 */
@Singleton
class StickerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: StickerDao,
    private val importer: StickerImporter,
    private val storage: StickerStorage,
) {

    fun observeStickers(): Flow<List<StickerEntity>> = dao.observeEnabled()

    fun observePacks(): Flow<List<StickerPackEntity>> = dao.observePacks()

    /** 管理页需要看到停用的表情。 */
    fun observeAllStickers(): Flow<List<StickerEntity>> = dao.observeAll()

    /** 首次启动播种内置小鱼；已存在则跳过，不覆盖用户改过的名称。 */
    suspend fun ensureBuiltinSeeded() {
        if (dao.packById(BUILTIN_STICKER_PACK_ID) != null) return
        val now = System.currentTimeMillis()
        dao.insertPack(
            StickerPackEntity(
                id = BUILTIN_STICKER_PACK_ID,
                name = "小鱼日常",
                author = "小鱼 App",
                source = "builtin",
                coverStickerId = BUILTIN_STICKER_PACK_ID + "-happy",
                sort = 0,
                isBuiltin = true,
                dirPath = BUILTIN_STICKER_PREFIX,
                createdAt = now,
                updatedAt = now,
            )
        )
        dao.insertStickers(
            BUILTINS.mapIndexed { index, item ->
                StickerEntity(
                    id = "$BUILTIN_STICKER_PACK_ID-${item.kind}",
                    packId = BUILTIN_STICKER_PACK_ID,
                    name = item.name,
                    tags = item.tags,
                    moodTags = item.moodTags,
                    filePath = BUILTIN_STICKER_PREFIX + item.kind,
                    mime = MIME_BUILTIN_CANVAS,
                    width = 132,
                    height = 132,
                    sort = index + 1,
                    createdAt = now,
                )
            }
        )
    }

    /** 表情目录（最多 [limit] 条），序号从 1 开始；空表时返回空列表，注入段整体省略。 */
    suspend fun catalogForPrompt(limit: Int = 40): List<StickerCatalogEntry> =
        dao.catalog(limit).mapIndexedNotNull { i, sticker ->
            // v3 §8.3：表情名/标签会进 prompt，入 prompt 前按白名单再清洗一次
            val name = InjectionGuard.sanitizeForeign(sticker.name, PromptZone.STICKER_NAME)
            if (name.isBlank()) return@mapIndexedNotNull null
            StickerCatalogEntry(
                index = i + 1,
                id = sticker.id,
                name = name,
                tags = sticker.tags.split(',')
                    .map { it.trim() }
                    .map { InjectionGuard.sanitizeForeign(it, PromptZone.STICKER_TAG) }
                    .filter { it.isNotEmpty() },
                moodTags = sticker.moodTags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            )
        }

    /**
     * 把 AI 的 token 值解析成表情：优先按目录序号，其次兼容 id / 名称。
     * 用本轮注入的目录解析，避免使用统计改变排序后编号错位。
     */
    suspend fun resolve(value: String?, catalog: List<StickerCatalogEntry>): StickerEntity? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty() || catalog.isEmpty()) return null
        val entry = raw.toIntOrNull()?.let { index -> catalog.firstOrNull { it.index == index } }
            ?: catalog.firstOrNull { it.id == raw || it.name == raw }
            ?: return null
        return dao.byId(entry.id)?.takeIf { it.enabled }
    }

    suspend fun byId(id: String): StickerEntity? = dao.byId(id)

    /** 历史消息里表情 id → 名称，用于注入上下文时还原成人话。 */
    suspend fun namesFor(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        return dao.byIds(ids.distinct()).associate { it.id to it.name }
    }

    suspend fun recordUse(id: String) = dao.recordUse(id, System.currentTimeMillis())

    // ------------------------------------------------------------------
    // M4-06 表情管理：包与单枚表情的增删改、排序、启停、体积统计
    // ------------------------------------------------------------------

    /** 新建用户表情包，返回落库后的实体（id 由时间戳生成，避免中文进入文件路径）。 */
    suspend fun createPack(name: String, author: String? = null): StickerPackEntity {
        val now = System.currentTimeMillis()
        val packId = "user-$now"
        val pack = StickerPackEntity(
            id = packId,
            name = name.trim().ifBlank { "我的表情" },
            author = author?.trim()?.takeIf { it.isNotBlank() },
            source = STICKER_SOURCE_USER,
            sort = dao.maxPackSort() + 1,
            isBuiltin = false,
            dirPath = StickerPaths.packDir(context, packId).path,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertPack(pack)
        return dao.packById(packId) ?: pack
    }

    suspend fun renamePack(id: String, name: String, author: String? = null): Boolean {
        val pack = dao.packById(id) ?: return false
        val newName = name.trim().take(20)
        if (newName.isBlank()) return false
        dao.updatePack(
            pack.copy(
                name = newName,
                author = author?.trim()?.takeIf { it.isNotBlank() } ?: pack.author,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun setPackEnabled(id: String, enabled: Boolean): Boolean {
        val pack = dao.packById(id) ?: return false
        dao.updatePack(pack.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        return true
    }

    /** 上移/下移一个包；内置包不参与排序，避免把「小鱼日常」挤走。 */
    suspend fun movePack(id: String, up: Boolean): Boolean {
        val packs = dao.packs().filterNot { it.isBuiltin }
        val index = packs.indexOfFirst { it.id == id }
        if (index < 0) return false
        val target = if (up) index - 1 else index + 1
        if (target !in packs.indices) return false
        val now = System.currentTimeMillis()
        val a = packs[index]
        val b = packs[target]
        dao.updatePack(a.copy(sort = b.sort, updatedAt = now))
        dao.updatePack(b.copy(sort = a.sort, updatedAt = now))
        return true
    }

    /** 删除包连同其表情与文件；内置包与收藏包不可删。 */
    suspend fun deletePack(id: String): Boolean {
        if (id == BUILTIN_STICKER_PACK_ID) return false
        val pack = dao.packById(id) ?: return false
        if (pack.isBuiltin) return false
        dao.deleteStickersByPack(id)
        dao.deletePack(id)
        withContext(Dispatchers.IO) { importer.deletePackFiles(id) }
        return true
    }

    /** 导入管线落库：sort 依次递增，enabled 默认开启。 */
    suspend fun addImported(packId: String, item: StickerImportItem): StickerEntity {
        val entity = StickerEntity(
            id = item.stickerId,
            packId = packId,
            name = item.name,
            tags = "",
            moodTags = "",
            filePath = item.filePath,
            mime = item.mime,
            width = item.width,
            height = item.height,
            bytes = item.bytes,
            sort = dao.maxStickerSort(packId) + 1,
            createdAt = System.currentTimeMillis(),
        )
        dao.insertStickers(listOf(entity))
        val pack = dao.packById(packId)
        if (pack != null && pack.coverStickerId.isNullOrBlank()) {
            dao.updatePack(pack.copy(coverStickerId = entity.id, updatedAt = entity.createdAt))
        }
        return entity
    }

    /** 自动打标或手动编辑后写回名称与标签。 */
    suspend fun updateStickerMeta(id: String, name: String? = null, tags: String? = null, moodTags: String? = null): Boolean {
        val sticker = dao.byId(id) ?: return false
        dao.updateSticker(
            sticker.copy(
                name = name?.trim()?.take(10)?.takeIf { it.isNotBlank() } ?: sticker.name,
                tags = tags ?: sticker.tags,
                moodTags = moodTags ?: sticker.moodTags,
            )
        )
        return true
    }

    suspend fun setStickerEnabled(id: String, enabled: Boolean): Boolean {
        val sticker = dao.byId(id) ?: return false
        dao.updateSticker(sticker.copy(enabled = enabled))
        return true
    }

    /** 单枚表情上移/下移。 */
    suspend fun moveSticker(id: String, up: Boolean): Boolean {
        val sticker = dao.byId(id) ?: return false
        val siblings = dao.byPack(sticker.packId)
        val index = siblings.indexOfFirst { it.id == id }
        if (index < 0) return false
        val target = if (up) index - 1 else index + 1
        if (target !in siblings.indices) return false
        val other = siblings[target]
        dao.updateSticker(sticker.copy(sort = other.sort))
        dao.updateSticker(other.copy(sort = sticker.sort))
        return true
    }

    /** 删除单枚表情（内置包拒绝），同时删除原图与缩略图。 */
    suspend fun deleteSticker(id: String): Boolean {
        val sticker = dao.byId(id) ?: return false
        if (sticker.mime == MIME_BUILTIN_CANVAS) return false
        dao.deleteSticker(id)
        withContext(Dispatchers.IO) { importer.deleteFiles(sticker.filePath) }
        return true
    }

    /** 单包占用体积与枚数，用于管理页展示。 */
    suspend fun packUsage(packId: String): Pair<Long, Int> {
        val list = dao.byPack(packId)
        return list.sumOf { it.bytes } to list.size
    }

    suspend fun stickerCount(): Int = dao.count()

    /** 存储占用（表情 / 图片 / 语音 / 备份）。 */
    suspend fun storageUsage(): StickerStorageUsage = storage.usage(dao.count())

    private data class Builtin(
        val kind: String,
        val name: String,
        val tags: String,
        val moodTags: String,
    )

    private companion object {
        val BUILTINS = listOf(
            Builtin("happy", "开心到转圈", "开心,兴奋", "joy,excited"),
            Builtin("sad", "委屈巴巴", "委屈,撒娇", "sad,shy"),
            Builtin("angry", "生气鼓腮", "生气,不满", "angry"),
            Builtin("cute", "卖萌眨眨", "卖萌,可爱", "shy,tease"),
            Builtin("speechless", "无语子", "无语,尴尬", "speechless"),
            Builtin("night", "晚安月亮", "晚安,困", "tired"),
            Builtin("think", "认真思考", "思考,疑惑", "think"),
            Builtin("heart", "给你比心", "比心,喜欢", "love"),
        )
    }
}
