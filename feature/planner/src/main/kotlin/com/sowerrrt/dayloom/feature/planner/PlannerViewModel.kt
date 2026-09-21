package com.sowerrrt.dayloom.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlanPreset
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.isCompletedOn
import com.sowerrrt.dayloom.core.model.isScheduledOn
import com.sowerrrt.dayloom.core.model.occursOn
import com.sowerrrt.dayloom.core.model.toPlanPresetOrNull
import com.sowerrrt.dayloom.core.model.toStorageValue
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.NotificationScope
import com.sowerrrt.dayloom.core.notifications.activeHabitReminders
import com.sowerrrt.dayloom.core.notifications.activePlanReminders
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class PlannerUiState(
    val habits: List<Habit> = emptyList(),
    val plans: List<PlanItem> = emptyList(),
    val archivedPlans: List<PlanItem> = emptyList(),
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val displayedMonth: YearMonth = YearMonth.now(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val reminderSchedulingFailed: Boolean = false,
    val presets: List<PlanPreset> = emptyList(),
) {
    val selectedHabits: List<Habit>
        get() =
            habits
                .filter { it.isScheduledOn(selectedEpochDay) }
                .sortedWith(
                    compareBy<Habit> { it.reminderMinutesOfDay ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() },
                )

    val selectedPlans: List<PlanItem>
        get() =
            plans
                .filter { it.occursOn(selectedEpochDay) }
                .sortedWith(
                    compareBy<PlanItem> { it.reminderMinutesOfDay ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() },
                )

    fun habitCount(epochDay: Long): Int = habits.count { it.isScheduledOn(epochDay) }

    fun planCount(epochDay: Long): Int = plans.count { it.occursOn(epochDay) }

    fun completedHabitCount(epochDay: Long): Int =
        habits.count { it.isScheduledOn(epochDay) && epochDay in it.completedEpochDays }

    fun completedPlanCount(epochDay: Long): Int = plans.count { it.occursOn(epochDay) && it.isCompletedOn(epochDay) }
}

private data class PlannerLoadedContent(
    val habits: List<Habit>,
    val plans: List<PlanItem>,
    val archivedPlans: List<PlanItem>,
    val presets: Set<String>,
)

@HiltViewModel
class PlannerViewModel
    @Inject
    constructor(
        private val habitsRepository: HabitsRepository,
        private val plannerRepository: PlannerRepository,
        private val notificationScheduler: NotificationScheduler,
        private val attachmentRepository: AttachmentRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(PlannerUiState())
        val uiState: StateFlow<PlannerUiState> = mutableUiState.asStateFlow()
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
                    coroutineScope {
                        val habits = async { habitsRepository.loadHabits() }
                        val plans = async { plannerRepository.loadPlans() }
                        val archivedPlans = async { plannerRepository.loadArchivedPlans() }
                        val presets = async { settingsRepository.settings.first().planPresets }
                        PlannerLoadedContent(
                            habits = habits.await(),
                            plans = plans.await(),
                            archivedPlans = archivedPlans.await(),
                            presets = presets.await(),
                        )
                    }
                }.onSuccess { content ->
                    val habits = content.habits
                    val plans = content.plans
                    val planReminderResult =
                        notificationScheduler.rescheduleAll(NotificationScope.PLANS, plans.activePlanReminders())
                    val habitReminderResult =
                        notificationScheduler.rescheduleAll(
                            NotificationScope.HABITS,
                            habits.activeHabitReminders(),
                        )
                    mutableUiState.update {
                        it.copy(
                            habits = habits,
                            plans = plans,
                            archivedPlans = content.archivedPlans,
                            todayEpochDay = LocalDate.now().toEpochDay(),
                            isLoading = false,
                            hasError = false,
                            reminderSchedulingFailed =
                                planReminderResult.isFailure || habitReminderResult.isFailure,
                            presets =
                                content.presets
                                    .mapNotNull(String::toPlanPresetOrNull)
                                    .sortedBy { it.title.lowercase() },
                        )
                    }
                }.onFailure {
                    mutableUiState.update { it.copy(isLoading = false, hasError = true) }
                }
            }
        }

        fun selectDate(epochDay: Long) {
            mutableUiState.update {
                it.copy(
                    selectedEpochDay = epochDay,
                    displayedMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay)),
                )
            }
        }

        fun showPreviousMonth() {
            mutableUiState.update { it.copy(displayedMonth = it.displayedMonth.minusMonths(1)) }
        }

        fun showNextMonth() {
            mutableUiState.update { it.copy(displayedMonth = it.displayedMonth.plusMonths(1)) }
        }

        fun showToday() {
            val today = LocalDate.now()
            mutableUiState.update {
                it.copy(
                    selectedEpochDay = today.toEpochDay(),
                    displayedMonth = YearMonth.from(today),
                    todayEpochDay = today.toEpochDay(),
                )
            }
        }

        fun createPlan(
            title: String,
            reminderMinutesOfDay: Int?,
            repeat: PlanRepeat = PlanRepeat.NONE,
            reminderEnabled: Boolean = reminderMinutesOfDay != null,
            scheduledWeekdays: Set<Weekday> = emptySet(),
            repeatEveryDays: Int? = null,
            scheduledMonthDays: Set<Int> = emptySet(),
            note: String = "",
            reminderOffsetsMinutes: Set<Int> = setOf(0),
            measurementUnit: String = "",
        ) {
            if (title.isBlank()) return
            val day = mutableUiState.value.selectedEpochDay
            updatePlans {
                plannerRepository.createPlanDetails(
                    title = title,
                    note = note,
                    dateEpochDay = day,
                    reminderMinutesOfDay = reminderMinutesOfDay,
                    repeat = repeat,
                    repeatUntilEpochDay = null,
                    reminderEnabled = reminderEnabled,
                    scheduledWeekdays = scheduledWeekdays,
                    repeatEveryDays = repeatEveryDays,
                    scheduledMonthDays = scheduledMonthDays,
                    reminderOffsetsMinutes = reminderOffsetsMinutes,
                    measurementUnit = measurementUnit,
                )
            }
        }

        fun updatePlan(
            id: EntityId,
            title: String,
            reminderMinutesOfDay: Int?,
            repeat: PlanRepeat = PlanRepeat.NONE,
            reminderEnabled: Boolean = reminderMinutesOfDay != null,
            scheduledWeekdays: Set<Weekday> = emptySet(),
            repeatEveryDays: Int? = null,
            scheduledMonthDays: Set<Int> = emptySet(),
            note: String = "",
            reminderOffsetsMinutes: Set<Int> = setOf(0),
            measurementUnit: String = "",
        ) {
            if (title.isBlank()) return
            val day = mutableUiState.value.selectedEpochDay
            updatePlans {
                plannerRepository.updatePlanDetails(
                    id = id,
                    title = title,
                    note = note,
                    dateEpochDay = day,
                    reminderMinutesOfDay = reminderMinutesOfDay,
                    repeat = repeat,
                    repeatUntilEpochDay = null,
                    reminderEnabled = reminderEnabled,
                    scheduledWeekdays = scheduledWeekdays,
                    repeatEveryDays = repeatEveryDays,
                    scheduledMonthDays = scheduledMonthDays,
                    reminderOffsetsMinutes = reminderOffsetsMinutes,
                    measurementUnit = measurementUnit,
                )
            }
        }

        fun savePreset(
            title: String,
            reminderMinutesOfDay: Int?,
            repeat: PlanRepeat = PlanRepeat.NONE,
            reminderEnabled: Boolean = reminderMinutesOfDay != null,
            scheduledWeekdays: Set<Weekday> = emptySet(),
            repeatEveryDays: Int? = null,
            scheduledMonthDays: Set<Int> = emptySet(),
            reminderOffsetsMinutes: Set<Int> = setOf(0),
            measurementUnit: String = "",
        ) {
            val normalized = title.trim()
            if (normalized.isEmpty()) return
            val preset =
                PlanPreset(
                    title = normalized,
                    reminderMinutesOfDay = reminderMinutesOfDay,
                    repeat = repeat,
                    reminderEnabled = reminderEnabled,
                    scheduledWeekdays = scheduledWeekdays,
                    repeatEveryDays = repeatEveryDays,
                    scheduledMonthDays = scheduledMonthDays,
                    reminderOffsetsMinutes = reminderOffsetsMinutes,
                    measurementUnit = measurementUnit.trim(),
                )
            viewModelScope.launch {
                removeStoredPlanPresets(normalized)
                settingsRepository.addPreset(PresetType.PLAN, preset.toStorageValue())
                mutableUiState.update {
                    it.copy(
                        presets =
                            (it.presets.filterNot { existing -> existing.title.equals(normalized, true) } + preset)
                                .sortedBy { saved -> saved.title.lowercase() },
                    )
                }
            }
        }

        fun removePreset(preset: PlanPreset) {
            viewModelScope.launch {
                removeStoredPlanPresets(preset.title)
                mutableUiState.update {
                    it.copy(presets = it.presets.filterNot { saved -> saved.title.equals(preset.title, true) })
                }
            }
        }

        fun createFromPreset(preset: PlanPreset) {
            createPlan(
                preset.title,
                preset.reminderMinutesOfDay,
                preset.repeat,
                preset.reminderEnabled,
                preset.scheduledWeekdays,
                preset.repeatEveryDays,
                preset.scheduledMonthDays,
                reminderOffsetsMinutes = preset.reminderOffsetsMinutes,
                measurementUnit = preset.measurementUnit,
            )
        }

        fun togglePlan(
            id: EntityId,
            epochDay: Long = mutableUiState.value.selectedEpochDay,
        ) {
            updatePlans { plannerRepository.toggleCompletion(id, epochDay) }
        }

        fun recordMeasurement(
            id: EntityId,
            epochDay: Long,
            value: Double?,
        ) {
            updatePlans { plannerRepository.recordMeasurement(id, epochDay, value) }
        }

        fun movePlan(
            id: EntityId,
            days: Long,
        ) {
            val plan = mutableUiState.value.plans.firstOrNull { it.id == id } ?: return
            updatePlans { plannerRepository.movePlan(id, plan.dateEpochDay + days) }
        }

        fun archivePlan(id: EntityId) {
            updatePlans { plannerRepository.archivePlan(id) }
        }

        fun restorePlan(id: EntityId) {
            updatePlans { plannerRepository.restorePlan(id) }
        }

        fun deletePlan(id: EntityId) {
            val previous =
                mutableUiState.value.plans
                    .firstOrNull { it.id == id }
                    ?.image
            updatePlans {
                plannerRepository.deletePlan(id).also {
                    if (previous != null) runCatching { attachmentRepository.delete(previous) }
                }
            }
        }

        fun toggleHabit(id: EntityId) {
            val state = mutableUiState.value
            if (state.selectedEpochDay > state.todayEpochDay) return
            viewModelScope.launch {
                runCatching { habitsRepository.toggleCompletion(id, state.selectedEpochDay) }
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
                            )
                        }
                    }.onFailure {
                        mutableUiState.update { it.copy(hasError = true) }
                    }
            }
        }

        private fun updatePlans(operation: suspend () -> List<PlanItem>) {
            viewModelScope.launch {
                runCatching { operation() to plannerRepository.loadArchivedPlans() }
                    .onSuccess { (plans, archivedPlans) ->
                        val reminderResult =
                            notificationScheduler.rescheduleAll(NotificationScope.PLANS, plans.activePlanReminders())
                        mutableUiState.update {
                            it.copy(
                                plans = plans,
                                archivedPlans = archivedPlans,
                                hasError = false,
                                reminderSchedulingFailed = reminderResult.isFailure,
                            )
                        }
                    }.onFailure {
                        mutableUiState.update { it.copy(hasError = true) }
                    }
            }
        }

        private suspend fun removeStoredPlanPresets(title: String) {
            settingsRepository.settings
                .first()
                .planPresets
                .filter { it.toPlanPresetOrNull()?.title?.equals(title, true) == true }
                .forEach { settingsRepository.removePreset(PresetType.PLAN, it) }
        }
    }

internal fun List<PlanItem>.activeReminders(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
) = activePlanReminders(nowEpochMillis, zoneId)
