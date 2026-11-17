package com.xiaoyu.app.data.repo

import android.os.Build
import com.xiaoyu.app.data.db.ChatBackgroundDao
import com.xiaoyu.app.data.db.ChatBackgroundEntity
import com.xiaoyu.app.data.db.IdentityDao
import com.xiaoyu.app.data.media.ChatBackgroundStore
import com.xiaoyu.app.data.settings.ChatBackgroundCodec
import com.xiaoyu.app.data.settings.ChatBackgroundSpec
import com.xiaoyu.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * 渲染所需的输入（§14.6.2 / §14.6.3）。
 *
 * @param spec 已按深色模式解析过的参数（深色独立设置时取 dark_json）
 * @param imageFile 原图（`files/bg/<sha1>.jpg`）
 * @param preRenderedFile API < 31 命中的预渲染模糊缓存；未命中为 null
 * @param effectiveBlurDp 实际生效模糊（低端机上限 18dp）
 * @param fromCache 是否命中预渲染缓存
 * @param lowRam 低端机（禁用视差）
 */
data class BackgroundRenderInput(
    val spec: ChatBackgroundSpec,
    val imageFile: File?,
    val preRenderedFile: File?,
    val effectiveBlurDp: Int,
    val fromCache: Boolean,
    val lowRam: Boolean,
)

/**
 * 自定义聊天背景仓库（执行书 v3 §14.6.2 / G-11 / G-15）。
 *
 * 读写 `chat_backgrounds`（每身份一行）；首次读取时把旧版
 * `settings.chatBackground / chatBackgroundBlur / chatBackgroundDim`
 * 迁移成一条 `source=image` 的规格，不改旧键、不丢数据。
 */
