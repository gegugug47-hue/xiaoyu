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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.db.FactCardEntity
import com.xiaoyu.app.data.db.MemoryEntity
import com.xiaoyu.app.ui.components.YuEmptyView
import com.xiaoyu.app.ui.illustrations.YuIllustrationKind
import com.xiaoyu.soul.memory.CARD_CATEGORIES
import com.xiaoyu.soul.memory.MemoryType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val shortTerm by viewModel.shortMemories.collectAsStateWithLifecycle()
    val cards by viewModel.factCards.collectAsStateWithLifecycle()
    val bin by viewModel.binMemories.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var editingCard by remember { mutableStateOf<FactCardEntity?>(null) }
    var creatingCard by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.memory_back))
                    }
                },
                actions = {
                    if (tab == 1) {
                        TextButton(onClick = { creatingCard = true }) { Text(stringResource(R.string.memory_add_card)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text(stringResource(R.string.memory_tab_short_term, shortTerm.size)) })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(stringResource(R.string.memory_tab_long_cards, cards.size)) })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text(stringResource(R.string.memory_tab_bin, bin.size)) })
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (shortTerm.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.memory_empty_short_term)) }
                    }
                    items(shortTerm, key = { it.id }) { memory ->
                        MemoryCard(
                            title = MemoryType.from(memory.type).label,
                            content = memory.content,
                            tags = memory.tags,
                            meta = stringResource(R.string.memory_meta_importance_strength, memory.importance, memory.strength),
                            onEdit = { editingMemory = memory },
                            onDelete = { viewModel.deleteMemory(memory.id) },
                        )
                    }
                }
                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (cards.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.memory_empty_long_cards)) }
                    }
                    items(cards, key = { it.id }) { card ->
                        MemoryCard(
                            title = card.category,
                            content = card.content,
                            tags = card.tags,
                            meta = if (card.source == "user_command") stringResource(R.string.memory_meta_from_user) else stringResource(R.string.memory_meta_learned),
                            onEdit = { editingCard = card },
                            onDelete = { viewModel.deleteCard(card.id) },
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (bin.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.memory_empty_bin)) }
                    }
                    items(bin, key = { it.id }) { memory ->
                        MemoryCard(
                            title = MemoryType.from(memory.type).label,
                            content = memory.content,
                            tags = memory.tags,
                            meta = stringResource(R.string.memory_meta_in_bin),
                            onEdit = null,
                            onDelete = { viewModel.deleteMemory(memory.id) },
                            extra = {
                                TextButton(onClick = { viewModel.restoreMemory(memory.id) }) { Text(stringResource(R.string.memory_restore)) }
                            },
                        )
                    }
                }
            }
        }
    }

    editingMemory?.let { memory ->
        MemoryEditDialog(
            memory = memory,
            onDismiss = { editingMemory = null },
            onConfirm = { content, type, tags, importance ->
                viewModel.editMemory(memory.id, content, type, tags, importance)
                editingMemory = null
            },
        )
    }

    if (creatingCard || editingCard != null) {
        CardEditDialog(
            card = editingCard,
            onDismiss = { creatingCard = false; editingCard = null },
            onConfirm = { content, category, tags ->
                val target = editingCard
                if (target == null) {
                    viewModel.createCard(content, category, tags.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                } else {
                    viewModel.editCard(target.id, content, category, tags)
                }
                creatingCard = false
                editingCard = null
            },
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    YuEmptyView(kind = YuIllustrationKind.MEMORY, message = text)
}

@Composable
private fun MemoryCard(
    title: String,
    content: String,
    tags: String,
    meta: String,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (tags.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(tags, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                extra?.invoke()
                if (onEdit != null) TextButton(onClick = onEdit) { Text(stringResource(R.string.memory_edit)) }
                TextButton(onClick = onDelete) {
                    Text(if (onEdit == null) stringResource(R.string.memory_delete_permanently) else stringResource(R.string.memory_delete))
                }
            }
        }
    }
}

@Composable
private fun MemoryEditDialog(
    memory: MemoryEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, MemoryType, String, Int) -> Unit,
) {
    var content by remember { mutableStateOf(memory.content) }
    var tags by remember { mutableStateOf(memory.tags) }
    var type by remember { mutableStateOf(MemoryType.from(memory.type)) }
    var importance by remember { mutableStateOf(memory.importance) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_dialog_edit_memory)) },
        text = {
            Column {
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(stringResource(R.string.memory_label_content)) }, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryType.entries.forEach { item ->
                        FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item.label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text(stringResource(R.string.memory_label_tags)) })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.memory_importance_value, importance), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = importance.toFloat(),
                    onValueChange = { importance = it.roundToInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(content, type, tags, importance) }) { Text(stringResource(R.string.memory_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) } },
    )
}

@Composable
private fun CardEditDialog(
    card: FactCardEntity?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var content by remember { mutableStateOf(card?.content.orEmpty()) }
    var tags by remember { mutableStateOf(card?.tags.orEmpty()) }
    var category by remember { mutableStateOf(card?.category ?: CARD_CATEGORIES.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (card == null) stringResource(R.string.memory_dialog_create_card) else stringResource(R.string.memory_dialog_edit_card)) },
        text = {
            Column {
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(stringResource(R.string.memory_label_content)) }, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CARD_CATEGORIES.take(3).forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CARD_CATEGORIES.drop(3).forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text(stringResource(R.string.memory_label_tags)) })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(content, category, tags) }) { Text(stringResource(R.string.memory_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) } },
    )
}
