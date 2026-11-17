package com.xiaoyu.app.ui

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.db.DiaryEntity
import com.xiaoyu.app.ui.components.YuEmptyView
import com.xiaoyu.app.ui.illustrations.YuIllustrationKind
import com.xiaoyu.app.ui.theme.LocalYuType
import com.xiaoyu.soul.diary.DiaryPlanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val diaries by viewModel.diaries.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<DiaryEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.diary_back)) }
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (diaries.isEmpty()) {
                item {
                    YuEmptyView(
                        kind = YuIllustrationKind.DIARY,
                        message = stringResource(R.string.diary_empty),
                    )
                }
            }
            items(diaries, key = { it.id }) { diary ->
                val locked = diary.isSecret && !diary.unlocked
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // 列表项合并为一组朗读：日期 + 心情 + 摘要 / 上锁状态
                        .semantics(mergeDescendants = true) {},
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${diary.eventDate}  ${DiaryPlanner.moodIcon(diary.moodSnapshot)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            if (locked) {
                                Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.diary_locked_content_description), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (locked) {
                            Text(
                                stringResource(R.string.diary_locked),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { viewModel.unlockDiary(diary.id) }) { Text(stringResource(R.string.diary_try_unlock)) }
                        } else if (opened?.id == diary.id) {
                            Text(diary.contentMd, style = LocalYuType.current.diary, color = MaterialTheme.colorScheme.onSurface)
                            Row {
                                TextButton(onClick = { viewModel.exportDiaryMarkdown(diary.id) }) { Text(stringResource(R.string.diary_export_markdown)) }
                                TextButton(onClick = { opened = null }) { Text(stringResource(R.string.diary_close)) }
                            }
                        } else {
                            Text(
                                diary.contentMd.take(48) + if (diary.contentMd.length > 48) "…" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = {
                                opened = diary
                                viewModel.openDiary(diary)
                            }) { Text(stringResource(R.string.diary_open)) }
                        }
                    }
                }
            }
        }
    }
}
