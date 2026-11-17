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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.CrashLogger
import com.xiaoyu.app.data.db.DatabaseSelfCheck
import com.xiaoyu.app.data.db.RequestSnapshotEntity
import com.xiaoyu.app.perf.FrameStats
import com.xiaoyu.app.perf.MemoryProbe
import com.xiaoyu.app.perf.StartupTimer
import com.xiaoyu.app.ui.theme.SemanticSuccess
import com.xiaoyu.app.ui.theme.SemanticWarning
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val snapshotTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

private enum class DiagTab(val labelRes: Int) {
    PROMPT(R.string.diag_tab_prompt),
    RAW(R.string.diag_tab_raw),
    EVENT(R.string.diag_tab_event),
    ERROR(R.string.diag_tab_error),
    PERF(R.string.diag_tab_perf),
}

/** 诊断面板（v3 §7.14 四页签）：prompt 分段 / 原文 / 事件 / 错误。 */
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
    var currentTab by remember { mutableStateOf(DiagTab.PROMPT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diag_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.diag_back)) }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.exportDiagnostics { path ->
                            Toast.makeText(
                                context,
                                path?.let { context.getString(R.string.diag_export_success, it) } ?: context.getString(R.string.diag_export_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) { Text(stringResource(R.string.diag_export)) }
                    TextButton(onClick = { viewModel.clearDiagnostics() }) { Text(stringResource(R.string.diag_clear)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // v3 §7.14：四页签（prompt / raw / event / error）
            TabRow(
                selectedTabIndex = currentTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal])
                    )
                },
            ) {
                DiagTab.entries.forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

            when (currentTab) {
                DiagTab.PROMPT -> PromptTab(snapshots, expanded, viewModel, context)
                DiagTab.RAW -> RawTab(snapshots, expanded, viewModel, context)
                DiagTab.EVENT -> EventTab(snapshots, context)
                DiagTab.ERROR -> ErrorTab(dbNotice, crashFiles, openedCrash, snapshots,
                    onCrashClear = { CrashLogger.clear(context); crashFiles = CrashLogger.list(context); openedCrash = null },
                    onNoticeClear = { DatabaseSelfCheck.clearNotice(context); dbNotice = null },
                    onCrashToggle = { openedCrash = if (openedCrash == it) null else it },
                )
                DiagTab.PERF -> PerfTab(viewModel)
            }
        }
    }
}

// —— 以下是四个 Tab 的独立渲染 ——

