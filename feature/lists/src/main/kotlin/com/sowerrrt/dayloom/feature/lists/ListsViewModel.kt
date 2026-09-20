package com.sowerrrt.dayloom.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.DayListItem
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.ListItemPreset
import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.toListItemPresetOrNull
import com.sowerrrt.dayloom.core.model.toStorageValue
import com.sowerrrt.dayloom.core.storage.ListsRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListsUiState(
    val lists: List<DayList> = emptyList(),
    val archivedLists: List<DayList> = emptyList(),
    val selectedListId: EntityId? = null,
    val query: String = "",
    val showingArchive: Boolean = false,
    val selectedItemIds: Set<EntityId> = emptySet(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val itemPresets: List<ListItemPreset> = emptyList(),
) {
    val selectedList: DayList?
        get() = lists.firstOrNull { it.id == selectedListId }

    val totalItems: Int
        get() = lists.sumOf { it.items.size }

    val completedItems: Int
        get() = lists.sumOf { list -> list.items.count(DayListItem::completed) }

    val visibleLists: List<DayList>
        get() {
            val source = if (showingArchive) archivedLists else lists
            val needle = query.trim()
            if (needle.isEmpty()) return source
            return source.filter { list ->
                list.title.contains(needle, true) ||
                    list.customKind.contains(needle, true) ||
                    list.items.any { item ->
                        item.title.contains(needle, true) || item.note.contains(needle, true)
                    }
            }
        }
}

@HiltViewModel
class ListsViewModel
    @Inject
    constructor(
        private val repository: ListsRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(ListsUiState())
        val uiState: StateFlow<ListsUiState> = mutableUiState.asStateFlow()
        private var hasEnteredScreen = false

        init {
            refresh()
        }

        fun onScreenEntered() {
            if (hasEnteredScreen) refresh() else hasEnteredScreen = true
        }

        fun refresh() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, hasError = false) }
                runCatching {
                    Triple(
                        repository.loadLists(),
                        repository.loadArchivedLists(),
                        settingsRepository.settings.first().listItemPresets,
                    )
                }.onSuccess { (lists, archived, presets) ->
                    applyLists(
                        lists,
                        archived,
                        presets.mapNotNull(String::toListItemPresetOrNull).sortedBy { it.title.lowercase() },
                    )
                }.onFailure { mutableUiState.update { state -> state.copy(isLoading = false, hasError = true) } }
            }
        }

        fun openList(id: EntityId) {
            mutableUiState.update { it.copy(selectedListId = id) }
        }

        fun closeList() {
            mutableUiState.update { it.copy(selectedListId = null, selectedItemIds = emptySet()) }
        }

        fun setQuery(value: String) {
            mutableUiState.update { it.copy(query = value.take(120)) }
        }

        fun setShowingArchive(value: Boolean) {
            mutableUiState.update {
                it.copy(
                    showingArchive = value,
                    selectedListId = null,
                    selectedItemIds = emptySet(),
                )
            }
        }

        fun createList(
            title: String,
            kind: ListKind,
            customKind: String = "",
        ) {
            if (title.isBlank()) return
            val previousIds =
                mutableUiState.value.lists
                    .map(DayList::id)
                    .toSet()
            updateLists(
                operation = { repository.createList(title, kind, customKind) },
                selectedId = { lists -> lists.firstOrNull { it.id !in previousIds }?.id },
            )
        }

        fun updateList(
            id: EntityId,
            title: String,
            kind: ListKind,
            customKind: String = "",
        ) {
            if (title.isBlank()) return
            updateLists(operation = { repository.updateList(id, title, kind, customKind) })
        }

        fun deleteList(id: EntityId) {
            updateLists(
                operation = { repository.deleteList(id) },
                selectedId = { null },
            )
        }

        fun archiveList(id: EntityId) = reloadAfter { repository.archiveList(id) }

        fun restoreList(id: EntityId) = reloadAfter { repository.restoreList(id) }

        fun duplicateList(id: EntityId) = reloadAfter { repository.duplicateList(id) }

        fun toggleItemSelection(id: EntityId) {
            mutableUiState.update { state ->
                state.copy(
                    selectedItemIds =
                        if (id in state.selectedItemIds) state.selectedItemIds - id else state.selectedItemIds + id,
                )
            }
        }

        fun clearItemSelection() {
            mutableUiState.update { it.copy(selectedItemIds = emptySet()) }
        }

        fun completeSelected(listId: EntityId) {
            val selected = mutableUiState.value.selectedItemIds
            if (selected.isEmpty()) return
            updateLists(operation = { repository.setItemsCompleted(listId, selected, true) })
            clearItemSelection()
        }

        fun deleteSelected(listId: EntityId) {
            val selected = mutableUiState.value.selectedItemIds
            if (selected.isEmpty()) return
            updateLists(operation = { repository.deleteItems(listId, selected) })
            clearItemSelection()
        }

        fun moveSelected(
            sourceListId: EntityId,
            targetListId: EntityId,
        ) {
            val selected = mutableUiState.value.selectedItemIds
            if (selected.isEmpty()) return
            updateLists(operation = { repository.moveItems(sourceListId, targetListId, selected) })
            clearItemSelection()
        }

        fun addItem(
            listId: EntityId,
            title: String,
            quantity: String,
            note: String,
        ) {
            if (title.isBlank()) return
            updateLists(operation = { repository.addItem(listId, title, quantity, note) })
        }

        fun updateItem(
            listId: EntityId,
            itemId: EntityId,
            title: String,
            quantity: String,
            note: String,
        ) {
            if (title.isBlank()) return
            updateLists(operation = { repository.updateItem(listId, itemId, title, quantity, note) })
        }

        fun toggleItem(
            listId: EntityId,
            itemId: EntityId,
        ) {
            updateLists(operation = { repository.toggleItem(listId, itemId) })
        }

        fun deleteItem(
            listId: EntityId,
            itemId: EntityId,
        ) {
            updateLists(operation = { repository.deleteItem(listId, itemId) })
        }

        fun moveItem(
            listId: EntityId,
            itemId: EntityId,
            offset: Int,
        ) {
            updateLists(operation = { repository.moveItem(listId, itemId, offset) })
        }

        fun saveItemPreset(
            title: String,
            quantity: String,
            note: String,
        ) {
            val normalized = title.trim()
            if (normalized.isEmpty()) return
            val preset = ListItemPreset(normalized, quantity.trim(), note.trim())
            viewModelScope.launch {
                removeStoredItemPresets(normalized)
                settingsRepository.addPreset(PresetType.LIST_ITEM, preset.toStorageValue())
                mutableUiState.update {
                    it.copy(
                        itemPresets =
                            (it.itemPresets.filterNot { existing -> existing.title.equals(normalized, true) } + preset)
                                .sortedBy { saved -> saved.title.lowercase() },
                    )
                }
            }
        }

        fun removeItemPreset(preset: ListItemPreset) {
            viewModelScope.launch {
                removeStoredItemPresets(preset.title)
                mutableUiState.update {
                    it.copy(itemPresets = it.itemPresets.filterNot { saved -> saved.title.equals(preset.title, true) })
                }
            }
        }

        fun addItemFromPreset(
            listId: EntityId,
            preset: ListItemPreset,
        ) {
            addItem(listId, preset.title, preset.quantity, preset.note)
        }

        private fun updateLists(
            operation: suspend () -> List<DayList>,
            selectedId: (List<DayList>) -> EntityId? = { mutableUiState.value.selectedListId },
        ) {
            viewModelScope.launch {
                runCatching { operation() }
                    .onSuccess { lists ->
                        mutableUiState.update {
                            it.copy(
                                lists = lists,
                                selectedListId = selectedId(lists),
                                isLoading = false,
                                hasError = false,
                            )
                        }
                    }.onFailure { mutableUiState.update { state -> state.copy(hasError = true) } }
            }
        }

        private fun applyLists(
            lists: List<DayList>,
            archivedLists: List<DayList> = mutableUiState.value.archivedLists,
            presets: List<ListItemPreset> = mutableUiState.value.itemPresets,
        ) {
            mutableUiState.update { state ->
                state.copy(
                    lists = lists,
                    archivedLists = archivedLists,
                    selectedListId = state.selectedListId?.takeIf { id -> lists.any { it.id == id } },
                    isLoading = false,
                    hasError = false,
                    itemPresets = presets,
                )
            }
        }

        private fun reloadAfter(operation: suspend () -> Unit) {
            viewModelScope.launch {
                runCatching {
                    operation()
                    repository.loadLists() to repository.loadArchivedLists()
                }.onSuccess { (active, archived) ->
                    applyLists(active, archived)
                    mutableUiState.update { it.copy(selectedListId = null, selectedItemIds = emptySet()) }
                }.onFailure { mutableUiState.update { state -> state.copy(hasError = true) } }
            }
        }

        private suspend fun removeStoredItemPresets(title: String) {
            settingsRepository.settings
                .first()
                .listItemPresets
                .filter { it.toListItemPresetOrNull()?.title?.equals(title, true) == true }
                .forEach { settingsRepository.removePreset(PresetType.LIST_ITEM, it) }
        }
    }
