package com.sowerrrt.dayloom.feature.habits

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.HabitPreset
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.toHabitPresetOrNull
import com.sowerrrt.dayloom.core.model.toStorageValue
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.NotificationScope
import com.sowerrrt.dayloom.core.notifications.activeHabitReminders
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitsUiState(
    val habits: List<Habit> = emptyList(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val reminderSchedulingFailed: Boolean = false,
    val imagePaths: Map<EntityId, String> = emptyMap(),
    val isChangingImage: Boolean = false,
    val hasImageError: Boolean = false,
    val presets: List<HabitPreset> = emptyList(),
) {
    val completedToday: Int
        get() = habits.count { todayEpochDay in it.completedEpochDays }
}

@HiltViewModel
class HabitsViewModel
    @Inject
    constructor(
        private val repository: HabitsRepository,
        private val notificationScheduler: NotificationScheduler,
        private val attachmentRepository: AttachmentRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(HabitsUiState())
        val uiState: StateFlow<HabitsUiState> = mutableUiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, hasError = false) }
                runCatching { repository.loadHabits() to settingsRepository.settings.first().habitPresets }
                    .onSuccess { (habits, presets) ->
                        val reminderResult =
                            notificationScheduler.rescheduleAll(
                                NotificationScope.HABITS,
                                habits.activeHabitReminders(),
                            )
                        mutableUiState.update {
                            it.copy(
                                habits = habits,
                                todayEpochDay = LocalDate.now().toEpochDay(),
                                isLoading = false,
                                hasError = false,
                                reminderSchedulingFailed = reminderResult.isFailure,
                                imagePaths = imagePaths(habits),
                                presets =
                                    presets
                                        .mapNotNull(
                                            String::toHabitPresetOrNull,
                                        ).sortedBy { it.title.lowercase() },
                            )
                        }
                    }.onFailure {
                        mutableUiState.update { it.copy(isLoading = false, hasError = true) }
                    }
            }
        }

        fun createHabit(
            title: String,
            scheduledWeekdays: Set<Weekday>,
            repeatEveryDays: Int?,
            scheduledMonthDays: Set<Int>,
            reminderMinutesOfDay: Int?,
            targetAmount: String = "",
            targetUnit: String = "",
        ) {
            if (
                title.isBlank() ||
                (repeatEveryDays == null && scheduledMonthDays.isEmpty() && scheduledWeekdays.isEmpty())
            ) {
                return
            }
            val today = LocalDate.now().toEpochDay()
            updateHabits {
                repository.createHabit(
                    title,
                    scheduledWeekdays,
                    today,
                    repeatEveryDays = repeatEveryDays,
                    scheduledMonthDays = scheduledMonthDays,
                    reminderMinutesOfDay = reminderMinutesOfDay,
                    targetAmount = targetAmount,
                    targetUnit = targetUnit,
                )
            }
        }

        fun updateHabit(
            id: EntityId,
            title: String,
            scheduledWeekdays: Set<Weekday>,
            repeatEveryDays: Int?,
            scheduledMonthDays: Set<Int>,
            reminderMinutesOfDay: Int?,
            targetAmount: String = "",
            targetUnit: String = "",
        ) {
            if (
                title.isBlank() ||
                (repeatEveryDays == null && scheduledMonthDays.isEmpty() && scheduledWeekdays.isEmpty())
            ) {
                return
            }
            updateHabits {
                repository.updateHabit(
                    id = id,
                    title = title,
                    scheduledWeekdays = scheduledWeekdays,
                    repeatEveryDays = repeatEveryDays,
                    scheduledMonthDays = scheduledMonthDays,
                    reminderMinutesOfDay = reminderMinutesOfDay,
                    targetAmount = targetAmount,
                    targetUnit = targetUnit,
                )
            }
        }

        fun savePreset(
            title: String,
            scheduledWeekdays: Set<Weekday>,
            repeatEveryDays: Int?,
            scheduledMonthDays: Set<Int>,
            reminderMinutesOfDay: Int?,
            targetAmount: String,
            targetUnit: String,
        ) {
            val normalized = title.trim()
            if (
                normalized.isEmpty() ||
                (repeatEveryDays == null && scheduledMonthDays.isEmpty() && scheduledWeekdays.isEmpty())
            ) {
                return
            }
            val preset =
                HabitPreset(
                    title = normalized,
                    scheduledWeekdays = scheduledWeekdays,
                    repeatEveryDays = repeatEveryDays,
                    scheduledMonthDays = scheduledMonthDays,
                    reminderMinutesOfDay = reminderMinutesOfDay,
                    targetAmount = targetAmount.trim(),
                    targetUnit = targetUnit.trim(),
                )
            viewModelScope.launch {
                removeStoredHabitPresets(normalized)
                settingsRepository.addPreset(PresetType.HABIT, preset.toStorageValue())
                mutableUiState.update {
                    it.copy(
                        presets =
                            (it.presets.filterNot { existing -> existing.title.equals(normalized, true) } + preset)
                                .sortedBy { saved -> saved.title.lowercase() },
                    )
                }
            }
        }

        fun removePreset(preset: HabitPreset) {
            viewModelScope.launch {
                removeStoredHabitPresets(preset.title)
                mutableUiState.update {
                    it.copy(presets = it.presets.filterNot { saved -> saved.title.equals(preset.title, true) })
                }
            }
        }

        fun createFromPreset(preset: HabitPreset) {
            createHabit(
                preset.title,
                preset.scheduledWeekdays,
                preset.repeatEveryDays,
                preset.scheduledMonthDays,
                preset.reminderMinutesOfDay,
                preset.targetAmount,
                preset.targetUnit,
            )
        }

        fun toggleCompletion(id: EntityId) {
            val today = mutableUiState.value.todayEpochDay
            updateHabits { repository.toggleCompletion(id, today) }
        }

        fun archiveHabit(id: EntityId) {
            val previous =
                mutableUiState.value.habits
                    .firstOrNull { it.id == id }
                    ?.image
            updateHabits {
                repository.archiveHabit(id).also {
                    if (previous != null) runCatching { attachmentRepository.delete(previous) }
                }
            }
        }

        fun setImage(
            habitId: EntityId,
            uri: Uri,
        ) {
            if (mutableUiState.value.isChangingImage) return
            viewModelScope.launch {
                mutableUiState.update { it.copy(isChangingImage = true, hasImageError = false) }
                val previous =
                    mutableUiState.value.habits
                        .firstOrNull { it.id == habitId }
                        ?.image
                runCatching {
                    val imported = attachmentRepository.importImage(uri)
                    try {
                        repository.setImage(habitId, imported).also {
                            if (previous != null) attachmentRepository.delete(previous)
                        }
                    } catch (error: Throwable) {
                        attachmentRepository.delete(imported)
                        throw error
                    }
                }.onSuccess(::applyHabits)
                    .onFailure {
                        mutableUiState.update { state ->
                            state.copy(isChangingImage = false, hasImageError = true)
                        }
                    }
            }
        }

        fun removeImage(habitId: EntityId) {
            if (mutableUiState.value.isChangingImage) return
            viewModelScope.launch {
                val previous =
                    mutableUiState.value.habits
                        .firstOrNull { it.id == habitId }
                        ?.image ?: return@launch
                mutableUiState.update { it.copy(isChangingImage = true, hasImageError = false) }
                runCatching {
                    repository.setImage(habitId, null).also { attachmentRepository.delete(previous) }
                }.onSuccess(::applyHabits)
                    .onFailure {
                        mutableUiState.update { state ->
                            state.copy(isChangingImage = false, hasImageError = true)
                        }
                    }
            }
        }

        private fun updateHabits(operation: suspend () -> List<Habit>) {
            viewModelScope.launch {
                runCatching { operation() }
                    .onSuccess { habits ->
                        val reminderResult =
                            notificationScheduler.rescheduleAll(
                                NotificationScope.HABITS,
                                habits.activeHabitReminders(),
                            )
                        mutableUiState.update {
                            it.copy(
                                habits = habits,
                                hasError = false,
                                reminderSchedulingFailed = reminderResult.isFailure,
                                imagePaths = imagePaths(habits),
                                isChangingImage = false,
                                hasImageError = false,
                            )
                        }
                    }.onFailure {
                        mutableUiState.update { it.copy(hasError = true) }
                    }
            }
        }

        private fun applyHabits(habits: List<Habit>) {
            mutableUiState.update {
                it.copy(
                    habits = habits,
                    imagePaths = imagePaths(habits),
                    isLoading = false,
                    hasError = false,
                    isChangingImage = false,
                    hasImageError = false,
                )
            }
        }

        private fun imagePaths(habits: List<Habit>): Map<EntityId, String> =
            habits
                .mapNotNull { habit ->
                    habit.image?.let(attachmentRepository::localPath)?.let { habit.id to it }
                }.toMap()

        private suspend fun removeStoredHabitPresets(title: String) {
            settingsRepository.settings
                .first()
                .habitPresets
                .filter { it.toHabitPresetOrNull()?.title?.equals(title, true) == true }
                .forEach { settingsRepository.removePreset(PresetType.HABIT, it) }
        }
    }
