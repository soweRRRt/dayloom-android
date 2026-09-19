package com.sowerrrt.dayloom.feature.lists

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.DayListItem
import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.ui.ErrorState
import com.sowerrrt.dayloom.core.ui.LoadingState

@Composable
fun ListsScreen(viewModel: ListsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showListEditor by rememberSaveable { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<DayList?>(null) }
    var showItemEditor by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<DayListItem?>(null) }
    var pendingListDelete by remember { mutableStateOf<DayList?>(null) }
    var pendingItemDelete by remember { mutableStateOf<DayListItem?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val selectedList = state.selectedList
    Box(Modifier.fillMaxSize().testTag("lists_screen")) {
        Column(Modifier.fillMaxSize()) {
            if (selectedList == null) {
                DayloomTopBar(stringResource(R.string.lists_title))
            } else {
                DayloomTopBar(
                    title = selectedList.title,
                    navigationIcon = {
                        IconButton(onClick = viewModel::closeList, modifier = Modifier.testTag("close_list")) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.lists_back))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                editingList = selectedList
                                showListEditor = true
                            },
                            modifier = Modifier.testTag("edit_list"),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.lists_edit_list))
                        }
                        IconButton(
                            onClick = { pendingListDelete = selectedList },
                            modifier = Modifier.testTag("delete_list"),
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.lists_delete_list),
                            )
                        }
                    },
                )
            }

            when {
                state.isLoading -> LoadingState(Modifier.weight(1f))
                state.hasError ->
                    ErrorState(
                        title = stringResource(R.string.lists_error_title),
                        message = stringResource(R.string.lists_error_description),
                        retryLabel = stringResource(R.string.lists_retry),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.weight(1f),
                    )
                selectedList == null ->
                    ListsOverview(
                        state = state,
                        onOpenList = { viewModel.openList(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                else ->
                    ListDetails(
                        list = selectedList,
                        onToggle = { viewModel.toggleItem(selectedList.id, it.id) },
                        onEdit = {
                            editingItem = it
                            showItemEditor = true
                        },
                        onDelete = { pendingItemDelete = it },
                        onMove = { item, offset -> viewModel.moveItem(selectedList.id, item.id, offset) },
                        modifier = Modifier.weight(1f),
                    )
            }
        }

        if (!state.isLoading && !state.hasError) {
            FloatingActionButton(
                onClick = {
                    if (selectedList == null) {
                        editingList = null
                        showListEditor = true
                    } else {
                        editingItem = null
                        showItemEditor = true
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = DayloomSpacing.md, bottom = DayloomSpacing.lg)
                        .testTag(if (selectedList == null) "create_list" else "create_list_item"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.lists_add))
            }
        }
    }

    if (showListEditor) {
        ListEditorDialog(
            list = editingList,
            customKinds =
                state.lists
                    .map(DayList::customKind)
                    .filter(String::isNotBlank)
                    .toSet(),
            onDismiss = { showListEditor = false },
            onSave = { title, kind, customKind ->
                val list = editingList
                if (list == null) {
                    viewModel.createList(title, kind, customKind)
                } else {
                    viewModel.updateList(list.id, title, kind, customKind)
                }
                showListEditor = false
            },
        )
    }

    if (showItemEditor && selectedList != null) {
        ItemEditorDialog(
            item = editingItem,
            presets = state.itemPresets,
            onSavePreset = viewModel::saveItemPreset,
            onRemovePreset = viewModel::removeItemPreset,
            onDismiss = { showItemEditor = false },
            onSave = { title, quantity, note ->
                val item = editingItem
                if (item == null) {
                    viewModel.addItem(selectedList.id, title, quantity, note)
                } else {
                    viewModel.updateItem(selectedList.id, item.id, title, quantity, note)
                }
                showItemEditor = false
            },
        )
    }

    pendingListDelete?.let { list ->
        DeleteDialog(
            title = stringResource(R.string.lists_delete_list_title),
            description = stringResource(R.string.lists_delete_list_description, list.title),
            onDismiss = { pendingListDelete = null },
            onConfirm = {
                viewModel.deleteList(list.id)
                pendingListDelete = null
            },
        )
    }

    if (selectedList != null) {
        pendingItemDelete?.let { item ->
            DeleteDialog(
                title = stringResource(R.string.lists_delete_item_title),
                description = stringResource(R.string.lists_delete_item_description, item.title),
                onDismiss = { pendingItemDelete = null },
                onConfirm = {
                    viewModel.deleteItem(selectedList.id, item.id)
                    pendingItemDelete = null
                },
            )
        }
    }
}

