package com.xiaoyu.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.db.StickerEntity
import com.xiaoyu.app.data.db.StickerPackEntity
import com.xiaoyu.app.data.repo.MIME_BUILTIN_CANVAS
import com.xiaoyu.app.data.sticker.StickerStorage
import com.xiaoyu.app.data.sticker.StickerStorageUsage

/**
 * 表情管理页（执行书 M4-06 / 7.4.6）：
 * 新建表情包、排序、改名、停用启用、删除、编辑单枚表情的名称与标签，并展示存储占用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManageScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onImport: (packId: String, packName: String) -> Unit,
    viewModel: StickerViewModel = hiltViewModel(),
) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val stickers by viewModel.stickers.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // M7-07：整包导入 / 导出（.xystickers）
    val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { appViewModel.importStickerPack(it) }
    }

    var selectedPackId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newPackName by remember { mutableStateOf("") }
    var renamingPack by remember { mutableStateOf<StickerPackEntity?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var importingPack by remember { mutableStateOf<StickerPackEntity?>(null) }
    var editingSticker by remember { mutableStateOf<StickerEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.clearMessage()
    }

    val byPack = remember(stickers) { stickers.groupBy { it.packId } }
    val selectedPack = packs.firstOrNull { it.id == selectedPackId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sticker_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.sticker_action_back)) }
                },
                actions = {
                    TextButton(onClick = { packPicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.sticker_action_import_pack)) }
                    TextButton(
                        onClick = { selectedPack?.let { appViewModel.shareStickerPack(it.id) } },
                        enabled = selectedPack != null && !selectedPack.isBuiltin,
                    ) { Text(stringResource(R.string.sticker_action_export)) }
                    TextButton(onClick = {
                        newPackName = ""
                        creating = true
                    }) { Text(stringResource(R.string.sticker_action_create)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StorageCard(storage) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        newPackName = ""
                        creating = true
                    }) { Text(stringResource(R.string.sticker_action_create_pack)) }
                    val target = selectedPack ?: packs.firstOrNull { !it.isBuiltin }
                    OutlinedButton(
                        onClick = { target?.let { importingPack = it } },
                        enabled = target != null,
                    ) { Text(stringResource(R.string.sticker_action_import_images)) }
                }
            }
            item {
                Text(stringResource(R.string.sticker_section_packs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (packs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.sticker_empty_packs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(packs.size, key = { packs[it].id }) { index ->
                val pack = packs[index]
                PackRow(
                    pack = pack,
                    cover = byPack[pack.id].orEmpty(),
                    sizeBytes = byPack[pack.id].orEmpty().sumOf { it.bytes },
                    selected = pack.id == selectedPackId,
                    isFirstUserPack = packs.filterNot { it.isBuiltin }.firstOrNull()?.id == pack.id,
                    isLastUserPack = packs.filterNot { it.isBuiltin }.lastOrNull()?.id == pack.id,
                    onClick = { selectedPackId = if (selectedPackId == pack.id) null else pack.id },
                    onToggle = { viewModel.setPackEnabled(pack.id, it) },
                    onUp = { viewModel.movePack(pack.id, true) },
                    onDown = { viewModel.movePack(pack.id, false) },
                    onRename = {
                        renamingPack = pack
                        renameValue = pack.name
                    },
                    onDelete = { pendingDelete = PendingDelete.Pack(pack) },
                    onImport = { importingPack = pack },
                )
                if (pack.id == selectedPackId) {
                    Divider(Modifier.padding(vertical = 4.dp))
                }
            }
            selectedPack?.let { pack ->
                item {
                    Text(
                        stringResource(R.string.sticker_section_pack_stickers, pack.name),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    val list = byPack[pack.id].orEmpty()
                    if (list.isEmpty()) {
                        Text(
                            stringResource(R.string.sticker_empty_pack_stickers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        StickerGrid(
                            stickers = list,
                            onClick = { editingSticker = it },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.sticker_dialog_create_pack_title)) },
            text = {
                OutlinedTextField(
                    value = newPackName,
                    onValueChange = { newPackName = it.take(20) },
                    label = { Text(stringResource(R.string.sticker_label_pack_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createPack(newPackName)
                    creating = false
                }) { Text(stringResource(R.string.sticker_action_confirm_create)) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.sticker_action_cancel)) } },
        )
    }

    renamingPack?.let { pack ->
        AlertDialog(
            onDismissRequest = { renamingPack = null },
            title = { Text(stringResource(R.string.sticker_dialog_rename_pack_title)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(20) },
                    label = { Text(stringResource(R.string.sticker_label_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renamePack(pack.id, renameValue)
                    renamingPack = null
                }) { Text(stringResource(R.string.sticker_action_save)) }
            },
            dismissButton = { TextButton(onClick = { renamingPack = null }) { Text(stringResource(R.string.sticker_action_cancel)) } },
        )
    }

    importingPack?.let { pack ->
        AlertDialog(
            onDismissRequest = { importingPack = null },
            title = { Text(stringResource(R.string.sticker_dialog_import_title, pack.name)) },
            text = { Text(stringResource(R.string.sticker_dialog_import_message)) },
            confirmButton = {
                TextButton(onClick = {
                    importingPack = null
                    onImport(pack.id, pack.name)
                }) { Text(stringResource(R.string.sticker_action_go_select)) }
            },
            dismissButton = { TextButton(onClick = { importingPack = null }) { Text(stringResource(R.string.sticker_action_cancel)) } },
        )
    }

    editingSticker?.let { sticker ->
        StickerEditDialog(
            sticker = sticker,
            canDelete = sticker.mime != MIME_BUILTIN_CANVAS,
            onDismiss = { editingSticker = null },
            onSave = { name, tags, moodTags ->
                viewModel.updateStickerMeta(sticker.id, name, tags, moodTags)
                editingSticker = null
            },
            onToggle = { enabled -> viewModel.setStickerEnabled(sticker.id, enabled) },
            onUp = { viewModel.moveSticker(sticker.id, true) },
            onDown = { viewModel.moveSticker(sticker.id, false) },
            onDelete = {
                editingSticker = null
                pendingDelete = PendingDelete.Sticker(sticker)
            },
        )
    }

    when (val target = pendingDelete) {
        is PendingDelete.Pack -> ConfirmDeleteDialog(
            title = stringResource(R.string.sticker_dialog_delete_pack_title),
            text = stringResource(R.string.sticker_dialog_delete_pack_message, target.pack.name),
            onConfirm = {
                viewModel.deletePack(target.pack.id)
                if (selectedPackId == target.pack.id) selectedPackId = null
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
        is PendingDelete.Sticker -> ConfirmDeleteDialog(
            title = stringResource(R.string.sticker_dialog_delete_sticker_title),
            text = stringResource(R.string.sticker_dialog_delete_sticker_message, target.sticker.name),
            onConfirm = {
                viewModel.deleteSticker(target.sticker.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
        null -> Unit
    }
}

private sealed interface PendingDelete {
    data class Pack(val pack: StickerPackEntity) : PendingDelete
    data class Sticker(val sticker: StickerEntity) : PendingDelete
}

/** 存储占用：表情 / 图片 / 语音 / 备份分类展示（执行书 7.4.5）。 */
@Composable
private fun StorageCard(usage: StickerStorageUsage) {
    val warn = usage.stickerBytes > StickerStorage.STICKER_STORAGE_WARN_MB * 1024L * 1024L
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.sticker_storage_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            StorageLine(stringResource(R.string.sticker_storage_stickers), usage.stickerBytes, stringResource(R.string.sticker_storage_count, usage.stickerCount))
            StorageLine(stringResource(R.string.sticker_storage_images), usage.imageBytes, null)
            StorageLine(stringResource(R.string.sticker_storage_voice), usage.voiceBytes, null)
            StorageLine(stringResource(R.string.sticker_storage_backup), usage.backupBytes, null)
            Divider()
            StorageLine(stringResource(R.string.sticker_storage_total), usage.totalBytes, null)
            if (warn) {
                Text(
                    stringResource(R.string.sticker_storage_warn, StickerStorage.STICKER_STORAGE_WARN_MB),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB8860B),
                )
            }
        }
    }
}

