package com.sowerrrt.dayloom.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.isCompleted
import com.sowerrrt.dayloom.core.model.isScheduledOn
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.ListsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import com.sowerrrt.dayloom.core.storage.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val habitsToday: Int = 0,
    val habitsCompletedToday: Int = 0,
    val plansToday: Int = 0,
    val plansCompletedToday: Int = 0,
    val listCount: Int = 0,
    val openListItems: Int = 0,
    val wishCount: Int = 0,
    val completedWishCount: Int = 0,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val habitsRepository: HabitsRepository,
        private val plannerRepository: PlannerRepository,
        private val listsRepository: ListsRepository,
        private val wishlistRepository: WishlistRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                val today = LocalDate.now().toEpochDay()
                runCatching {
                    val habits = habitsRepository.loadHabits().filter { it.isScheduledOn(today) }
                    val plans = plannerRepository.loadPlans().filter { it.dateEpochDay == today }
                    val lists = listsRepository.loadLists()
                    val wishes = wishlistRepository.loadGoals()
                    HomeUiState(
                        habitsToday = habits.size,
                        habitsCompletedToday = habits.count { today in it.completedEpochDays },
                        plansToday = plans.size,
                        plansCompletedToday = plans.count { it.completed },
                        listCount = lists.size,
                        openListItems = lists.sumOf { list -> list.items.count { !it.completed } },
                        wishCount = wishes.size,
                        completedWishCount = wishes.count { it.isCompleted },
                    )
                }.onSuccess { state -> mutableUiState.update { state } }
            }
        }
    }
