package com.xiaoyu.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.CrashLogger
import com.xiaoyu.app.data.db.DatabaseSelfCheck
import com.xiaoyu.app.data.db.RequestSnapshotEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val snapshotTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

/** 诊断面板（执行书 6.13）：Prompt 分段、原文、事件、token 与复现命令。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: AppViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val snapshots by viewModel.diagnostics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    var dbNotice by remember { mutableStateOf(DatabaseSelfCheck.pendingNotice(context)) }
    var crashFiles by remember { mutableStateOf(CrashLogger.list(context)) }
    var openedCrash by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("诊断") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.exportDiagnostics { path ->
                            Toast.makeText(
                                context,
                                path?.let { "已导出：$it" } ?: "导出失败",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) { Text("导出诊断包") }
                    TextButton(onClick = { viewModel.clearDiagnostics() }) { Text("清空") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            dbNotice?.let { notice ->
                item(key = "db-notice") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "数据库自检提示",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = {
                                DatabaseSelfCheck.clearNotice(context)
                                dbNotice = null
                            }) { Text("我知道了") }
                        }
                    }
                }
            }

            item(key = "crash-header") {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("崩溃日志", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "最近 ${crashFiles.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            CrashLogger.clear(context)
                            crashFiles = CrashLogger.list(context)
                            openedCrash = null
                        },
                        enabled = crashFiles.isNotEmpty(),
                    ) { Text("清空") }
                }
            }
            if (crashFiles.isEmpty()) {
                item(key = "crash-empty") {
                    Text(
                        "没有崩溃记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(crashFiles, key = { "crash-" + it.name }) { file ->
                    val opened = openedCrash == file
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { openedCrash = if (opened) null else file },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                snapshotTimeFormat.format(Date(file.lastModified())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (opened) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    CrashLogger.read(context, file).orEmpty().take(4000),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (snapshots.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("还没有请求记录", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "和她聊几句后，这里会显示最近 50 次模型请求的 Prompt 分段、原文与后处理事件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(snapshots, key = { it.id }) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        expanded = expanded[snapshot.id] == true,
                        onToggle = { expanded[snapshot.id] = expanded[snapshot.id] != true },
                        onCopyCurl = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("curl", viewModel.curlOf(snapshot))
                            )
                            Toast.makeText(context, "已复制 curl，Key 用 \$XY_API_KEY 占位", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    snapshot: RequestSnapshotEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopyCurl: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    snapshotTimeFormat.format(Date(snapshot.createdAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (snapshot.error == null) snapshot.finishReason.orEmpty() else "错误",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapshot.error == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${snapshot.provider} / ${snapshot.model}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                "prompt ≈ ${snapshot.promptTokens} · 输出 ≈ ${snapshot.completionTokens} · ${snapshot.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(10.dp))

                Text("参数", style = MaterialTheme.typography.labelLarge)
                Text(
                    pretty(snapshot.paramsJson),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                snapshot.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("错误：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (snapshot.eventsText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("事件：${snapshot.eventsText}", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))
                Text("Prompt 分段", style = MaterialTheme.typography.labelLarge)
                sections(snapshot.sectionsJson).forEach { section ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "L${section.optInt("index")} ${section.optString("label")} · ~${section.optInt("tokens")} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        section.optString("content"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("模型原文", style = MaterialTheme.typography.labelLarge)
                Text(
                    snapshot.responseRaw.ifBlank { "（空）" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onCopyCurl) { Text("复制 curl") }
                    TextButton(onClick = onToggle) { Text("收起") }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("点击展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun sections(json: String): List<JSONObject> = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).map { array.getJSONObject(it) }
}.getOrDefault(emptyList())

private fun pretty(json: String): String = runCatching {
    JSONObject(json).toString(2)
}.getOrDefault(json)
