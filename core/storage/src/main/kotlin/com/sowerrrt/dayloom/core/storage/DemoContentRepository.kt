package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.WishPriority

data class DemoContent(
    val habits: List<DemoHabit>,
    val plans: List<DemoPlan>,
    val lists: List<DemoList>,
    val goals: List<DemoGoal>,
)

data class DemoHabit(
    val title: String,
    val scheduledWeekdays: Set<Weekday>,
    val completedDayOffsets: List<Int> = emptyList(),
)

data class DemoPlan(
    val title: String,
    val dayOffset: Int,
    val completed: Boolean = false,
)

data class DemoList(
    val title: String,
    val kind: ListKind,
    val items: List<DemoListItem>,
)

data class DemoListItem(
    val title: String,
    val quantity: String = "",
    val note: String = "",
    val completed: Boolean = false,
)

data class DemoGoal(
    val title: String,
    val targetMinor: Long,
    val currencyCode: String,
    val priority: WishPriority,
    val note: String = "",
    val contributionsMinor: List<Long> = emptyList(),
)

data class DemoSeedResult(
    val habitsAdded: Int = 0,
    val plansAdded: Int = 0,
    val listsAdded: Int = 0,
    val goalsAdded: Int = 0,
) {
    val totalAdded: Int
        get() = habitsAdded + plansAdded + listsAdded + goalsAdded
}

interface DemoContentRepository {
    suspend fun seedMissing(
        content: DemoContent,
        todayEpochDay: Long,
    ): DemoSeedResult
}

class LocalDemoContentRepository(
    private val habitsRepository: HabitsRepository,
    private val plannerRepository: PlannerRepository,
    private val listsRepository: ListsRepository,
    private val wishlistRepository: WishlistRepository,
) : DemoContentRepository {
    override suspend fun seedMissing(
        content: DemoContent,
        todayEpochDay: Long,
    ): DemoSeedResult {
        val habitsAdded = seedMissingHabits(content.habits, todayEpochDay)
        val plansAdded = seedMissingPlans(content.plans, todayEpochDay)
        val listsAdded = seedMissingLists(content.lists)
        val goalsAdded = seedMissingGoals(content.goals)
        return DemoSeedResult(habitsAdded, plansAdded, listsAdded, goalsAdded)
    }

    private suspend fun seedMissingHabits(
        habits: List<DemoHabit>,
        todayEpochDay: Long,
    ): Int {
        val existingTitles = habitsRepository.loadHabits().map { it.title }.toSet()
        val missing = habits.filterNot { it.title in existingTitles }
        missing.forEach { demo ->
            val created =
                habitsRepository.createHabit(
                    title = demo.title,
                    scheduledWeekdays = demo.scheduledWeekdays,
                    startEpochDay = todayEpochDay - 30,
                )
            val habit = created.last { it.title == demo.title }
            demo.completedDayOffsets
                .map { todayEpochDay + it }
                .filter { day -> Weekday.fromEpochDay(day) in demo.scheduledWeekdays }
                .distinct()
                .forEach { day -> habitsRepository.toggleCompletion(habit.id, day) }
        }
        return missing.size
    }

    private suspend fun seedMissingPlans(
        plans: List<DemoPlan>,
        todayEpochDay: Long,
    ): Int {
        val existingTitles = plannerRepository.loadPlans().map { it.title }.toSet()
        val missing = plans.filterNot { it.title in existingTitles }
        missing.forEach { demo ->
            val created = plannerRepository.createPlan(demo.title, todayEpochDay + demo.dayOffset)
            if (demo.completed) {
                val plan = created.last { it.title == demo.title }
                plannerRepository.toggleCompletion(plan.id)
            }
        }
        return missing.size
    }

    private suspend fun seedMissingLists(lists: List<DemoList>): Int {
        val existingTitles = listsRepository.loadLists().map { it.title }.toSet()
        val missing = lists.filterNot { it.title in existingTitles }
        missing.forEach { demo ->
            var created = listsRepository.createList(demo.title, demo.kind)
            val listId = created.first { it.title == demo.title }.id
            demo.items.forEach { item ->
                created = listsRepository.addItem(listId, item.title, item.quantity, item.note)
                if (item.completed) {
                    val itemId =
                        created
                            .first { it.id == listId }
                            .items
                            .last { it.title == item.title }
                            .id
                    created = listsRepository.toggleItem(listId, itemId)
                }
            }
        }
        return missing.size
    }

    private suspend fun seedMissingGoals(goals: List<DemoGoal>): Int {
        val existingTitles = wishlistRepository.loadGoals().map { it.title }.toSet()
        val missing = goals.filterNot { it.title in existingTitles }
        missing.forEach { demo ->
            var created =
                wishlistRepository.createGoal(
                    title = demo.title,
                    targetMinor = demo.targetMinor,
                    currencyCode = demo.currencyCode,
                    priority = demo.priority,
                    note = demo.note,
                )
            val goalId = created.first { it.title == demo.title }.id
            demo.contributionsMinor.forEachIndexed { index, amount ->
                created = wishlistRepository.addContribution(goalId, amount, "#${index + 1}")
            }
        }
        return missing.size
    }
}
