package com.sowerrrt.dayloom.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.isCompleted
import com.sowerrrt.dayloom.core.model.isCompletedOn
import com.sowerrrt.dayloom.core.model.isScheduledOn
import com.sowerrrt.dayloom.core.model.occursOn
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.NotificationScope
import com.sowerrrt.dayloom.core.notifications.activeHabitReminders
import com.sowerrrt.dayloom.core.notifications.activePlanReminders
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.ListsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import com.sowerrrt.dayloom.core.storage.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val todayHabits: List<Habit> = emptyList(),
    val todayPlans: List<PlanItem> = emptyList(),
    val habitsToday: Int = 0,
    val habitsCompletedToday: Int = 0,
    val plansToday: Int = 0,
    val plansCompletedToday: Int = 0,
    val listCount: Int = 0,
    val openListItems: Int = 0,
    val wishCount: Int = 0,
    val completedWishCount: Int = 0,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val habitsRepository: HabitsRepository,
        private val plannerRepository: PlannerRepository,
        private val listsRepository: ListsRepository,
        private val wishlistRepository: WishlistRepository,
        private val notificationScheduler: NotificationScheduler,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
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
                val today = LocalDate.now().toEpochDay()
                runCatching {
                    coroutineScope {
                        val allHabits = async { habitsRepository.loadHabits() }
                        val allPlans = async { plannerRepository.loadPlans() }
                        val lists = async { listsRepository.loadLists() }
                        val wishes = async { wishlistRepository.loadGoals() }
                        val habits = allHabits.await().filter { it.isScheduledOn(today) }
                        val plans = allPlans.await().filter { it.occursOn(today) }
                        HomeUiState(
                            todayEpochDay = today,
                            todayHabits = habits.sortedWith(compareBy(nullsLast()) { it.reminderMinutesOfDay }),
                            todayPlans = plans.sortedWith(compareBy(nullsLast()) { it.reminderMinutesOfDay }),
                            habitsToday = habits.size,
                            habitsCompletedToday = habits.count { today in it.completedEpochDays },
                            plansToday = plans.size,
                            plansCompletedToday = plans.count { it.isCompletedOn(today) },
                            listCount = lists.await().size,
                            openListItems = lists.await().sumOf { list -> list.items.count { !it.completed } },
                            wishCount = wishes.await().size,
                            completedWishCount = wishes.await().count { it.isCompleted },
                            isLoading = false,
                        )
                    }
                }.onSuccess { state -> mutableUiState.value = state }
                    .onFailure { mutableUiState.update { it.copy(isLoading = false, hasError = true) } }
            }
        }

        fun toggleHabit(id: EntityId) {
            val state = mutableUiState.value
            viewModelScope.launch {
                runCatching { habitsRepository.toggleCompletion(id, state.todayEpochDay) }
                    .onSuccess { habits ->
                        notificationScheduler.rescheduleAll(NotificationScope.HABITS, habits.activeHabitReminders())
                        refresh()
                    }.onFailure { mutableUiState.update { it.copy(hasError = true) } }
            }
        }

        fun togglePlan(id: EntityId) {
            val today = mutableUiState.value.todayEpochDay
            viewModelScope.launch {
                runCatching { plannerRepository.toggleCompletion(id, today) }
                    .onSuccess { plans ->
                        notificationScheduler.rescheduleAll(NotificationScope.PLANS, plans.activePlanReminders())
                        refresh()
                    }.onFailure { mutableUiState.update { it.copy(hasError = true) } }
            }
        }
    }
