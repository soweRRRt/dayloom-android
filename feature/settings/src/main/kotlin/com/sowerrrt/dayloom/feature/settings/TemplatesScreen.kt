package com.sowerrrt.dayloom.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.designsystem.dayloomDialogMotion
import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.TemplateBundle
import com.sowerrrt.dayloom.core.model.TemplateBundleEntry
import com.sowerrrt.dayloom.core.model.TemplateEntryType
import java.time.LocalDate

@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TemplateBundle?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf<TemplateBundle?>(null) }

    Box(Modifier.fillMaxSize().testTag("templates_screen")) {
        Column(Modifier.fillMaxSize()) {
            DayloomTopBar(
                title = stringResource(R.string.templates_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.templates_back),
                        )
                    }
                },
            )
            LazyColumn(
                contentPadding = PaddingValues(DayloomSpacing.md, DayloomSpacing.xs, DayloomSpacing.md, 96.dp),
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            ) {
                item {
                    Text(
                        stringResource(R.string.templates_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.bundles.isEmpty() && !state.isLoading) {
                    item {
                        DayloomCard(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.templates_empty))
                        }
                    }
                }
                items(state.bundles, key = { it.id.value }) { bundle ->
                    DayloomCard(
                        modifier = Modifier.fillMaxWidth().animateItem().testTag("template_bundle_${bundle.name}"),
                        onClick = { applying = bundle },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(DayloomSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(bundle.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.templates_items_count, bundle.entries.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                editing = bundle
                                showEditor = true
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.templates_edit))
                            }
                            IconButton(onClick = { viewModel.duplicateBundle(bundle.id) }) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.templates_duplicate),
                                )
                            }
                            IconButton(onClick = { viewModel.deleteBundle(bundle.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.templates_delete),
                                )
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editing = null
                showEditor = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(DayloomSpacing.md).testTag("create_template_bundle"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.templates_create))
        }
    }

    if (showEditor) {
        TemplateEditorDialog(
            bundle = editing,
            available = state.availableEntries,
            onDismiss = { showEditor = false },
            onSave = { name, entries ->
                viewModel.saveBundle(editing?.id, name, entries)
                showEditor = false
            },
        )
    }
    applying?.let { bundle ->
        ApplyTemplateDialog(
            bundle = bundle,
            lists = state.lists,
            onDismiss = { applying = null },
            onApply = { selected, targetListId ->
                viewModel.applyBundle(bundle, selected, targetListId, LocalDate.now().toEpochDay())
                applying = null
            },
        )
    }
    state.appliedBundleName?.let { name ->
        AlertDialog(
            onDismissRequest = viewModel::consumeAppliedFeedback,
            title = { Text(stringResource(R.string.templates_applied_title)) },
            text = { Text(stringResource(R.string.templates_applied_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::consumeAppliedFeedback,
                ) { Text(stringResource(R.string.templates_done)) }
            },
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    bundle: TemplateBundle?,
    available: List<TemplateBundleEntry>,
    onDismiss: () -> Unit,
    onSave: (String, List<TemplateBundleEntry>) -> Unit,
) {
    var name by remember(bundle?.id) { mutableStateOf(bundle?.name.orEmpty()) }
    val choices = remember(bundle, available) { (bundle?.entries.orEmpty() + available).distinctBy(::entryKey) }
    var selected by remember(bundle?.id, choices) {
        mutableStateOf(
            bundle
                ?.entries
                .orEmpty()
                .map(::entryKey)
                .toSet(),
        )
    }
    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (bundle ==
                        null
                    ) {
                        R.string.templates_create_title
                    } else {
                        R.string.templates_edit_title
                    },
                ),
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.templates_name)) },
                        singleLine = true,
                    )
                }
                items(choices, key = ::entryKey) { entry ->
                    val key = entryKey(entry)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in selected,
                            onCheckedChange = { checked -> selected = if (checked) selected + key else selected - key },
                        )
                        Column {
                            Text(entry.title)
                            Text(entryTypeLabel(entry.type), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), choices.filter { entryKey(it) in selected }) },
                enabled = name.isNotBlank() && selected.isNotEmpty(),
            ) { Text(stringResource(R.string.templates_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.templates_cancel)) } },
    )
}

@Composable
private fun ApplyTemplateDialog(
    bundle: TemplateBundle,
    lists: List<DayList>,
    onDismiss: () -> Unit,
    onApply: (Set<EntityId>, EntityId?) -> Unit,
) {
    var selected by remember(bundle.id) { mutableStateOf(bundle.entries.map(TemplateBundleEntry::id).toSet()) }
    var targetListId by remember(bundle.id, lists) { mutableStateOf(lists.firstOrNull()?.id) }
    val needsList = bundle.entries.any { it.id in selected && it.type == TemplateEntryType.LIST_ITEM }
    AlertDialog(
        modifier = Modifier.dayloomDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(bundle.name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                item { Text(stringResource(R.string.templates_choose_items)) }
                items(bundle.entries, key = { it.id.value }) { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = entry.id in selected,
                            onCheckedChange = { checked ->
                                selected =
                                    if (checked) selected + entry.id else selected - entry.id
                            },
                        )
                        Column {
                            Text(entry.title)
                            Text(entryTypeLabel(entry.type), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (needsList) {
                    item {
                        Text(
                            stringResource(R.string.templates_target_list),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    items(lists, key = { it.id.value }) { list ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = targetListId == list.id, onClick = { targetListId = list.id })
                            Text(list.title)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(selected, targetListId) },
                enabled = selected.isNotEmpty() && (!needsList || targetListId != null),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.templates_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.templates_cancel)) } },
    )
}

private fun entryKey(entry: TemplateBundleEntry): String = "${entry.type}:${entry.title.lowercase()}"

@Composable
private fun entryTypeLabel(type: TemplateEntryType): String =
    stringResource(
        when (type) {
            TemplateEntryType.HABIT -> R.string.templates_type_habit
            TemplateEntryType.PLAN -> R.string.templates_type_plan
            TemplateEntryType.LIST_ITEM -> R.string.templates_type_list_item
        },
    )
