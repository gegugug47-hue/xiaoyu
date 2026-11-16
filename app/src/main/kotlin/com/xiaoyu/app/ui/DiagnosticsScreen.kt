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
                                stringResource(R.string.diag_db_notice_title),
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
                            }) { Text(stringResource(R.string.diag_got_it)) }
                        }
                    }
                }
            }

            item(key = "crash-header") {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.diag_crash_logs), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.diag_crash_recent, crashFiles.size),
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
                    ) { Text(stringResource(R.string.diag_clear)) }
                }
            }
            if (crashFiles.isEmpty()) {
                item(key = "crash-empty") {
                    Text(
                        stringResource(R.string.diag_crash_empty),
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
                        Text(stringResource(R.string.diag_snapshots_empty_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.diag_snapshots_empty_desc),
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
                            Toast.makeText(context, context.getString(R.string.diag_copied_curl), Toast.LENGTH_SHORT).show()
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
