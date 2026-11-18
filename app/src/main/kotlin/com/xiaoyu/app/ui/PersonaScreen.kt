package com.xiaoyu.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R

private data class PersonaDraft(val id: Long?, val name: String, val content: String, val isStyle: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val identities by viewModel.identities.collectAsStateWithLifecycle()
    val styles by viewModel.styles.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf<PersonaDraft?>(null) }
    val defaultIdentityName = stringResource(R.string.persona_default_identity_name)
    val defaultStyleName = stringResource(R.string.persona_default_style_name)
    // M7-09：.xypersona 人格包导入
    val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importPersonaPack(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.persona_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.persona_action_back)) }
                },
                actions = {
                    TextButton(onClick = {
                        // 大多数人设就是一份 Markdown，别逼用户先打包成 zip
                        packPicker.launch(arrayOf("text/markdown", "text/plain", "application/zip", "*/*"))
                    }) { Text(stringResource(R.string.persona_import_pack)) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.persona_import_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                PersonaSection(
                    title = stringResource(R.string.persona_section_identity),
                    hint = stringResource(R.string.persona_identity_hint),
                    onAdd = { draft = PersonaDraft(null, defaultIdentityName, "", false) },
                ) {
                    identities.forEach { identity ->
                        PersonaRow(
                            name = identity.name,
                            preview = identity.contentMd,
                            onEdit = { draft = PersonaDraft(identity.id, identity.name, identity.contentMd, false) },
                            onDelete = { viewModel.deleteIdentity(identity.id) },
                            onShare = { viewModel.sharePersonaPack(identity.id) },
                            deletable = identities.size > 1,
                        )
                    }
                }
            }
            item {
                PersonaSection(
                    title = stringResource(R.string.persona_section_style),
                    hint = stringResource(R.string.persona_style_hint),
                    onAdd = { draft = PersonaDraft(null, defaultStyleName, "", true) },
                ) {
                    styles.forEach { style ->
                        PersonaRow(
                            name = style.name,
                            preview = style.contentMd,
                            onEdit = { draft = PersonaDraft(style.id, style.name, style.contentMd, true) },
                            onDelete = { viewModel.deleteStyle(style.id) },
                            deletable = styles.size > 1,
                        )
                    }
                }
            }
        }
    }

    draft?.let { current ->
        PersonaEditDialog(
            draft = current,
            onDismiss = { draft = null },
            onConfirm = { name, content ->
                if (current.isStyle) viewModel.saveStyle(current.id, name, content)
                else viewModel.saveIdentity(current.id, name, content)
                draft = null
            },
        )
    }
}

@Composable
private fun PersonaSection(
    title: String,
    hint: String,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text(stringResource(R.string.persona_action_add)) }
            }
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PersonaRow(
    name: String,
    preview: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deletable: Boolean,
    onShare: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            onShare?.let { share -> TextButton(onClick = share) { Text(stringResource(R.string.persona_action_share)) } }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.persona_action_edit)) }
            if (deletable) TextButton(onClick = onDelete) { Text(stringResource(R.string.persona_action_delete)) }
        }
        Text(
            preview.take(60) + if (preview.length > 60) "…" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun PersonaEditDialog(
    draft: PersonaDraft,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(draft.name) }
    var content by remember { mutableStateOf(draft.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.isStyle) stringResource(R.string.persona_dialog_title_style) else stringResource(R.string.persona_dialog_title_identity)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.persona_label_name)) }, singleLine = true)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.persona_label_description)) },
                    minLines = 8,
                    maxLines = 18,
                )
                // 导入的人设同样可改：存下来就以手机上的这份为准，不存在「只能改源文件」
                Text(
                    stringResource(R.string.persona_edit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, content) }, enabled = content.isNotBlank()) { Text(stringResource(R.string.persona_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.persona_action_cancel)) } },
    )
}
