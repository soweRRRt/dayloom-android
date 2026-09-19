package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.HabitsSnapshot
import com.sowerrrt.dayloom.core.model.Weekday
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.ZoneId

interface HabitsRepository {
    suspend fun loadHabits(): List<Habit>

    suspend fun createHabit(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        startEpochDay: Long,
        reminderMinutesOfDay: Int? = null,
        targetAmount: String = "",
        targetUnit: String = "",
        repeatEveryDays: Int? = null,
        scheduledMonthDays: Set<Int> = emptySet(),
    ): List<Habit>

    suspend fun updateHabit(
        id: EntityId,
        title: String,
        scheduledWeekdays: Set<Weekday>,
        reminderMinutesOfDay: Int? = null,
        targetAmount: String = "",
        targetUnit: String = "",
        repeatEveryDays: Int? = null,
        scheduledMonthDays: Set<Int> = emptySet(),
    ): List<Habit>

    suspend fun archiveHabit(id: EntityId): List<Habit>

    suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<Habit>

    suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<Habit>
}

class FileHabitsRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> EntityId = EntityId::random,
) : HabitsRepository {
    private val store =
        FileJsonStore(
            directory = directory,
            fileName = "habits.json",
            payloadSerializer = HabitsSnapshot.serializer(),
            currentSchemaVersion = 2,
            defaultValue = ::HabitsSnapshot,
            migrations =
                mapOf(
                    1 to
                        StorageMigration { envelope ->
                            val payload = requireNotNull(envelope["payload"]).jsonObject
                            val migratedHabits =
                                requireNotNull(payload["habits"]).jsonArray.map { element ->
                                    val habit = element.jsonObject
                                    val createdAtEpochMillis =
                                        requireNotNull(habit["createdAtEpochMillis"])
                                            .jsonPrimitive
                                            .content
                                            .toLong()
                                    val startEpochDay =
                                        Instant
                                            .ofEpochMilli(createdAtEpochMillis)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDate()
                                            .toEpochDay()
                                    JsonObject(
                                        habit +
                                            ("startEpochDay" to JsonPrimitive(startEpochDay)) +
                                            (
                                                "scheduledWeekdays" to
                                                    JsonArray(Weekday.entries.map { JsonPrimitive(it.name) })
                                            ),
                                    )
                                }
                            JsonObject(
                                envelope +
                                    ("schemaVersion" to JsonPrimitive(2)) +
                                    ("payload" to JsonObject(payload + ("habits" to JsonArray(migratedHabits)))),
                            )
                        },
                ),
        )

    override suspend fun loadHabits(): List<Habit> = store.read().visibleHabits()

    override suspend fun createHabit(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        startEpochDay: Long,
        reminderMinutesOfDay: Int?,
        targetAmount: String,
        targetUnit: String,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<Habit> {
        val normalizedTitle = title.trim()
        val target = normalizeTarget(targetAmount, targetUnit)
        validate(normalizedTitle, scheduledWeekdays, repeatEveryDays, scheduledMonthDays, reminderMinutesOfDay)
        return store
            .update { snapshot ->
                snapshot.copy(
                    habits =
                        snapshot.habits +
                            Habit(
                                id = idFactory(),
                                title = normalizedTitle,
                                createdAtEpochMillis = clock(),
                                startEpochDay = startEpochDay,
                                scheduledWeekdays = scheduledWeekdays,
                                repeatEveryDays = repeatEveryDays,
                                scheduledMonthDays = scheduledMonthDays,
                                reminderMinutesOfDay = reminderMinutesOfDay,
                                targetAmount = target.first,
                                targetUnit = target.second,
                            ),
                )
            }.visibleHabits()
    }

    override suspend fun updateHabit(
        id: EntityId,
        title: String,
        scheduledWeekdays: Set<Weekday>,
        reminderMinutesOfDay: Int?,
        targetAmount: String,
        targetUnit: String,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<Habit> {
        val normalizedTitle = title.trim()
        val target = normalizeTarget(targetAmount, targetUnit)
        validate(normalizedTitle, scheduledWeekdays, repeatEveryDays, scheduledMonthDays, reminderMinutesOfDay)
        return updateExisting(id) { habit ->
            habit.copy(
                title = normalizedTitle,
                scheduledWeekdays = scheduledWeekdays,
                repeatEveryDays = repeatEveryDays,
                scheduledMonthDays = scheduledMonthDays,
                reminderMinutesOfDay = reminderMinutesOfDay,
                targetAmount = target.first,
                targetUnit = target.second,
            )
        }
    }

    override suspend fun archiveHabit(id: EntityId): List<Habit> =
        updateExisting(id) { habit -> habit.copy(archived = true, image = null) }

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<Habit> = updateExisting(id) { habit -> habit.copy(image = image) }

    override suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<Habit> =
        updateExisting(id) { habit ->
            val days = habit.completedEpochDays.toMutableSet()
            if (!days.add(epochDay)) days.remove(epochDay)
            habit.copy(completedEpochDays = days)
        }

    private suspend fun updateExisting(
        id: EntityId,
        transform: (Habit) -> Habit,
    ): List<Habit> =
        store
            .update { snapshot ->
                require(snapshot.habits.any { it.id == id }) { "Habit does not exist" }
                snapshot.copy(
                    habits = snapshot.habits.map { habit -> if (habit.id == id) transform(habit) else habit },
                )
            }.visibleHabits()

    private fun validate(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
        reminderMinutesOfDay: Int?,
    ) {
        require(title.isNotEmpty()) { "Habit title must not be blank" }
        require(title.length <= MAX_TITLE_LENGTH) { "Habit title is too long" }
        require(repeatEveryDays != null || scheduledMonthDays.isNotEmpty() || scheduledWeekdays.isNotEmpty()) {
            "Habit schedule must contain at least one day"
        }
        require(repeatEveryDays == null || repeatEveryDays in 1..MAX_REPEAT_INTERVAL_DAYS) {
            "Habit repeat interval is out of range"
        }
        require(repeatEveryDays == null || scheduledMonthDays.isEmpty()) {
            "Habit cannot use interval and month days together"
        }
        require(scheduledMonthDays.all { it in 1..31 }) { "Habit month days are out of range" }
        require(reminderMinutesOfDay == null || reminderMinutesOfDay in 0 until 24 * 60) {
            "Habit reminder must be within a day"
        }
    }

    private fun normalizeTarget(
        amount: String,
        unit: String,
    ): Pair<String, String> {
        val normalizedAmount = amount.trim()
        val normalizedUnit = unit.trim()
        require(normalizedAmount.length <= MAX_TARGET_AMOUNT_LENGTH) { "Habit target is too long" }
        require(normalizedUnit.length <= MAX_TARGET_UNIT_LENGTH) { "Habit target unit is too long" }
        return normalizedAmount to normalizedUnit
    }

    private fun HabitsSnapshot.visibleHabits(): List<Habit> =
        habits
            .asSequence()
            .filterNot(Habit::archived)
            .sortedBy(Habit::createdAtEpochMillis)
            .toList()

    private companion object {
        const val MAX_TITLE_LENGTH = 80
        const val MAX_TARGET_AMOUNT_LENGTH = 24
        const val MAX_TARGET_UNIT_LENGTH = 24
        const val MAX_REPEAT_INTERVAL_DAYS = 3650
    }
}
