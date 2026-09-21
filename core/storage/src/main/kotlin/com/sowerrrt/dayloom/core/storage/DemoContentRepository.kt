package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.model.toHabitPresetOrNull
import com.sowerrrt.dayloom.core.model.toListItemPresetOrNull
import com.sowerrrt.dayloom.core.model.toPlanPresetOrNull
import kotlinx.coroutines.flow.first

data class DemoContent(
    val habits: List<DemoHabit>,
    val plans: List<DemoPlan>,
    val lists: List<DemoList>,
    val goals: List<DemoGoal>,
    val habitPresets: List<String> = emptyList(),
    val planPresets: List<String> = emptyList(),
    val listItemPresets: List<String> = emptyList(),
)

data class DemoHabit(
    val title: String,
    val scheduledWeekdays: Set<Weekday>,
    val completedDayOffsets: List<Int> = emptyList(),
    val reminderMinutesOfDay: Int? = null,
    val image: DemoImage? = null,
    val targetAmount: String = "",
    val targetUnit: String = "",
    val repeatEveryDays: Int? = null,
    val scheduledMonthDays: Set<Int> = emptySet(),
    val progressToday: String = "",
)

data class DemoPlan(
    val title: String,
    val dayOffset: Int,
    val completed: Boolean = false,
    val reminderMinutesOfDay: Int? = null,
    val repeat: PlanRepeat = PlanRepeat.NONE,
    val reminderEnabled: Boolean = reminderMinutesOfDay != null,
    val scheduledWeekdays: Set<Weekday> = emptySet(),
    val repeatEveryDays: Int? = null,
    val scheduledMonthDays: Set<Int> = emptySet(),
    val note: String = "",
    val reminderOffsetsMinutes: Set<Int> = setOf(0),
    val measurementUnit: String = "",
    val measurementValuesByDayOffset: Map<Int, Double> = emptyMap(),
)