@Composable
private fun StorageLine(label: String, bytes: Long, extra: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (extra != null) {
            Text(
                extra,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            StickerStorage.formatSize(bytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PackRow(
    pack: StickerPackEntity,
    cover: List<StickerEntity>,
    sizeBytes: Long,
    selected: Boolean,
    isFirstUserPack: Boolean,
    isLastUserPack: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val coverSticker = cover.firstOrNull { it.id == pack.coverStickerId } ?: cover.firstOrNull()
                StickerArt(sticker = coverSticker, size = 44.dp, useThumbnail = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pack.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.sticker_pack_meta, cover.size, StickerStorage.formatSize(sizeBytes)) +
                            if (pack.isBuiltin) stringResource(R.string.sticker_pack_builtin_suffix) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = pack.enabled, onCheckedChange = onToggle)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onUp, enabled = !pack.isBuiltin && !isFirstUserPack) { Text("↑") }
                TextButton(onClick = onDown, enabled = !pack.isBuiltin && !isLastUserPack) { Text("↓") }
                TextButton(onClick = onRename) { Text(stringResource(R.string.sticker_action_rename)) }
                if (!pack.isBuiltin) {
                    TextButton(onClick = onImport) { Text(stringResource(R.string.sticker_action_import)) }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.sticker_action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerGrid(stickers: List<StickerEntity>, onClick: (StickerEntity) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        stickers.forEach { sticker ->
            Column(
                modifier = Modifier.width(76.dp).clickable { onClick(sticker) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    StickerArt(sticker = sticker, size = 64.dp, useThumbnail = true, playGif = false)
                }
                Text(
                    sticker.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (sticker.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!sticker.enabled) {
                    Text(
                        stringResource(R.string.sticker_status_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerEditDialog(
    sticker: StickerEntity,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, tags: String, moodTags: String) -> Unit,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(sticker.id) { mutableStateOf(sticker.name) }
    var tags by remember(sticker.id) { mutableStateOf(sticker.tags) }
    var moodTags by remember(sticker.id) { mutableStateOf(sticker.moodTags) }
    var enabled by remember(sticker.id) { mutableStateOf(sticker.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sticker_dialog_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StickerArt(sticker = sticker, size = 96.dp, useThumbnail = false, playGif = true)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(10) },
                    label = { Text(stringResource(R.string.sticker_label_name_max10)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.sticker_label_semantic_tags)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = moodTags,
                    onValueChange = { moodTags = it },
                    label = { Text(stringResource(R.string.sticker_label_mood_tags)) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sticker_label_enabled), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            onToggle(it)
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onUp) { Text(stringResource(R.string.sticker_action_move_up)) }
                    TextButton(onClick = onDown) { Text(stringResource(R.string.sticker_action_move_down)) }
                    if (canDelete) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.sticker_action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    "${sticker.mime} · ${sticker.width}×${sticker.height} · ${StickerStorage.formatSize(sticker.bytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name.trim(), normalizeCsv(tags, 4), normalizeCsv(moodTags, 3))
            }) { Text(stringResource(R.string.sticker_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sticker_action_cancel)) } },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sticker_action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sticker_action_cancel)) } },
    )
}

/** 标签输入容错：中英文逗号都拆开，去空去重，限制个数。 */
private fun normalizeCsv(raw: String, limit: Int): String =
    raw.split(',', '，')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(limit)
        .joinToString(",")
