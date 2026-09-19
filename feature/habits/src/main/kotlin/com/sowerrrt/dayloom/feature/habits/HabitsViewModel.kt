package com.sowerrrt.dayloom.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitsUiState(
    val habits: List<Habit> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) {
    val completedToday: Int
        get() = habits.count { todayEpochDay in it.completedEpochDays }
}

@HiltViewModel
class HabitsViewModel
    @Inject
    constructor(
        private val repository: HabitsRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(HabitsUiState())
        val uiState: StateFlow<HabitsUiState> = mutableUiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, hasError = false) }
                runCatching { repository.loadHabits() }
                    .onSuccess { habits ->
                        mutableUiState.update {
                            it.copy(habits = habits, isLoading = false, hasError = false)
                        }
                    }.onFailure {
                        mutableUiState.update { it.copy(isLoading = false, hasError = true) }
                    }
            }
        }

        fun createHabit(title: String) {
            if (title.isBlank()) return
            updateHabits { repository.createHabit(title) }
        }

        fun toggleCompletion(id: EntityId) {
            val today = mutableUiState.value.todayEpochDay
            updateHabits { repository.toggleCompletion(id, today) }
        }

        private fun updateHabits(operation: suspend () -> List<Habit>) {
            viewModelScope.launch {
                runCatching { operation() }
                    .onSuccess { habits ->
                        mutableUiState.update { it.copy(habits = habits, hasError = false) }
                    }.onFailure {
                        mutableUiState.update { it.copy(hasError = true) }
                    }
            }
        }
    }