data class DemoList(
    val title: String,
    val kind: ListKind,
    val items: List<DemoListItem>,
    val customKind: String = "",
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
    val purchaseUrl: String = "",
    val category: String = "",
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
    val presetsAdded: Int = 0,
    val detailsAdded: Int = 0,
) {
    val totalAdded: Int
        get() = habitsAdded + plansAdded + listsAdded + goalsAdded + imagesAdded + presetsAdded + detailsAdded
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
    private val settingsRepository: SettingsRepository? = null,
) : DemoContentRepository {
    override suspend fun seedMissing(
        content: DemoContent,
        todayEpochDay: Long,
    ): DemoSeedResult {
        val habits = seedMissingHabits(content.habits, todayEpochDay)
        val plans = seedMissingPlans(content.plans, todayEpochDay)
        val lists = seedMissingLists(content.lists)
        val goals = seedMissingGoals(content.goals)
        val presetsAdded = seedMissingPresets(content)
        return DemoSeedResult(
            habitsAdded = habits.entitiesAdded,
            plansAdded = plans.entitiesAdded,
            listsAdded = lists.entitiesAdded,
            goalsAdded = goals.goalsAdded,
            imagesAdded = habits.imagesAdded + plans.imagesAdded + goals.imagesAdded,
            presetsAdded = presetsAdded,
            detailsAdded = habits.detailsAdded + lists.detailsAdded,
        )
    }

    private suspend fun seedMissingHabits(
        habits: List<DemoHabit>,
        todayEpochDay: Long,
    ): EntitySeedResult {
        var current = habitsRepository.loadHabits()
        var entitiesAdded = 0
        var imagesAdded = 0
        var detailsAdded = 0
        habits.forEach { demo ->
            var habit = current.firstOrNull { it.title == demo.title }
            if (habit == null) {
                current =
                    habitsRepository.createHabit(
                        title = demo.title,
                        scheduledWeekdays = demo.scheduledWeekdays,
                        startEpochDay = todayEpochDay - 30,
                        repeatEveryDays = demo.repeatEveryDays,
                        scheduledMonthDays = demo.scheduledMonthDays,
                        reminderMinutesOfDay = demo.reminderMinutesOfDay,
                        targetAmount = demo.targetAmount,
                        targetUnit = demo.targetUnit,
                    )
                habit = current.last { it.title == demo.title }
                demo.completedDayOffsets
                    .map { todayEpochDay + it }
                    .filter { day ->
                        when {
                            demo.scheduledMonthDays.isNotEmpty() ->
                                java.time.LocalDate
                                    .ofEpochDay(day)
                                    .dayOfMonth in demo.scheduledMonthDays
                            demo.repeatEveryDays != null -> (day - (todayEpochDay - 30)) % demo.repeatEveryDays == 0L
                            else -> Weekday.fromEpochDay(day) in demo.scheduledWeekdays
                        }
                    }.distinct()
                    .forEach { day -> current = habitsRepository.toggleCompletion(habit.id, day) }
                entitiesAdded++
            }
            if (
                habit.targetAmount.isBlank() &&
                habit.targetUnit.isBlank() &&
                (demo.targetAmount.isNotBlank() || demo.targetUnit.isNotBlank())
            ) {
                current =
                    habitsRepository.updateHabit(
                        id = habit.id,
                        title = habit.title,
                        scheduledWeekdays = habit.scheduledWeekdays,
                        repeatEveryDays = habit.repeatEveryDays,
                        scheduledMonthDays = habit.scheduledMonthDays,
                        reminderMinutesOfDay = habit.reminderMinutesOfDay,
                        targetAmount = demo.targetAmount,
                        targetUnit = demo.targetUnit,
                    )
                habit = current.first { it.id == habit.id }
                detailsAdded++
            }
            if (demo.progressToday.isNotBlank() && habit.progressByEpochDay[todayEpochDay] == null) {
                current = habitsRepository.setProgress(habit.id, todayEpochDay, demo.progressToday)
                habit = current.first { it.id == habit.id }
                detailsAdded++
            }
            if (habit.image == null && demo.image != null && attachmentRepository != null && demoImageSource != null) {
                val asset = demoImageSource.load(demo.image)
                val attachment = attachmentRepository.importImage(asset.displayName, asset.mimeType, asset.bytes)
                current = habitsRepository.setImage(habit.id, attachment)
                imagesAdded++
            }
        }
        return EntitySeedResult(entitiesAdded, imagesAdded, detailsAdded)
    }

    private suspend fun seedMissingPlans(
        plans: List<DemoPlan>,
        todayEpochDay: Long,
    ): EntitySeedResult {
        var current = plannerRepository.loadPlans()
        var entitiesAdded = 0
        var imagesAdded = 0
        var detailsAdded = 0
        plans.forEach { demo ->
            var plan = current.firstOrNull { it.title == demo.title }
            val createdThisRun = plan == null
            if (plan == null) {
                current =
                    plannerRepository.createPlanDetails(
                        title = demo.title,
                        note = demo.note,
                        dateEpochDay = todayEpochDay + demo.dayOffset,
                        reminderMinutesOfDay = demo.reminderMinutesOfDay,
                        repeat = demo.repeat,
                        repeatUntilEpochDay = null,
                        reminderEnabled = demo.reminderEnabled,
                        scheduledWeekdays = demo.scheduledWeekdays,
                        repeatEveryDays = demo.repeatEveryDays,
                        scheduledMonthDays = demo.scheduledMonthDays,
                        reminderOffsetsMinutes = demo.reminderOffsetsMinutes,
                        measurementUnit = demo.measurementUnit,
                    )
                plan = current.last { it.title == demo.title }
                if (demo.completed) current = plannerRepository.toggleCompletion(plan.id)
                entitiesAdded++
            }
            if (
                demo.note.isNotBlank() && plan.note.isBlank()
            ) {
                current =
                    plannerRepository.updatePlanDetails(
                        id = plan.id,
                        title = plan.title,
                        note = demo.note,
                        dateEpochDay = plan.dateEpochDay,
                        reminderMinutesOfDay = plan.reminderMinutesOfDay,
                        repeat = plan.repeat,
                        repeatUntilEpochDay = plan.repeatUntilEpochDay,
                        reminderEnabled = plan.reminderEnabled,
                        scheduledWeekdays = plan.scheduledWeekdays,
                        repeatEveryDays = plan.repeatEveryDays,
                        scheduledMonthDays = plan.scheduledMonthDays,
                        reminderOffsetsMinutes = plan.reminderOffsetsMinutes,
                        measurementUnit = plan.measurementUnit,
                    )
                plan = current.first { it.id == plan.id }
                detailsAdded++
            }
            if (createdThisRun) {
                demo.measurementValuesByDayOffset.forEach { (dayOffset, value) ->
                    val epochDay = todayEpochDay + dayOffset
                    val savedPlan = requireNotNull(plan)
                    if (savedPlan.measurementValuesByEpochDay[epochDay] == null) {
                        current = plannerRepository.recordMeasurement(savedPlan.id, epochDay, value)
                        plan = current.first { it.id == savedPlan.id }
                        detailsAdded++
                    }
                }
            }
        }
        return EntitySeedResult(entitiesAdded, imagesAdded, detailsAdded)
    }

    private suspend fun seedMissingLists(lists: List<DemoList>): ListSeedResult {
        var current = listsRepository.loadLists()
        var entitiesAdded = 0
        var detailsAdded = 0
        lists.forEach { demo ->
            var list = current.firstOrNull { it.title == demo.title }
            if (list == null) {
                current = listsRepository.createList(demo.title, demo.kind, demo.customKind)
                list = current.first { it.title == demo.title }
                demo.items.forEach { item ->
                    current = listsRepository.addItem(list.id, item.title, item.quantity, item.note)
                    if (item.completed) {
                        val itemId =
                            current
                                .first { it.id == list.id }
                                .items
                                .last { it.title == item.title }
                                .id
                        current = listsRepository.toggleItem(list.id, itemId)
                    }
                }
                entitiesAdded++
            } else if (list.customKind.isBlank() && demo.customKind.isNotBlank()) {
                current = listsRepository.updateList(list.id, list.title, list.kind, demo.customKind)
                detailsAdded++
            }
        }
        return ListSeedResult(entitiesAdded, detailsAdded)
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
                        purchaseUrl = demo.purchaseUrl,
                    )
                goal = current.first { it.title == demo.title }
                demo.contributionsMinor.forEachIndexed { index, amount ->
                    current = wishlistRepository.addContribution(goal.id, amount, "#${index + 1}")
                }
                goalsAdded++
            }
            if (demo.category.isNotBlank() && goal.category != demo.category) {
                current = wishlistRepository.setCategory(goal.id, demo.category)
                goal = current.first { it.id == goal.id }
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

    private suspend fun seedMissingPresets(content: DemoContent): Int {
        val repository = settingsRepository ?: return 0
        val current = repository.settings.first()
        val groups =
            listOf(
                Triple(PresetType.HABIT, content.habitPresets, current.habitPresets),
                Triple(PresetType.PLAN, content.planPresets, current.planPresets),
                Triple(PresetType.LIST_ITEM, content.listItemPresets, current.listItemPresets),
            )
        var added = 0
        groups.forEach { (type, examples, existing) ->
            val existingTitles = existing.mapNotNull { presetTitle(type, it) }.map(String::lowercase).toSet()
            examples.map(String::trim).filter(String::isNotEmpty).distinct().forEach { value ->
                val title = presetTitle(type, value)
                if (title != null && title.lowercase() !in existingTitles) {
                    repository.addPreset(type, value)
                    added++
                } else if (title != null && value !in existing && title in existing) {
                    repository.removePreset(type, title)
                    repository.addPreset(type, value)
                    added++
                }
            }
        }
        return added
    }

    private fun presetTitle(
        type: PresetType,
        value: String,
    ): String? =
        when (type) {
            PresetType.HABIT -> value.toHabitPresetOrNull()?.title
            PresetType.PLAN -> value.toPlanPresetOrNull()?.title
            PresetType.LIST_ITEM -> value.toListItemPresetOrNull()?.title
        }

    private data class GoalSeedResult(
        val goalsAdded: Int,
        val imagesAdded: Int,
    )

    private data class EntitySeedResult(
        val entitiesAdded: Int,
        val imagesAdded: Int,
        val detailsAdded: Int = 0,
    )

    private data class ListSeedResult(
        val entitiesAdded: Int,
        val detailsAdded: Int,
    )
}