/** v3 §7.14：prompt tab —— 每个 snapshot 完整展开。 */
@Composable
private fun PromptTab(
    snapshots: List<RequestSnapshotEntity>,
    expanded: MutableMap<Long, Boolean>,
    viewModel: AppViewModel,
    context: android.content.Context,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (snapshots.isEmpty()) {
            item { EmptyDiagHint() }
        } else {
            items(snapshots, key = { it.id }) { snapshot ->
                SnapshotCard(
                    snapshot = snapshot,
                    expanded = expanded[snapshot.id] == true,
                    onToggle = { expanded[snapshot.id] = expanded[snapshot.id] != true },
                    onCopyCurl = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("curl", viewModel.curlOf(snapshot)))
                        Toast.makeText(context, context.getString(R.string.diag_copied_curl), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

/** v3 §7.14：raw tab —— 同 PromptTab，按内容分组入口不同。 */
@Composable
private fun RawTab(
    snapshots: List<RequestSnapshotEntity>,
    expanded: MutableMap<Long, Boolean>,
    viewModel: AppViewModel,
    context: android.content.Context,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (snapshots.isEmpty()) {
            item { EmptyDiagHint() }
        } else {
            items(snapshots, key = { it.id }) { snapshot ->
                SnapshotCard(
                    snapshot = snapshot,
                    expanded = expanded[snapshot.id] == true,
                    onToggle = { expanded[snapshot.id] = expanded[snapshot.id] != true },
                    onCopyCurl = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("curl", viewModel.curlOf(snapshot)))
                        Toast.makeText(context, context.getString(R.string.diag_copied_curl), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

/** v3 §7.14：event tab —— 事件流 + token 用量，折叠紧凑显示。 */
@Composable
private fun EventTab(
    snapshots: List<RequestSnapshotEntity>,
    context: android.content.Context,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (snapshots.isEmpty()) {
            item { EmptyDiagHint() }
        } else {
            items(snapshots, key = { it.id }) { snapshot ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                snapshotTimeFormat.format(Date(snapshot.createdAt)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${snapshot.provider}/${snapshot.model}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(
                                R.string.diag_token_usage,
                                snapshot.promptTokens, snapshot.completionTokens, snapshot.durationMs,
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (snapshot.eventsText.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                snapshot.eventsText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        snapshot.error?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.diag_error_detail, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** v3 §7.14：error tab —— dbNotice + crashLogs + 带 error 的 snapshots。 */
@Composable
private fun ErrorTab(
    dbNotice: String?,
    crashFiles: List<File>,
    openedCrash: File?,
    snapshots: List<RequestSnapshotEntity>,
    onCrashClear: () -> Unit,
    onNoticeClear: () -> Unit,
    onCrashToggle: (File) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // DB 自检 notice
        dbNotice?.let { notice ->
            item(key = "db-notice") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.diag_db_notice_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onNoticeClear) { Text(stringResource(R.string.diag_got_it)) }
                    }
                }
            }
        }

        // Crash logs
        item(key = "crash-header") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.diag_crash_logs), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.diag_crash_recent, crashFiles.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onCrashClear, enabled = crashFiles.isNotEmpty()) { Text(stringResource(R.string.diag_clear)) }
            }
        }
        if (crashFiles.isEmpty()) {
            item(key = "crash-empty") {
                Text(stringResource(R.string.diag_crash_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(crashFiles, key = { "crash-" + it.name }) { file ->
                val opened = openedCrash == file
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onCrashToggle(file) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(file.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        Text(snapshotTimeFormat.format(Date(file.lastModified())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (opened) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                CrashLogger.read(androidx.compose.ui.platform.LocalContext.current, file).orEmpty().take(4000),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 带 error 的 snapshots
        val errorSnaps = snapshots.filter { it.error != null }
        if (errorSnaps.isNotEmpty()) {
            item(key = "error-snap-header") {
                Text(stringResource(R.string.diag_error), style = MaterialTheme.typography.titleSmall)
            }
            items(errorSnaps, key = { "error-snap-" + it.id }) { snap ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            snapshotTimeFormat.format(Date(snap.createdAt)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${snap.provider}/${snap.model}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.diag_error_detail, snap.error.orEmpty()),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDiagHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(stringResource(R.string.diag_snapshots_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.diag_snapshots_empty_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (snapshot.error == null) snapshot.finishReason.orEmpty() else stringResource(R.string.diag_error),
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
                stringResource(
                    R.string.diag_token_usage,
                    snapshot.promptTokens,
                    snapshot.completionTokens,
                    snapshot.durationMs,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(10.dp))

                Text(stringResource(R.string.diag_params), style = MaterialTheme.typography.labelLarge)
                Text(
                    pretty(snapshot.paramsJson),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                snapshot.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.diag_error_detail, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (snapshot.eventsText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.diag_events, snapshot.eventsText), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.diag_prompt_sections), style = MaterialTheme.typography.labelLarge)
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
                Text(stringResource(R.string.diag_raw_response), style = MaterialTheme.typography.labelLarge)
                Text(
                    snapshot.responseRaw.ifBlank { stringResource(R.string.diag_empty_placeholder) },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onCopyCurl) { Text(stringResource(R.string.diag_copy_curl)) }
                    TextButton(onClick = onToggle) { Text(stringResource(R.string.diag_collapse)) }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.diag_expand_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

// ---------------- 性能页签（执行书 v3 §1.5 / C-02、C-03、C-05） ----------------

/** 冷启动目标：1.5s。 */
private const val STARTUP_TARGET_MS = 1500L

/** 60fps 单帧预算：16.7ms。 */
private const val FRAME_TARGET_MS = 16.7

@Composable
private fun PerfTab(viewModel: AppViewModel) {
    val startup = remember { StartupTimer.last() }
    val frames = remember { FrameStats.snapshot() }
    val memory = remember { MemoryProbe.sample() }
    var exported by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }

    val startupOk = startup != null && startup.coldStartMs < STARTUP_TARGET_MS
    val frameOk = frames != null && frames.p95Ms <= FRAME_TARGET_MS
    val memoryOk = memory.available && memory.pssKb < memory.limitKb

    val pass = stringResource(R.string.perf_pass)
    val fail = stringResource(R.string.perf_fail)
    val unknown = stringResource(R.string.perf_unknown)

    val labelStartup = stringResource(R.string.perf_startup)
    val labelFrame = stringResource(R.string.perf_frame)
    val labelMemory = stringResource(R.string.perf_memory)
    val targetStartup = stringResource(R.string.perf_target_startup)
    val targetFrame = stringResource(R.string.perf_target_frame)
    val targetMemory = stringResource(R.string.perf_target_memory)

    val startupValue = startup?.let { stringResource(R.string.perf_ms, it.coldStartMs) }
        ?: stringResource(R.string.perf_startup_none)
    val frameValue = frames?.let {
        stringResource(
            R.string.perf_frame_value,
            oneDecimal(it.p50Ms),
            oneDecimal(it.p95Ms),
            it.samples,
        )
    } ?: stringResource(R.string.perf_frame_none)
    val memoryValue = if (memory.available) {
        stringResource(
            R.string.perf_memory_value,
            oneDecimal(memory.pssMb),
            oneDecimal(memory.javaHeapKb / 1024.0),
            oneDecimal(memory.nativeAllocatedKb / 1024.0),
        )
    } else {
        stringResource(R.string.perf_memory_none)
    }

    val statusStartup = if (startup == null) unknown else if (startupOk) pass else fail
    val statusFrame = if (frames == null) unknown else if (frameOk) pass else fail
    val statusMemory = if (!memory.available) unknown else if (memoryOk) pass else fail

    // 报告是纯文本文件，先把所有本地化片段取出来，避免在 onClick 里调用 stringResource。
    val report = listOf(
        stringResource(R.string.perf_report_title),
        stringResource(
            R.string.perf_report_time,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
        ),
        stringResource(
            R.string.perf_report_device,
            android.os.Build.MODEL.orEmpty(),
            android.os.Build.VERSION.SDK_INT,
        ),
        stringResource(R.string.perf_report_line, labelStartup, startupValue, targetStartup, statusStartup),
        stringResource(R.string.perf_report_line, labelFrame, frameValue, targetFrame, statusFrame),
        stringResource(R.string.perf_report_line, labelMemory, memoryValue, targetMemory, statusMemory),
        stringResource(R.string.perf_honest_note),
    ).joinToString("\n")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.perf_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PerfMetric(labelStartup, startupValue, targetStartup, statusStartup, startupOk)
        PerfMetric(labelFrame, frameValue, targetFrame, statusFrame, frameOk)
        PerfMetric(labelMemory, memoryValue, targetMemory, statusMemory, memoryOk)

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                exported = null
                exportFailed = false
                val fileName = "perf-" +
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
                viewModel.exportPerfReport(fileName, report) { path ->
                    if (path != null) {
                        exported = path
                    } else {
                        exportFailed = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.perf_export))
        }
        exported?.let {
            Text(
                text = stringResource(R.string.perf_export_success, it),
                style = MaterialTheme.typography.bodySmall,
                color = SemanticSuccess,
            )
        }
        if (exportFailed) {
            Text(
                text = stringResource(R.string.perf_export_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.perf_honest_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PerfMetric(
    label: String,
    value: String,
    target: String,
    status: String,
    ok: Boolean?,
) {
    val statusColor = when (ok) {
        true -> SemanticSuccess
        false -> SemanticWarning
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = statusColor,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = target,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
