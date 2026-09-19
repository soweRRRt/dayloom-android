package com.sowerrrt.dayloom.feature.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.model.isCompleted
import com.sowerrrt.dayloom.core.storage.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WishlistUiState(
    val goals: List<WishGoal> = emptyList(),
    val selectedGoalId: EntityId? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) {
    val selectedGoal: WishGoal?
        get() = goals.firstOrNull { it.id == selectedGoalId }

    val completedGoals: Int
        get() = goals.count(WishGoal::isCompleted)
}

@HiltViewModel
class WishlistViewModel
    @Inject
    constructor(
        private val repository: WishlistRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(WishlistUiState())
        val uiState: StateFlow<WishlistUiState> = mutableUiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, hasError = false) }
                runCatching { repository.loadGoals() }
                    .onSuccess(::applyGoals)
                    .onFailure { mutableUiState.update { state -> state.copy(isLoading = false, hasError = true) } }
            }
        }

        fun openGoal(id: EntityId) {
            mutableUiState.update { it.copy(selectedGoalId = id) }
        }

        fun closeGoal() {
            mutableUiState.update { it.copy(selectedGoalId = null) }
        }

        fun createGoal(
            title: String,
            targetMinor: Long,
            currencyCode: String,
            priority: WishPriority,
            note: String,
        ) {
            val previousIds =
                mutableUiState.value.goals
                    .map(WishGoal::id)
                    .toSet()
            updateGoals(
                operation = { repository.createGoal(title, targetMinor, currencyCode, priority, note) },
                selectedId = { goals -> goals.firstOrNull { it.id !in previousIds }?.id },
            )
        }

        fun updateGoal(
            id: EntityId,
            title: String,
            targetMinor: Long,
            currencyCode: String,
            priority: WishPriority,
            note: String,
        ) {
            updateGoals(operation = { repository.updateGoal(id, title, targetMinor, currencyCode, priority, note) })
        }

        fun deleteGoal(id: EntityId) {
            updateGoals(
                operation = { repository.deleteGoal(id) },
                selectedId = { null },
            )
        }

        fun addContribution(
            goalId: EntityId,
            amountMinor: Long,
            note: String,
        ) {
            updateGoals(operation = { repository.addContribution(goalId, amountMinor, note) })
        }

        fun deleteContribution(
            goalId: EntityId,
            contributionId: EntityId,
        ) {
            updateGoals(operation = { repository.deleteContribution(goalId, contributionId) })
        }

        private fun updateGoals(
            operation: suspend () -> List<WishGoal>,
            selectedId: (List<WishGoal>) -> EntityId? = { mutableUiState.value.selectedGoalId },
        ) {
            viewModelScope.launch {
                runCatching { operation() }
                    .onSuccess { goals ->
                        mutableUiState.update {
                            it.copy(
                                goals = goals,
                                selectedGoalId = selectedId(goals),
                                isLoading = false,
                                hasError = false,
                            )
                        }
                    }.onFailure { mutableUiState.update { state -> state.copy(hasError = true) } }
            }
        }

        private fun applyGoals(goals: List<WishGoal>) {
            mutableUiState.update { state ->
                state.copy(
                    goals = goals,
                    selectedGoalId = state.selectedGoalId?.takeIf { id -> goals.any { it.id == id } },
                    isLoading = false,
                    hasError = false,
                )
            }
        }
    }
