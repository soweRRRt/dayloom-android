package com.sowerrrt.dayloom.feature.wishlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.model.isCompleted
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
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
    val imagePaths: Map<EntityId, String> = emptyMap(),
    val isChangingImage: Boolean = false,
    val hasImageError: Boolean = false,
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
        private val attachmentRepository: AttachmentRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(WishlistUiState())
        val uiState: StateFlow<WishlistUiState> = mutableUiState.asStateFlow()
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
            purchaseUrl: String,
        ) {
            val previousIds =
                mutableUiState.value.goals
                    .map(WishGoal::id)
                    .toSet()
            updateGoals(
                operation = { repository.createGoal(title, targetMinor, currencyCode, priority, note, purchaseUrl) },
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
            purchaseUrl: String,
        ) {
            updateGoals(
                operation = {
                    repository.updateGoal(id, title, targetMinor, currencyCode, priority, note, purchaseUrl)
                },
            )
        }

        fun deleteGoal(id: EntityId) {
            val attachment =
                mutableUiState.value.goals
                    .firstOrNull { it.id == id }
                    ?.image
            updateGoals(
                operation = {
                    repository.deleteGoal(id).also {
                        if (attachment != null) attachmentRepository.delete(attachment)
                    }
                },
                selectedId = { null },
            )
        }

        fun setImage(
            goalId: EntityId,
            uri: Uri,
        ) {
            if (mutableUiState.value.isChangingImage) return
            viewModelScope.launch {
                mutableUiState.update { it.copy(isChangingImage = true, hasImageError = false) }
                val previous =
                    mutableUiState.value.goals
                        .firstOrNull { it.id == goalId }
                        ?.image
                runCatching {
                    val imported = attachmentRepository.importImage(uri)
                    try {
                        repository.setImage(goalId, imported).also {
                            if (previous != null) attachmentRepository.delete(previous)
                        }
                    } catch (error: Throwable) {
                        attachmentRepository.delete(imported)
                        throw error
                    }
                }.onSuccess(::applyGoals)
                    .onFailure {
                        mutableUiState.update { state ->
                            state.copy(isChangingImage = false, hasImageError = true)
                        }
                    }
            }
        }

        fun removeImage(goalId: EntityId) {
            if (mutableUiState.value.isChangingImage) return
            viewModelScope.launch {
                val previous =
                    mutableUiState.value.goals
                        .firstOrNull { it.id == goalId }
                        ?.image ?: return@launch
                mutableUiState.update { it.copy(isChangingImage = true, hasImageError = false) }
                runCatching {
                    repository.setImage(goalId, null).also { attachmentRepository.delete(previous) }
                }.onSuccess(::applyGoals)
                    .onFailure {
                        mutableUiState.update { state ->
                            state.copy(isChangingImage = false, hasImageError = true)
                        }
                    }
            }
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
                                imagePaths = imagePaths(goals),
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
                    imagePaths = imagePaths(goals),
                    isLoading = false,
                    hasError = false,
                    isChangingImage = false,
                    hasImageError = false,
                )
            }
        }

        private fun imagePaths(goals: List<WishGoal>): Map<EntityId, String> =
            goals
                .mapNotNull { goal ->
                    goal.image?.let(attachmentRepository::localPath)?.let { goal.id to it }
                }.toMap()
    }
