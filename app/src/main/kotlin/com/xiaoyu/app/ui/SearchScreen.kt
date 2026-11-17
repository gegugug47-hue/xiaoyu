package com.xiaoyu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.repo.ROLE_ASSISTANT
import com.xiaoyu.app.data.repo.ROLE_USER
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val searchTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

/**
 * 翻旧账搜索页（PRD US-020）：
 * 对全部可见消息做关键词搜索，选中某条后作为下一轮的临时上下文，不进记忆系统。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onUseContext: () -> Unit,
) {
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    var keyword by remember { mutableStateOf("") }
    // v3 B-07：高亮以「实际提交搜索」的关键词为准，避免边打字边改变已有结果的高亮
    var appliedKeywords by remember { mutableStateOf<List<String>>(emptyList()) }

    // 进入页面时清空上次搜索结果
    LaunchedEffect(Unit) { viewModel.searchMessages("") }

    val runSearch: () -> Unit = {
        appliedKeywords = splitKeywords(keyword)
        viewModel.searchMessages(keyword)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.search_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = runSearch,
                            enabled = keyword.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_action))
                        }
                    },
                )
            }

            when {
                searching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                results.isEmpty() && keyword.isNotBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_no_result, keyword),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_empty_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp, vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.id }) { message ->
                            SearchResultItem(
                                message = message,
                                keywords = appliedKeywords,
                                onClick = {
                                    viewModel.useMessageAsContext(message)
                                    onUseContext()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(message: MessageEntity, keywords: List<String>, onClick: () -> Unit) {
    val isUser = message.role == ROLE_USER
    val roleLabel = when (message.role) {
        ROLE_USER -> stringResource(R.string.search_role_me)
        ROLE_ASSISTANT -> stringResource(R.string.search_role_her)
        else -> stringResource(R.string.search_role_system)
    }
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    // v3 B-07：命中关键词用 primaryContainer 背景高亮（大小写不敏感、支持多关键词）
    val highlighted = remember(message.content, keywords, highlightColor) {
        highlightKeywords(message.content, keywords, highlightColor)
    }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    roleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    searchTimeFormat.format(Date(message.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                highlighted,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 搜索关键词拆分：空格 / 中英文逗号 / 顿号都算分隔符，去重后逐个高亮。 */
private fun splitKeywords(raw: String): List<String> =
    raw.split(Regex("[\\s,，、]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * v3 B-07：把命中的关键词包上 [SpanStyle] 高亮。
 * 用 `indexOf(ignoreCase = true)` 而不是先转小写再取下标，避免某些语言大小写转换改变长度导致错位。
 */
private fun highlightKeywords(
    content: String,
    keywords: List<String>,
    background: Color,
): AnnotatedString {
    if (content.isEmpty() || keywords.isEmpty()) return AnnotatedString(content)
    val builder = AnnotatedString.Builder(content)
    val style = SpanStyle(background = background)
    keywords.forEach { keyword ->
        var from = 0
        while (from <= content.length - keyword.length) {
            val index = content.indexOf(keyword, from, ignoreCase = true)
            if (index < 0) break
            builder.addStyle(style, index, index + keyword.length)
            from = index + keyword.length
        }
    }
    return builder.toAnnotatedString()
}
