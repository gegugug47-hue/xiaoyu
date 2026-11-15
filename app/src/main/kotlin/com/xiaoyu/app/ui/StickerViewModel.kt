package com.xiaoyu.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoyu.app.data.db.StickerEntity
import com.xiaoyu.app.data.db.StickerPackEntity
import com.xiaoyu.app.data.repo.StickerRepository
import com.xiaoyu.app.data.sticker.StickerStorageUsage
import com.xiaoyu.app.work.StickerImportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 表情管理页 / 导入页的状态与操作（执行书 M4-03 / M4-06）。
 * 只依赖 [StickerRepository] 与后台 Worker，不改动 AppViewModel 这类中央文件。
 */
@HiltViewModel
class StickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StickerRepository,
) : ViewModel() {

    val packs: StateFlow<List<StickerPackEntity>> = repository.observePacks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 含停用表情，管理页需要全都看到。 */
    val stickers: StateFlow<List<StickerEntity>> = repository.observeAllStickers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _storage = MutableStateFlow(StickerStorageUsage())
    val storage: StateFlow<StickerStorageUsage> = _storage.asStateFlow()

    /** 一次性提示（新建/删除/导入开始等），页面消费后调用 [clearMessage]。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshStorage()
        // 导入完成或删除后表情数量变化，顺手刷新一次占用统计
        viewModelScope.launch {
            stickers.map { it.size }.distinctUntilChanged().collect { refreshStorage() }
        }
    }

    fun refreshStorage() {
        viewModelScope.launch { _storage.value = repository.storageUsage() }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun createPack(name: String) {
        viewModelScope.launch {
            repository.createPack(name)
            _message.value = "已新建表情包"
            refreshStorage()
        }
    }

    fun renamePack(id: String, name: String) {
        viewModelScope.launch {
            if (repository.renamePack(id, name)) _message.value = "已保存"
        }
    }

    fun setPackEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setPackEnabled(id, enabled) }
    }

    fun movePack(id: String, up: Boolean) {
        viewModelScope.launch { repository.movePack(id, up) }
    }

    fun deletePack(id: String) {
        viewModelScope.launch {
            _message.value = if (repository.deletePack(id)) "已删除表情包" else "内置表情包不能删除"
            refreshStorage()
        }
    }

    fun updateStickerMeta(id: String, name: String, tags: String, moodTags: String) {
        viewModelScope.launch {
            if (repository.updateStickerMeta(id, name, tags, moodTags)) _message.value = "已保存"
        }
    }

    fun setStickerEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setStickerEnabled(id, enabled) }
    }

    fun moveSticker(id: String, up: Boolean) {
        viewModelScope.launch { repository.moveSticker(id, up) }
    }

    fun deleteSticker(id: String) {
        viewModelScope.launch {
            _message.value = if (repository.deleteSticker(id)) "已删除表情" else "内置表情不能删除"
            refreshStorage()
        }
    }

    /** 交给 WorkManager 后台导入，界面立即返回，进度与结果走通知。 */
    fun startImport(packId: String, uris: List<Uri>, autoTag: Boolean) {
        if (packId.isBlank() || uris.isEmpty()) return
        StickerImportWorker.enqueue(context, packId, uris, autoTag)
        _message.value = "已开始导入，完成后会收到通知"
    }
}