@Composable
private fun ListsOverview(
    state: ListsUiState,
    onOpenList: (DayList) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("lists_overview"),
        contentPadding = PaddingValues(start = DayloomSpacing.md, end = DayloomSpacing.md, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
    ) {
        item {
            Text(
                stringResource(R.string.lists_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.lists.isEmpty()) {
            item { EmptyListsCard() }
        } else {
            item {
                Text(
                    stringResource(
                        R.string.lists_overview_summary,
                        state.lists.size,
                        state.completedItems,
                        state.totalItems,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            itemsIndexed(state.lists, key = { _, list -> list.id.value }) { _, list ->
                ListOverviewCard(list = list, onClick = { onOpenList(list) })
            }
        }
    }
}

@Composable
private fun EmptyListsCard() {
    DayloomCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(42.dp))
            Text(stringResource(R.string.lists_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.lists_empty_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListOverviewCard(
    list: DayList,
    onClick: () -> Unit,
) {
    val completed = list.items.count(DayListItem::completed)
    val total = list.items.size
    DayloomCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("list_${list.title}"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            ) {
                Icon(kindIcon(list.kind), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Column(Modifier.weight(1f)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        list.customKind.ifBlank { kindLabel(list.kind) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(stringResource(R.string.lists_progress, completed, total))
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else completed.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ListDetails(
    list: DayList,
    onToggle: (DayListItem) -> Unit,
    onEdit: (DayListItem) -> Unit,
    onDelete: (DayListItem) -> Unit,
    onMove: (DayListItem, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = list.items.count(DayListItem::completed)
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("list_details"),
        contentPadding = PaddingValues(start = DayloomSpacing.md, end = DayloomSpacing.md, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
    ) {
        item {
            DayloomCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(kindIcon(list.kind), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.size(DayloomSpacing.sm))
                        Text(
                            list.customKind.ifBlank { kindLabel(list.kind) },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.lists_progress, completed, list.items.size))
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (list.items.isEmpty()) 0f else completed.toFloat() / list.items.size
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (list.items.isEmpty()) {
            item {
                DayloomCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.lists_no_items),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            itemsIndexed(list.items, key = { _, item -> item.id.value }) { index, item ->
                ListItemRow(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < list.items.lastIndex,
                    onToggle = { onToggle(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    onMoveUp = { onMove(item, -1) },
                    onMoveDown = { onMove(item, 1) },
                )
            }
        }
    }
}

@Composable
private fun ListItemRow(
    item: DayListItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    DayloomCard(Modifier.fillMaxWidth().testTag("list_item_${item.title}")) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle, modifier = Modifier.testTag("list_item_toggle_${item.title}")) {
                    Icon(
                        if (item.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.lists_toggle_item, item.title),
                        tint =
                            if (item.completed) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                    )
                    if (item.quantity.isNotEmpty()) {
                        Text(
                            stringResource(R.string.lists_quantity_value, item.quantity),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (item.note.isNotEmpty()) {
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.lists_edit_item))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.lists_delete_item))
                }
            }
            HorizontalDivider()
            Row(modifier = Modifier.align(Alignment.End)) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.lists_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.lists_move_down))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListEditorDialog(
    list: DayList?,
    customKinds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String, ListKind, String) -> Unit,
) {
    var title by remember(list?.id) { mutableStateOf(list?.title.orEmpty()) }
    var kind by remember(list?.id) { mutableStateOf(list?.kind ?: ListKind.GENERAL) }
    var customKind by remember(list?.id) { mutableStateOf(list?.customKind.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (list == null) R.string.lists_create_title else R.string.lists_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_LIST_TITLE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("list_name_input"),
                    label = { Text(stringResource(R.string.lists_name_label)) },
                    supportingText = { Text("${title.length}/$MAX_LIST_TITLE_LENGTH") },
                    singleLine = true,
                )
                Text(stringResource(R.string.lists_kind_label), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm)) {
                    ListKind.entries.forEach { candidate ->
                        AssistChip(
                            onClick = {
                                kind = candidate
                                customKind = ""
                            },
                            label = { Text(kindLabel(candidate)) },
                            leadingIcon = {
                                Icon(
                                    kindIcon(candidate),
                                    contentDescription = null,
                                    tint =
                                        if (kind == candidate) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            },
                            modifier = Modifier.testTag("list_kind_${candidate.name.lowercase()}"),
                        )
                    }
                }
                OutlinedTextField(
                    value = customKind,
                    onValueChange = {
                        customKind = it.take(MAX_CUSTOM_KIND_LENGTH)
                        if (customKind.isNotBlank()) kind = ListKind.GENERAL
                    },
                    modifier = Modifier.fillMaxWidth().testTag("custom_list_kind_input"),
                    label = { Text(stringResource(R.string.lists_custom_kind_label)) },
                    supportingText = { Text(stringResource(R.string.lists_custom_kind_description)) },
                    singleLine = true,
                )
                if (customKinds.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs)) {
                        customKinds.sorted().forEach { savedKind ->
                            AssistChip(
                                onClick = {
                                    customKind = savedKind
                                    kind = ListKind.GENERAL
                                },
                                label = { Text(savedKind) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, kind, customKind) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_list"),
            ) {
                Text(stringResource(R.string.lists_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lists_cancel)) } },
    )
}

@Composable
private fun ItemEditorDialog(
    item: DayListItem?,
    presets: Set<String>,
    onSavePreset: (String) -> Unit,
    onRemovePreset: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var quantity by remember(item?.id) { mutableStateOf(item?.quantity.orEmpty()) }
    var note by remember(item?.id) { mutableStateOf(item?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (item == null) R.string.lists_add_item_title else R.string.lists_edit_item_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
            ) {
                if (presets.isNotEmpty()) {
                    Text(stringResource(R.string.lists_item_presets), style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xs),
                    ) {
                        presets.sorted().forEach { preset ->
                            InputChip(
                                selected = false,
                                onClick = { title = preset },
                                label = { Text(preset) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.lists_remove_preset, preset),
                                        modifier = Modifier.size(18.dp).clickable { onRemovePreset(preset) },
                                    )
                                },
                                modifier = Modifier.testTag("list_item_preset_$preset"),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_ITEM_TITLE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("list_item_name_input"),
                    label = { Text(stringResource(R.string.lists_item_name_label)) },
                    singleLine = true,
                )
                TextButton(
                    onClick = { onSavePreset(title) },
                    enabled = title.isNotBlank() && title !in presets,
                    modifier = Modifier.testTag("save_list_item_preset"),
                ) {
                    Text(stringResource(R.string.lists_save_preset))
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.take(MAX_QUANTITY_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("list_item_quantity_input"),
                    label = { Text(stringResource(R.string.lists_quantity_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag("list_item_note_input"),
                    label = { Text(stringResource(R.string.lists_note_label)) },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, quantity, note) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_list_item"),
            ) {
                Text(stringResource(R.string.lists_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lists_cancel)) } },
    )
}

@Composable
private fun DeleteDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_delete")) {
                Text(stringResource(R.string.lists_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lists_cancel)) } },
    )
}

private fun kindIcon(kind: ListKind): ImageVector =
    when (kind) {
        ListKind.GENERAL -> Icons.Rounded.Checklist
        ListKind.SHOPPING -> Icons.Rounded.ShoppingCart
        ListKind.PACKING -> Icons.Rounded.Luggage
        ListKind.IDEAS -> Icons.Rounded.Lightbulb
    }

@Composable
private fun kindLabel(kind: ListKind): String =
    stringResource(
        when (kind) {
            ListKind.GENERAL -> R.string.lists_kind_general
            ListKind.SHOPPING -> R.string.lists_kind_shopping
            ListKind.PACKING -> R.string.lists_kind_packing
            ListKind.IDEAS -> R.string.lists_kind_ideas
        },
    )

private const val MAX_LIST_TITLE_LENGTH = 80
private const val MAX_CUSTOM_KIND_LENGTH = 40
private const val MAX_ITEM_TITLE_LENGTH = 120
private const val MAX_QUANTITY_LENGTH = 32
private const val MAX_NOTE_LENGTH = 500
