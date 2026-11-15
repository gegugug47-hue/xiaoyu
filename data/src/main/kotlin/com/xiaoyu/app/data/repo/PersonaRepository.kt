package com.xiaoyu.app.data.repo

import android.content.Context
import com.xiaoyu.app.data.db.IdentityDao
import com.xiaoyu.app.data.db.IdentityEntity
import com.xiaoyu.app.data.db.StyleDao
import com.xiaoyu.app.data.db.StyleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 人设系统：身份层 / 风格层双层 CRUD，预置 1 身份 + 3 风格（PRD 4.3）。 */
@Singleton
class PersonaRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val identityDao: IdentityDao,
    private val styleDao: StyleDao,
) {
    fun observeIdentities(): Flow<List<IdentityEntity>> = identityDao.observeAll()

    fun observeStyles(): Flow<List<StyleEntity>> = styleDao.observeAll()

    suspend fun identities(): List<IdentityEntity> = identityDao.all()

    suspend fun styles(): List<StyleEntity> = styleDao.all()

    suspend fun identity(id: Long): IdentityEntity? = identityDao.byId(id)

    suspend fun style(id: Long): StyleEntity? = styleDao.byId(id)

    /** 首次启动播种：assets/personas 下的通用示例，不含任何私人内容（PRD 17.2-5）。 */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (styleDao.count() == 0) {
            seedStyle("gentle", "温柔陪伴型", "personas/style_gentle.md")
            seedStyle("playful", "活泼损友型", "personas/style_playful.md")
            seedStyle("calm", "冷静知性型", "personas/style_calm.md")
        }
        if (identityDao.count() == 0) {
            val content = asset("personas/identity_default.md")
                ?: "你是小鱼，25 岁，住在海边小城，喜欢在窗边晒太阳、听周杰伦、研究做菜。你和用户是认识很久的朋友，慢慢变得更亲近。"
            identityDao.insert(
                IdentityEntity(
                    name = "小鱼",
                    contentMd = content,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    presetKey = "default",
                )
            )
        }
    }

    private suspend fun seedStyle(key: String, name: String, path: String) {
        if (styleDao.byPresetKey(key) != null) return
        val content = asset(path) ?: return
        styleDao.insert(
            StyleEntity(
                name = name,
                contentMd = content,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                presetKey = key,
            )
        )
    }

    private fun asset(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrNull()

    suspend fun createIdentity(name: String, content: String): Long = identityDao.insert(
        IdentityEntity(
            name = name.ifBlank { "她" },
            contentMd = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    )

    suspend fun updateIdentity(id: Long, name: String, content: String) {
        val existing = identityDao.byId(id) ?: return
        identityDao.update(existing.copy(name = name, contentMd = content, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteIdentity(id: Long) {
        if (identityDao.count() <= 1) return
        identityDao.delete(id)
    }

    suspend fun createStyle(name: String, content: String): Long = styleDao.insert(
        StyleEntity(
            name = name.ifBlank { "新风格" },
            contentMd = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    )

    suspend fun updateStyle(id: Long, name: String, content: String) {
        val existing = styleDao.byId(id) ?: return
        styleDao.update(existing.copy(name = name, contentMd = content, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteStyle(id: Long) {
        if (styleDao.count() <= 1) return
        styleDao.delete(id)
    }

    suspend fun defaultStyleId(): Long = styleDao.all().firstOrNull()?.id ?: 0L
}
