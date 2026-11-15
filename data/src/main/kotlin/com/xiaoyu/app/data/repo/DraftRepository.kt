package com.xiaoyu.app.data.repo

import com.xiaoyu.app.data.db.DraftDao
import com.xiaoyu.app.data.db.DraftEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 草稿箱（执行书 M1-09）：按人设身份保存未发送文本，
 * 切回该身份时自动恢复，避免写到一半切走就丢。
 */
@Singleton
class DraftRepository @Inject constructor(
    private val draftDao: DraftDao,
) {
    suspend fun get(identityId: Long): String? = draftDao.byIdentity(identityId)?.content

    /** 保存草稿；空文本会清除。 */
    suspend fun save(identityId: Long, content: String) {
        if (content.isBlank()) {
            draftDao.clear(identityId)
        } else {
            draftDao.upsert(
                DraftEntity(
                    identityId = identityId,
                    content = content,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun clear(identityId: Long) = draftDao.clear(identityId)
}