@Singleton
class ChatBackgroundRepository @Inject constructor(
    private val dao: ChatBackgroundDao,
    private val identityDao: IdentityDao,
    private val store: ChatBackgroundStore,
    private val settings: SettingsRepository,
) {

    fun observe(identityId: Long): Flow<ChatBackgroundSpec> =
        dao.observe(identityId)
            .onStart { ensureRow(identityId) }
            .map { it?.toSpec() ?: ChatBackgroundSpec() }

    suspend fun load(identityId: Long): ChatBackgroundSpec =
        (ensureRow(identityId) ?: dao.byIdentity(identityId))?.toSpec() ?: ChatBackgroundSpec()

    /** 保存；[applyToAll] 为 true 时同步写入所有身份。 */
    suspend fun save(identityId: Long, spec: ChatBackgroundSpec, applyToAll: Boolean) {
        val safe = sanitize(spec)
        val now = System.currentTimeMillis()
        val targets = if (applyToAll) {
            identityDao.all().map { it.id }.ifEmpty { listOf(identityId) }.distinct()
        } else {
            listOf(identityId)
        }
        targets.forEach { id ->
            val existing = dao.byIdentity(id)
            dao.upsert(
                safe.toEntity(
                    identityId = id,
                    id = existing?.id ?: 0L,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
        store.enforceQuota(dao.usedImages().toSet() + listOfNotNull(safe.imageSha1))
        safe.imageSha1?.let { store.warmCache(it) }
    }

    /** 导入相册图片，返回 sha1；配额在这里一起收口。 */
    suspend fun importImage(uri: android.net.Uri): String? {
        val sha1 = store.importFrom(uri) ?: return null
        store.enforceQuota(dao.usedImages().toSet() + sha1)
        return sha1
    }

    /** 恢复默认：删除该身份（或所有身份）的自定义背景行，不动旧 settings 键。 */
    suspend fun reset(identityId: Long, applyToAll: Boolean = false) {
        if (applyToAll) {
            identityDao.all().forEach { dao.delete(it.id) }
        } else {
            dao.delete(identityId)
        }
    }

    /** 把参数解析成渲染输入；API 26–30 走预渲染缓存。 */
    suspend fun resolveRender(identityId: Long, darkTheme: Boolean): BackgroundRenderInput {
        val spec = load(identityId).forDark(darkTheme)
        val file = spec.imageSha1?.let { store.fileFor(it) }
        val lowRam = store.isLowRam()
        val blur = if (lowRam) minOf(spec.blurDp, LOW_RAM_MAX_BLUR_DP) else spec.blurDp
        var cache: File? = null
        var fromCache = false
        if (file != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blur > 0) {
            val cached = store.ensureCache(
                sha1 = spec.imageSha1.orEmpty(),
                blurTier = blur,
                edgePx = store.renderEdgePx(),
                dark = darkTheme,
            )
            if (cached != null) {
                cache = cached
                fromCache = true
            }
        }
        return BackgroundRenderInput(
            spec = spec,
            imageFile = file,
            preRenderedFile = cache,
            effectiveBlurDp = blur,
            fromCache = fromCache,
            lowRam = lowRam,
        )
    }

    /**
     * 旧版三键 → 一行规格（只在没有行且旧键有文件名时做一次）。
     * 不删除、不覆盖旧键。
     */
    private suspend fun ensureRow(identityId: Long): ChatBackgroundEntity? {
        val existing = dao.byIdentity(identityId)
        if (existing != null) return existing
        val legacy = settings.current()
        val sha1 = store.adoptLegacy(legacy.chatBackground) ?: return null
        val spec = ChatBackgroundSpec(
            source = ChatBackgroundSpec.SOURCE_IMAGE,
            imageSha1 = sha1,
            blurDp = ChatBackgroundSpec.snapBlur(legacy.chatBackgroundBlur.roundToInt()),
            scrimAlpha = (legacy.chatBackgroundDim * 100).roundToInt().coerceIn(0, 80),
        )
        val now = System.currentTimeMillis()
        val entity = spec.toEntity(identityId = identityId, id = 0L, createdAt = now, updatedAt = now)
        dao.upsert(entity)
        return dao.byIdentity(identityId)
    }

    /** 区间校验 + 吸附，保证入库值一定可用（失败只纠正，不崩）。 */
    private fun sanitize(spec: ChatBackgroundSpec): ChatBackgroundSpec = spec.copy(
        blurDp = ChatBackgroundSpec.snapBlur(spec.blurDp),
        brightness = spec.brightness.coerceIn(-40, 20),
        saturation = spec.saturation.coerceIn(0, 120),
        scrimAlpha = spec.scrimAlpha.coerceIn(0, 80),
        vignette = spec.vignette.coerceIn(0, 60),
        bubbleAlpha = spec.bubbleAlpha.coerceIn(55, 100),
        zoom = spec.zoom.coerceIn(100, 160),
        parallax = if (spec.parallax in ChatBackgroundSpec.PARALLAX_LEVELS) spec.parallax else 0,
        darkJson = spec.darkJson?.takeIf { it.isNotBlank() },
    )

    private companion object {
        const val LOW_RAM_MAX_BLUR_DP = 18
    }
}

private fun ChatBackgroundEntity.toSpec(): ChatBackgroundSpec = ChatBackgroundSpec(
    source = source,
    imageSha1 = imageSha1,
    gradientJson = gradientJson,
    texture = texture,
    solidHex = solidHex,
    scale = scale,
    zoom = zoom,
    anchor = anchor,
    blurDp = blurDp,
    brightness = brightness,
    saturation = saturation,
    scrimTone = scrimTone,
    scrimHex = scrimHex,
    scrimAlpha = scrimAlpha,
    vignette = vignette,
    bubbleAlpha = bubbleAlpha,
    bubbleUnderlay = bubbleUnderlay,
    bubbleBlur = bubbleBlur,
    parallax = parallax,
    legibilityAuto = legibilityAuto,
    darkJson = darkJson,
)

private fun ChatBackgroundSpec.toEntity(
    identityId: Long,
    id: Long,
    createdAt: Long,
    updatedAt: Long,
): ChatBackgroundEntity = ChatBackgroundEntity(
    id = id,
    identityId = identityId,
    source = source,
    imageSha1 = imageSha1,
    gradientJson = gradientJson,
    texture = texture,
    solidHex = solidHex,
    scale = scale,
    zoom = zoom,
    anchor = anchor,
    blurDp = blurDp,
    brightness = brightness,
    saturation = saturation,
    scrimTone = scrimTone,
    scrimHex = scrimHex,
    scrimAlpha = scrimAlpha,
    vignette = vignette,
    bubbleAlpha = bubbleAlpha,
    bubbleUnderlay = bubbleUnderlay,
    bubbleBlur = bubbleBlur,
    parallax = parallax,
    legibilityAuto = legibilityAuto,
    darkJson = darkJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** 深色独立设置时，把浅色规格 + 深色 JSON 合成一套（供设置页保存使用）。 */
fun ChatBackgroundSpec.withDarkVariant(dark: ChatBackgroundSpec?): ChatBackgroundSpec =
    copy(darkJson = dark?.let { ChatBackgroundCodec.encode(it.copy(darkJson = null)) })
