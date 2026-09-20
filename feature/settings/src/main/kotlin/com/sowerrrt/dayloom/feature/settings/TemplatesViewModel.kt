package com.sowerrrt.dayloom.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.TemplateBundle
import com.sowerrrt.dayloom.core.model.TemplateBundleEntry
import com.sowerrrt.dayloom.core.model.TemplateEntryType
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.ListsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import com.sowerrrt.dayloom.core.storage.TemplateBundlesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplatesUiState(
    val bundles: List<TemplateBundle> = emptyList(),
    val availableEntries: List<TemplateBundleEntry> = emptyList(),
    val lists: List<DayList> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val appliedBundleName: String? = null,
)

@HiltViewModel
class TemplatesViewModel
    @Inject
    constructor(
        private val bundlesRepository: TemplateBundlesRepository,
        private val habitsRepository: HabitsRepository,
        private val plannerRepository: PlannerRepository,
        private val listsRepository: ListsRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(TemplatesUiState())
        val uiState: StateFlow<TemplatesUiState> = mutableUiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, hasError = false) }
                runCatching {
                    val habits = habitsRepository.loadHabits()
                    val plans = plannerRepository.loadPlans()
                    val lists = listsRepository.loadLists()
                    var bundles = bundlesRepository.loadBundles()
                    if (bundles.isEmpty()) bundles = seedExamples(habits, plans, lists)
                    TemplatesUiState(
                        bundles = bundles,
                        availableEntries =
                            buildList {
                                addAll(habits.map(Habit::toTemplateEntry))
                                addAll(plans.map(PlanItem::toTemplateEntry))
                                addAll(lists.flatMap { list -> list.items.map { it.toTemplateEntry() } })
                            },
                        lists = lists,
                        isLoading = false,
                    )
                }.onSuccess { mutableUiState.value = it }
                    .onFailure { mutableUiState.update { state -> state.copy(isLoading = false, hasError = true) } }
            }
        }

        fun saveBundle(
            existingId: EntityId?,
            name: String,
            entries: List<TemplateBundleEntry>,
        ) {
            if (name.isBlank() || entries.isEmpty()) return
            viewModelScope.launch {
                runCatching {
                    if (existingId == null) {
                        bundlesRepository.createBundle(name, entries)
                    } else {
                        bundlesRepository.updateBundle(existingId, name, entries)
                    }
                }.onSuccess { bundles -> mutableUiState.update { it.copy(bundles = bundles, hasError = false) } }
                    .onFailure { mutableUiState.update { it.copy(hasError = true) } }
            }
        }

        fun duplicateBundle(id: EntityId) = updateBundles { bundlesRepository.duplicateBundle(id) }

        fun deleteBundle(id: EntityId) = updateBundles { bundlesRepository.deleteBundle(id) }

        fun applyBundle(
            bundle: TemplateBundle,
            entryIds: Set<EntityId>,
            targetListId: EntityId?,
            todayEpochDay: Long,
        ) {
            val selected = bundle.entries.filter { it.id in entryIds }
            if (selected.isEmpty()) return
            viewModelScope.launch {
                runCatching {
                    selected.forEach { entry ->
                        when (entry.type) {
                            TemplateEntryType.HABIT ->
                                habitsRepository.createHabit(
                                    title = entry.title,
                                    scheduledWeekdays = entry.scheduledWeekdays,
                                    startEpochDay = todayEpochDay,
                                    reminderMinutesOfDay = entry.reminderMinutesOfDay,
                                    targetAmount = entry.targetAmount,
                                    targetUnit = entry.targetUnit,
                                    repeatEveryDays = entry.repeatEveryDays,
                                    scheduledMonthDays = entry.scheduledMonthDays,
                                )
                            TemplateEntryType.PLAN ->
                                plannerRepository.createPlanDetails(
                                    title = entry.title,
                                    note = entry.note,
                                    dateEpochDay = todayEpochDay,
                                    reminderMinutesOfDay = entry.reminderMinutesOfDay,
                                    repeat = com.sowerrrt.dayloom.core.model.PlanRepeat.NONE,
                                    repeatUntilEpochDay = null,
                                    reminderEnabled = entry.reminderMinutesOfDay != null,
                                    scheduledWeekdays = entry.scheduledWeekdays,
                                    repeatEveryDays = entry.repeatEveryDays,
                                    scheduledMonthDays = entry.scheduledMonthDays,
                                )
                            TemplateEntryType.LIST_ITEM ->
                                targetListId?.let { listId ->
                                    listsRepository.addItem(listId, entry.title, entry.quantity, entry.note)
                                }
                        }
                    }
                }.onSuccess {
                    mutableUiState.update { it.copy(appliedBundleName = bundle.name, hasError = false) }
                }.onFailure { mutableUiState.update { it.copy(hasError = true) } }
            }
        }

        fun consumeAppliedFeedback() = mutableUiState.update { it.copy(appliedBundleName = null) }

        private fun updateBundles(operation: suspend () -> List<TemplateBundle>) {
            viewModelScope.launch {
                runCatching { operation() }
                    .onSuccess { bundles -> mutableUiState.update { it.copy(bundles = bundles, hasError = false) } }
                    .onFailure { mutableUiState.update { it.copy(hasError = true) } }
            }
        }

        private suspend fun seedExamples(
            habits: List<Habit>,
            plans: List<PlanItem>,
            lists: List<DayList>,
        ): List<TemplateBundle> {
            val morning =
                habits.take(2).map(Habit::toTemplateEntry) + plans.take(1).map(PlanItem::toTemplateEntry)
            val trip =
                plans.drop(1).take(1).map(PlanItem::toTemplateEntry) +
                    lists.flatMap { it.items }.take(3).map { it.toTemplateEntry() }
            if (morning.isNotEmpty()) bundlesRepository.createBundle("Morning focus", morning)
            if (trip.isNotEmpty()) bundlesRepository.createBundle("Trip checklist", trip)
            return bundlesRepository.loadBundles()
        }
    }

private fun Habit.toTemplateEntry() =
    TemplateBundleEntry(
        type = TemplateEntryType.HABIT,
        title = title,
        scheduledWeekdays = scheduledWeekdays,
        repeatEveryDays = repeatEveryDays,
        scheduledMonthDays = scheduledMonthDays,
        reminderMinutesOfDay = reminderMinutesOfDay,
        targetAmount = targetAmount,
        targetUnit = targetUnit,
    )

private fun PlanItem.toTemplateEntry() =
    TemplateBundleEntry(
        type = TemplateEntryType.PLAN,
        title = title,
        note = note,
        scheduledWeekdays = scheduledWeekdays,
        repeatEveryDays = repeatEveryDays,
        scheduledMonthDays = scheduledMonthDays,
        reminderMinutesOfDay = reminderMinutesOfDay,
    )

private fun com.sowerrrt.dayloom.core.model.DayListItem.toTemplateEntry() =
    TemplateBundleEntry(
        type = TemplateEntryType.LIST_ITEM,
        title = title,
        note = note,
        quantity = quantity,
    )
