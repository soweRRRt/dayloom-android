package com.sowerrrt.dayloom.feature.planner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlanPreset
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.PresetType
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
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val displayedMonth: YearMonth = YearMonth.now(),
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val reminderSchedulingFailed: Boolean = false,
    val planImagePaths: Map<EntityId, String> = emptyMap(),
    val isChangingImage: Boolean = false,
    val hasImageError: Boolean = false,
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
                        val presets = async { settingsRepository.settings.first().planPresets }
                        Triple(habits.await(), plans.await(), presets.await())
                    }
                }.onSuccess { (habits, plans, presets) ->
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
                            todayEpochDay = LocalDate.now().toEpochDay(),
                            isLoading = false,
                            hasError = false,
                            reminderSchedulingFailed =
                                planReminderResult.isFailure || habitReminderResult.isFailure,
                            planImagePaths = imagePaths(plans),
                            presets = presets.mapNotNull(String::toPlanPresetOrNull).sortedBy { it.title.lowercase() },
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
        ) {
            if (title.isBlank()) return
            val day = mutableUiState.value.selectedEpochDay
            updatePlans {
                plannerRepository.createPlan(
                    title,
                    day,
                    reminderMinutesOfDay,
                    repeat,
                    null,
                    reminderEnabled,
                )
            }
        }

        fun updatePlan(
            id: EntityId,
            title: String,
            reminderMinutesOfDay: Int?,
            repeat: PlanRepeat = PlanRepeat.NONE,
            reminderEnabled: Boolean = reminderMinutesOfDay != null,
        ) {
            if (title.isBlank()) return
            val day = mutableUiState.value.selectedEpochDay
            updatePlans {
                plannerRepository.updatePlan(
                    id,
                    title,
                    day,
                    reminderMinutesOfDay,
                    repeat,
                    null,
                    reminderEnabled,
                )
            }
        }

        fun savePreset(
            title: String,
            reminderMinutesOfDay: Int?,
            repeat: PlanRepeat = PlanRepeat.NONE,
            reminderEnabled: Boolean = reminderMinutesOfDay != null,
        ) {
            val normalized = title.trim()
            if (normalized.isEmpty()) return
            val preset = PlanPreset(normalized, reminderMinutesOfDay, repeat, reminderEnabled)
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
            createPlan(preset.title, preset.reminderMinutesOfDay, preset.repeat, preset.reminderEnabled)
        }

        fun togglePlan(id: EntityId) {
            val day = mutableUiState.value.selectedEpochDay
            updatePlans { plannerRepository.toggleCompletion(id, day) }
        }

        fun movePlan(
            id: EntityId,
            days: Long,
        ) {
            val plan = mutableUiState.value.plans.firstOrNull { it.id == id } ?: return
            updatePlans { plannerRepository.movePlan(id, plan.dateEpochDay + days) }
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

        fun setPlanImage(
            planId: EntityId,
            uri: Uri,
        ) {
            if (mutableUiState.value.isChangingImage) return
            viewModelScope.launch {
                mutableUiState.update { it.copy(isChangingImage = true, hasImageError = false) }
                val previous =
                    mutableUiState.value.plans
                        .firstOrNull { it.id == planId }
                        ?.image
                runCatching {
                    val imported = attachmentRepository.importImage(uri)
                    try {
                        plannerRepository.setImage(planId, imported).also {
                            if (previous != null) attachmentRepository.delete(previous)
                        }
                    } catch (error: Throwable) {
                        attachmentRepository.delete(imported)
                        throw error
                    }
                }.onSuccess(::applyPlans)
                    .onFailure {
                        mutableUiState.update { state ->
                            state.copy(isChangingImage = false, hasImageError = true)
                        }
                    }
            }
        }

        fun removePlanImage(planId: EntityId) {
            if (mutableUiState.value.isChangingImage) return
            viewModelScope.launch {
                val previous =
                    mutableUiState.value.plans
                        .firstOrNull { it.id == planId }
                        ?.image ?: return@launch
                mutableUiState.update { it.copy(isChangingImage = true, hasImageError = false) }
                runCatching {
                    plannerRepository.setImage(planId, null).also { attachmentRepository.delete(previous) }
                }.onSuccess(::applyPlans)
                    .onFailure {
                        mutableUiState.update { state ->
                            state.copy(isChangingImage = false, hasImageError = true)
                        }
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
                runCatching { operation() }
                    .onSuccess { plans ->
                        val reminderResult =
                            notificationScheduler.rescheduleAll(NotificationScope.PLANS, plans.activePlanReminders())
                        mutableUiState.update {
                            it.copy(
                                plans = plans,
                                hasError = false,
                                reminderSchedulingFailed = reminderResult.isFailure,
                                planImagePaths = imagePaths(plans),
                                isChangingImage = false,
                                hasImageError = false,
                            )
                        }
                    }.onFailure {
                        mutableUiState.update { it.copy(hasError = true) }
                    }
            }
        }

        private fun applyPlans(plans: List<PlanItem>) {
            mutableUiState.update {
                it.copy(
                    plans = plans,
                    planImagePaths = imagePaths(plans),
                    hasError = false,
                    isChangingImage = false,
                    hasImageError = false,
                )
            }
        }

        private fun imagePaths(plans: List<PlanItem>): Map<EntityId, String> =
            plans
                .mapNotNull { plan ->
                    plan.image?.let(attachmentRepository::localPath)?.let { plan.id to it }
                }.toMap()

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
