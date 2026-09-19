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
    val reminderMinutesOfDay: Int? = null,
    val image: DemoImage? = null,
)

data class DemoPlan(
    val title: String,
    val dayOffset: Int,
    val completed: Boolean = false,
    val reminderMinutesOfDay: Int? = null,
    val image: DemoImage? = null,
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
    val image: DemoImage? = null,
)

enum class DemoImage {
    LAPTOP,
    TRAVEL,
}

data class DemoImageAsset(
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

fun interface DemoImageSource {
    fun load(image: DemoImage): DemoImageAsset
}

data class DemoSeedResult(
    val habitsAdded: Int = 0,
    val plansAdded: Int = 0,
    val listsAdded: Int = 0,
    val goalsAdded: Int = 0,
    val imagesAdded: Int = 0,
) {
    val totalAdded: Int
        get() = habitsAdded + plansAdded + listsAdded + goalsAdded + imagesAdded
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
    private val attachmentRepository: AttachmentRepository? = null,
    private val demoImageSource: DemoImageSource? = null,
) : DemoContentRepository {
    override suspend fun seedMissing(
        content: DemoContent,
        todayEpochDay: Long,
    ): DemoSeedResult {
        val habits = seedMissingHabits(content.habits, todayEpochDay)
        val plans = seedMissingPlans(content.plans, todayEpochDay)
        val listsAdded = seedMissingLists(content.lists)
        val goals = seedMissingGoals(content.goals)
        return DemoSeedResult(
            habitsAdded = habits.entitiesAdded,
            plansAdded = plans.entitiesAdded,
            listsAdded = listsAdded,
            goalsAdded = goals.goalsAdded,
            imagesAdded = habits.imagesAdded + plans.imagesAdded + goals.imagesAdded,
        )
    }

    private suspend fun seedMissingHabits(
        habits: List<DemoHabit>,
        todayEpochDay: Long,
    ): EntitySeedResult {
        var current = habitsRepository.loadHabits()
        var entitiesAdded = 0
        var imagesAdded = 0
        habits.forEach { demo ->
            var habit = current.firstOrNull { it.title == demo.title }
            if (habit == null) {
                current =
                    habitsRepository.createHabit(
                        title = demo.title,
                        scheduledWeekdays = demo.scheduledWeekdays,
                        startEpochDay = todayEpochDay - 30,
                        reminderMinutesOfDay = demo.reminderMinutesOfDay,
                    )
                habit = current.last { it.title == demo.title }
                demo.completedDayOffsets
                    .map { todayEpochDay + it }
                    .filter { day -> Weekday.fromEpochDay(day) in demo.scheduledWeekdays }
                    .distinct()
                    .forEach { day -> current = habitsRepository.toggleCompletion(habit.id, day) }
                entitiesAdded++
            }
            if (habit.image == null && demo.image != null && attachmentRepository != null && demoImageSource != null) {
                val asset = demoImageSource.load(demo.image)
                val attachment = attachmentRepository.importImage(asset.displayName, asset.mimeType, asset.bytes)
                current = habitsRepository.setImage(habit.id, attachment)
                imagesAdded++
            }
        }
        return EntitySeedResult(entitiesAdded, imagesAdded)
    }

    private suspend fun seedMissingPlans(
        plans: List<DemoPlan>,
        todayEpochDay: Long,
    ): EntitySeedResult {
        var current = plannerRepository.loadPlans()
        var entitiesAdded = 0
        var imagesAdded = 0
        plans.forEach { demo ->
            var plan = current.firstOrNull { it.title == demo.title }
            if (plan == null) {
                current =
                    plannerRepository.createPlan(
                        demo.title,
                        todayEpochDay + demo.dayOffset,
                        demo.reminderMinutesOfDay,
                    )
                plan = current.last { it.title == demo.title }
                if (demo.completed) current = plannerRepository.toggleCompletion(plan.id)
                entitiesAdded++
            }
            if (plan.image == null && demo.image != null && attachmentRepository != null && demoImageSource != null) {
                val asset = demoImageSource.load(demo.image)
                val attachment = attachmentRepository.importImage(asset.displayName, asset.mimeType, asset.bytes)
                current = plannerRepository.setImage(plan.id, attachment)
                imagesAdded++
            }
        }
        return EntitySeedResult(entitiesAdded, imagesAdded)
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

    private suspend fun seedMissingGoals(goals: List<DemoGoal>): GoalSeedResult {
        var current = wishlistRepository.loadGoals()
        var goalsAdded = 0
        var imagesAdded = 0
        goals.forEach { demo ->
            var goal = current.firstOrNull { it.title == demo.title }
            if (goal == null) {
                current =
                    wishlistRepository.createGoal(
                        title = demo.title,
                        targetMinor = demo.targetMinor,
                        currencyCode = demo.currencyCode,
                        priority = demo.priority,
                        note = demo.note,
                    )
                goal = current.first { it.title == demo.title }
                demo.contributionsMinor.forEachIndexed { index, amount ->
                    current = wishlistRepository.addContribution(goal.id, amount, "#${index + 1}")
                }
                goalsAdded++
            }
            if (goal.image == null && demo.image != null && attachmentRepository != null && demoImageSource != null) {
                val asset = demoImageSource.load(demo.image)
                val attachment =
                    attachmentRepository.importImage(asset.displayName, asset.mimeType, asset.bytes)
                current = wishlistRepository.setImage(goal.id, attachment)
                imagesAdded++
            }
        }
        return GoalSeedResult(goalsAdded, imagesAdded)
    }

    private data class GoalSeedResult(
        val goalsAdded: Int,
        val imagesAdded: Int,
    )

    private data class EntitySeedResult(
        val entitiesAdded: Int,
        val imagesAdded: Int,
    )
}
