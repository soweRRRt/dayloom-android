package com.sowerrrt.dayloom.core.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
@JvmInline
value class EntityId(
    val value: String,
) {
    companion object {
        fun random(): EntityId = EntityId(UUID.randomUUID().toString())
    }
}

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class AppLanguage {
    SYSTEM,
    RUSSIAN,
    ENGLISH,
}

@Serializable
enum class AccentPalette {
    VIOLET,
    OCEAN,
    CORAL,
    FOREST,
}

@Serializable
enum class StartDestination {
    HOME,
    HABITS,
    PLANNER,
    LISTS,
}

@Serializable
enum class BottomSection {
    HOME,
    HABITS,
    PLANNER,
    LISTS,
    MORE,
}

@Serializable
enum class HomeSection {
    HABITS,
    PLANNER,
    LISTS,
    WISHLIST,
}

@Serializable
enum class PresetType {
    HABIT,
    PLAN,
    LIST_ITEM,
}

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val accentPalette: AccentPalette = AccentPalette.VIOLET,
    val startDestination: StartDestination = StartDestination.HOME,
    val automaticUpdateChecks: Boolean = true,
    val lockWholeApp: Boolean = false,
    val lastUpdateCheckEpochMillis: Long? = null,
    val bottomSections: List<BottomSection> = BottomSection.entries,
    val homeSections: List<HomeSection> = HomeSection.entries,
    val habitPresets: Set<String> = emptySet(),
    val planPresets: Set<String> = emptySet(),
    val listItemPresets: Set<String> = emptySet(),
)

@Serializable
data class AttachmentRef(
    val id: EntityId,
    val displayName: String,
    val mimeType: String,
)

@Serializable
data class Habit(
    val id: EntityId,
    val title: String,
    val createdAtEpochMillis: Long,
    val startEpochDay: Long = 0L,
    val scheduledWeekdays: Set<Weekday> = Weekday.entries.toSet(),
    val repeatEveryDays: Int? = null,
    val scheduledMonthDays: Set<Int> = emptySet(),
    val reminderMinutesOfDay: Int? = null,
    val completedEpochDays: Set<Long> = emptySet(),
    val image: AttachmentRef? = null,
    val targetAmount: String = "",
    val targetUnit: String = "",
    val archived: Boolean = false,
)

@Serializable
data class HabitsSnapshot(
    val habits: List<Habit> = emptyList(),
)

@Serializable
enum class Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
    ;

    companion object {
        fun fromEpochDay(epochDay: Long): Weekday = entries[LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1]
    }
}

fun Habit.isScheduledOn(epochDay: Long): Boolean {
    if (archived || epochDay < startEpochDay) return false
    if (scheduledMonthDays.isNotEmpty()) {
        return LocalDate.ofEpochDay(epochDay).dayOfMonth in scheduledMonthDays
    }
    val interval = repeatEveryDays
    return if (interval != null && interval > 0) {
        (epochDay - startEpochDay) % interval == 0L
    } else {
        Weekday.fromEpochDay(epochDay) in scheduledWeekdays
    }
}

fun Habit.currentStreak(asOfEpochDay: Long): Int {
    var day = asOfEpochDay
    while (!isScheduledOn(day) && day >= startEpochDay) day--
    var streak = 0
    while (day >= startEpochDay && isScheduledOn(day) && day in completedEpochDays) {
        streak++
        do {
            day--
        } while (day >= startEpochDay && !isScheduledOn(day))
    }
    return streak
}

fun Habit.bestStreak(): Int {
    val completed = completedEpochDays.filter(::isScheduledOn).sorted()
    var best = 0
    var current = 0
    var previous: Long? = null
    completed.forEach { day ->
        var expected = previous?.plus(1)
        while (expected != null && expected <= day && !isScheduledOn(expected)) expected++
        current = if (previous == null || expected == day) current + 1 else 1
        best = maxOf(best, current)
        previous = day
    }
    return best
}

@Serializable
data class PlanItem(
    val id: EntityId,
    val title: String,
    val dateEpochDay: Long,
    val createdAtEpochMillis: Long,
    val completed: Boolean = false,
    val reminderMinutesOfDay: Int? = null,
    val image: AttachmentRef? = null,
)

@Serializable
data class PlannerSnapshot(
    val plans: List<PlanItem> = emptyList(),
)

@Serializable
enum class ListKind {
    GENERAL,
    SHOPPING,
    PACKING,
    IDEAS,
}

@Serializable
data class DayListItem(
    val id: EntityId,
    val title: String,
    val quantity: String = "",
    val note: String = "",
    val completed: Boolean = false,
    val order: Int,
    val createdAtEpochMillis: Long,
)

@Serializable
data class DayList(
    val id: EntityId,
    val title: String,
    val kind: ListKind = ListKind.GENERAL,
    val customKind: String = "",
    val items: List<DayListItem> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

@Serializable
data class ListsSnapshot(
    val lists: List<DayList> = emptyList(),
)

@Serializable
enum class WishPriority {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
data class WishContribution(
    val id: EntityId,
    val amountMinor: Long,
    val note: String = "",
    val createdAtEpochMillis: Long,
)

@Serializable
data class WishGoal(
    val id: EntityId,
    val title: String,
    val targetMinor: Long,
    val currencyCode: String,
    val priority: WishPriority = WishPriority.MEDIUM,
    val note: String = "",
    val purchaseUrl: String = "",
    val image: AttachmentRef? = null,
    val contributions: List<WishContribution> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

val WishGoal.savedMinor: Long
    get() = contributions.sumOf(WishContribution::amountMinor)

val WishGoal.isCompleted: Boolean
    get() = savedMinor >= targetMinor

@Serializable
data class WishlistSnapshot(
    val goals: List<WishGoal> = emptyList(),
)

@Serializable
data class VaultEntry(
    val id: EntityId,
    val title: String,
    val username: String = "",
    val password: String,
    val website: String = "",
    val note: String = "",
    val category: String = "",
    val favorite: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

@Serializable
data class VaultSnapshot(
    val entries: List<VaultEntry> = emptyList(),
)
