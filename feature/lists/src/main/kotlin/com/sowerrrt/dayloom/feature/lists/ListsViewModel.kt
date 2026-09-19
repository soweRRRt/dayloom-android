package com.sowerrrt.dayloom.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.DayListItem
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.storage.ListsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListsUiState(
    val lists: List<DayList> = emptyList(),
    val selectedListId: EntityId? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) {
    val selectedList: DayList?
        get() = lists.firstOrNull { it.id == selectedListId }

    val totalItems: Int
        get() = lists.sumOf { it.items.size }

    val completedItems: Int
        get() = lists.sumOf { list -> list.items.count(DayListItem::completed) }
}

@HiltViewModel
class ListsViewModel
    @Inject
    constructor(
        private val repository: ListsRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(ListsUiState())
        val uiState: StateFlow<ListsUiState> = mutableUiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, hasError = false) }
                runCatching { repository.loadLists() }
                    .onSuccess(::applyLists)
                    .onFailure { mutableUiState.update { state -> state.copy(isLoading = false, hasError = true) } }
            }
        }

        fun openList(id: EntityId) {
            mutableUiState.update { it.copy(selectedListId = id) }
        }

        fun closeList() {
            mutableUiState.update { it.copy(selectedListId = null) }
        }

        fun createList(
            title: String,
            kind: ListKind,
        ) {
            if (title.isBlank()) return
            val previousIds =
                mutableUiState.value.lists
                    .map(DayList::id)
                    .toSet()
            updateLists(
                operation = { repository.createList(title, kind) },
                selectedId = { lists -> lists.firstOrNull { it.id !in previousIds }?.id },
            )
        }

        fun updateList(
            id: EntityId,
            title: String,
            kind: ListKind,
        ) {
            if (title.isBlank()) return
            updateLists(operation = { repository.updateList(id, title, kind) })
        }

        fun deleteList(id: EntityId) {
            updateLists(
                operation = { repository.deleteList(id) },
                selectedId = { null },
            )
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

        private fun applyLists(lists: List<DayList>) {
            mutableUiState.update { state ->
                state.copy(
                    lists = lists,
                    selectedListId = state.selectedListId?.takeIf { id -> lists.any { it.id == id } },
                    isLoading = false,
                    hasError = false,
                )
            }
        }
    }
