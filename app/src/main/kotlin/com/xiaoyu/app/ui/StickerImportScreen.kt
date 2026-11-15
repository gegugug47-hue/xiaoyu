package com.xiaoyu.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R

/** 一次最多 30 个文件（执行书 7.4.3，与导入管线上限保持一致）。 */
private const val MAX_SELECTION = 30

/**
 * 表情导入页（执行书 M4-03）：从相册多选图片，或选择 zip / .xystickers 压缩包，
 * 交给 WorkManager 后台转码入库；页面底部固定展示版权合规提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerImportScreen(
    packId: String,
    packName: String,
    onBack: () -> Unit,
    onStarted: () -> Unit = {},
    viewModel: StickerViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val selection = remember { mutableStateListOf<Uri>() }
    var autoTag by remember { mutableStateOf(true) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.clearMessage()
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri -> if (selection.size < MAX_SELECTION && !selection.contains(uri)) selection += uri }
    }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selection.size < MAX_SELECTION) selection += uri
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("导入表情") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "导入到「${packName.ifBlank { packId }}」",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "支持 PNG / JPG / WebP / GIF，单次最多 $MAX_SELECTION 个；也可以选一个 zip / .xystickers 压缩包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = packId.isNotBlank() && selection.size < MAX_SELECTION,
                        ) { Text("从相册选择") }
                        OutlinedButton(
                            onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                            enabled = packId.isNotBlank() && selection.size < MAX_SELECTION,
                        ) { Text("选择压缩包") }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.sticker_import_auto_tag),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = autoTag, onCheckedChange = { autoTag = it })
                    }
                }
                if (selection.isEmpty()) {
                    item {
                        Text(
                            "还没有选择文件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        Text(
                            "已选择 ${selection.size} 个文件",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(selection.toList()) { index, uri ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}. ${fileName(uri)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { selection.remove(uri) }) { Text("移除") }
                            }
                        }
                    }
                }
                if (packId.isBlank()) {
                    item {
                        Text(
                            "请先新建或选择一个表情包后再导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // 合规提示固定底部，不可省略（执行书 7.4.3 / 7.5）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.startImport(packId, selection.toList(), autoTag)
                        onStarted()
                    },
                    enabled = packId.isNotBlank() && selection.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("开始导入（${selection.size}）") }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.sticker_import_copyright),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.sticker_import_copyright_extra),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Divider(Modifier.padding(vertical = 6.dp))
                Text(
                    "导入在后台进行，进度会显示在通知栏；完成后可在管理页改名或补充标签。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 选择器返回的 URI 没有直接文件名，取最后一段做展示即可。 */
private fun fileName(uri: Uri): String =
    uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "已选文件"
