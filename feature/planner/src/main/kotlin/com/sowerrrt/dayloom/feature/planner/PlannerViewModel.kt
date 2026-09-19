package com.sowerrrt.dayloom.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.isScheduledOn
import com.sowerrrt.dayloom.core.notifications.NotificationId
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.ScheduledNotification
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val selectedHabits: List<Habit>
        get() = habits.filter { it.isScheduledOn(selectedEpochDay) }

    val selectedPlans: List<PlanItem>
        get() = plans.filter { it.dateEpochDay == selectedEpochDay }

    fun habitCount(epochDay: Long): Int = habits.count { it.isScheduledOn(epochDay) }

    fun planCount(epochDay: Long): Int = plans.count { it.dateEpochDay == epochDay }
}

@HiltViewModel
class PlannerViewModel
    @Inject
    constructor(
        private val habitsRepository: HabitsRepository,
        private val plannerRepository: PlannerRepository,
        private val notificationScheduler: NotificationScheduler,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(PlannerUiState())
        val uiState: StateFlow<PlannerUiState> = mutableUiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, hasError = false) }
                runCatching {
                    coroutineScope {
                        val habits = async { habitsRepository.loadHabits() }
                        val plans = async { plannerRepository.loadPlans() }
                        habits.await() to plans.await()
                    }
                }.onSuccess { (habits, plans) ->
                    val reminderResult = notificationScheduler.rescheduleAll(plans.activeReminders())
                    mutableUiState.update {
                        it.copy(
                            habits = habits,
                            plans = plans,
                            todayEpochDay = LocalDate.now().toEpochDay(),
                            isLoading = false,
                            hasError = false,
                            reminderSchedulingFailed = reminderResult.isFailure,
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
        ) {
            if (title.isBlank()) return
            val day = mutableUiState.value.selectedEpochDay
            updatePlans { plannerRepository.createPlan(title, day, reminderMinutesOfDay) }
        }

        fun updatePlan(
            id: EntityId,
            title: String,
            reminderMinutesOfDay: Int?,
        ) {
            if (title.isBlank()) return
            val day = mutableUiState.value.selectedEpochDay
            updatePlans { plannerRepository.updatePlan(id, title, day, reminderMinutesOfDay) }
        }

        fun togglePlan(id: EntityId) {
            updatePlans { plannerRepository.toggleCompletion(id) }
        }

        fun deletePlan(id: EntityId) {
            updatePlans { plannerRepository.deletePlan(id) }
        }

        fun toggleHabit(id: EntityId) {
            val state = mutableUiState.value
            if (state.selectedEpochDay > state.todayEpochDay) return
            viewModelScope.launch {
                runCatching { habitsRepository.toggleCompletion(id, state.selectedEpochDay) }
                    .onSuccess { habits ->
                        mutableUiState.update { it.copy(habits = habits, hasError = false) }
                    }.onFailure {
                        mutableUiState.update { it.copy(hasError = true) }
                    }
            }
        }

        private fun updatePlans(operation: suspend () -> List<PlanItem>) {
            viewModelScope.launch {
                runCatching { operation() }
                    .onSuccess { plans ->
                        val reminderResult = notificationScheduler.rescheduleAll(plans.activeReminders())
                        mutableUiState.update {
                            it.copy(
                                plans = plans,
                                hasError = false,
                                reminderSchedulingFailed = reminderResult.isFailure,
                            )
                        }
                    }.onFailure {
                        mutableUiState.update { it.copy(hasError = true) }
                    }
            }
        }
    }

internal fun List<PlanItem>.activeReminders(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledNotification> =
    mapNotNull { plan ->
        val minutes = plan.reminderMinutesOfDay ?: return@mapNotNull null
        val trigger =
            LocalDate
                .ofEpochDay(plan.dateEpochDay)
                .atStartOfDay(zoneId)
                .plusMinutes(minutes.toLong())
                .toInstant()
                .toEpochMilli()
        if (plan.completed || trigger <= nowEpochMillis) {
            null
        } else {
            ScheduledNotification(
                id = NotificationId("plan:${plan.id.value}"),
                triggerAtEpochMillis = trigger,
                title = plan.title,
            )
        }
    }
