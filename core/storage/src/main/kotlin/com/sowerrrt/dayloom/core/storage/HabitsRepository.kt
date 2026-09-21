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

private const val ARCHIVE_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

interface HabitsRepository {
    suspend fun loadHabits(): List<Habit>

    suspend fun loadArchivedHabits(): List<Habit> = emptyList()

    suspend fun loadAllHabits(): List<Habit> = loadHabits() + loadArchivedHabits()

    suspend fun replaceAll(habits: List<Habit>): List<Habit> =
        error("This habits repository does not support replacement")

    suspend fun createHabit(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        startEpochDay: Long,
        reminderMinutesOfDay: Int? = null,
        targetAmount: String = "",
        targetUnit: String = "",
        repeatEveryDays: Int? = null,
        scheduledMonthDays: Set<Int> = emptySet(),
        reminderOffsetsMinutes: Set<Int> = setOf(0),
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
        reminderOffsetsMinutes: Set<Int> = setOf(0),
    ): List<Habit>

    suspend fun archiveHabit(id: EntityId): List<Habit>

    suspend fun restoreHabit(id: EntityId): List<Habit> =
        error("This habits repository does not support restoring archived habits")

    suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<Habit>

    suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<Habit>

    suspend fun setProgress(
        id: EntityId,
        epochDay: Long,
        progress: String,
    ): List<Habit> = error("This habits repository does not support progress")
}

class FileHabitsRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> EntityId = EntityId::random,
    private val deleteAttachment: suspend (EntityId) -> Boolean = { true },
) : HabitsRepository {
    private val store =
        FileJsonStore(
            directory = directory,
            fileName = "habits.json",
            payloadSerializer = HabitsSnapshot.serializer(),
            currentSchemaVersion = 4,
            defaultValue = ::HabitsSnapshot,
            clock = clock,
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
                    2 to
                        StorageMigration { envelope ->
                            JsonObject(envelope + ("schemaVersion" to JsonPrimitive(3)))
                        },
                    3 to
                        StorageMigration { envelope ->
                            val payload = requireNotNull(envelope["payload"]).jsonObject
                            val migratedHabits =
                                requireNotNull(payload["habits"]).jsonArray.map { element ->
                                    val habit = element.jsonObject
                                    if (
                                        habit["archived"]?.jsonPrimitive?.content == "true" &&
                                        "archivedAtEpochMillis" !in habit
                                    ) {
                                        JsonObject(habit + ("archivedAtEpochMillis" to JsonPrimitive(clock())))
                                    } else {
                                        habit
                                    }
                                }
                            JsonObject(
                                envelope +
                                    ("schemaVersion" to JsonPrimitive(4)) +
                                    ("payload" to JsonObject(payload + ("habits" to JsonArray(migratedHabits)))),
                            )
                        },
                ),
        )

    override suspend fun loadHabits(): List<Habit> = readWithoutExpiredArchives().visibleHabits()

    override suspend fun loadArchivedHabits(): List<Habit> = readWithoutExpiredArchives().archivedHabits()

    override suspend fun loadAllHabits(): List<Habit> =
        readWithoutExpiredArchives().habits.sortedBy(Habit::createdAtEpochMillis)

    override suspend fun replaceAll(habits: List<Habit>): List<Habit> {
        require(habits.map(Habit::id).distinct().size == habits.size) { "Habit IDs must be unique" }
        habits.forEach { habit ->
            validate(
                habit.title.trim(),
                habit.scheduledWeekdays,
                habit.repeatEveryDays,
                habit.scheduledMonthDays,
                habit.reminderMinutesOfDay,
            )
            validateReminderOffsets(habit.reminderOffsetsMinutes)
            normalizeTarget(habit.targetAmount, habit.targetUnit)
            require(habit.progressByEpochDay.values.all { it.length <= MAX_TARGET_AMOUNT_LENGTH }) {
                "Habit progress is too long"
            }
        }
        val now = clock()
        val normalized =
            habits
                .map { habit ->
                    when {
                        !habit.archived -> habit.copy(archivedAtEpochMillis = null)
                        habit.archivedAtEpochMillis == null -> habit.copy(archivedAtEpochMillis = now)
                        else -> habit
                    }
                }
        store.write(HabitsSnapshot(normalized))
        return readWithoutExpiredArchives().visibleHabits()
    }

    override suspend fun createHabit(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        startEpochDay: Long,
        reminderMinutesOfDay: Int?,
        targetAmount: String,
        targetUnit: String,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
        reminderOffsetsMinutes: Set<Int>,
    ): List<Habit> {
        purgeExpiredArchives()
        val normalizedTitle = title.trim()
        val target = normalizeTarget(targetAmount, targetUnit)
        validate(normalizedTitle, scheduledWeekdays, repeatEveryDays, scheduledMonthDays, reminderMinutesOfDay)
        validateReminderOffsets(reminderOffsetsMinutes)
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
                                reminderOffsetsMinutes = reminderOffsetsMinutes,
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
        reminderOffsetsMinutes: Set<Int>,
    ): List<Habit> {
        val normalizedTitle = title.trim()
        val target = normalizeTarget(targetAmount, targetUnit)
        validate(normalizedTitle, scheduledWeekdays, repeatEveryDays, scheduledMonthDays, reminderMinutesOfDay)
        validateReminderOffsets(reminderOffsetsMinutes)
        return updateExisting(id) { habit ->
            habit.copy(
                title = normalizedTitle,
                scheduledWeekdays = scheduledWeekdays,
                repeatEveryDays = repeatEveryDays,
                scheduledMonthDays = scheduledMonthDays,
                reminderMinutesOfDay = reminderMinutesOfDay,
                reminderOffsetsMinutes = reminderOffsetsMinutes,
                targetAmount = target.first,
                targetUnit = target.second,
            )
        }
    }

    override suspend fun archiveHabit(id: EntityId): List<Habit> =
        updateExisting(id) { habit ->
            habit.copy(
                archived = true,
                archivedAtEpochMillis = habit.archivedAtEpochMillis ?: clock(),
            )
        }

    override suspend fun restoreHabit(id: EntityId): List<Habit> =
        updateExisting(id) { habit -> habit.copy(archived = false, archivedAtEpochMillis = null) }

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

    override suspend fun setProgress(
        id: EntityId,
        epochDay: Long,
        progress: String,
    ): List<Habit> {
        val normalized = progress.trim()
        require(normalized.length <= MAX_TARGET_AMOUNT_LENGTH) { "Habit progress is too long" }
        return updateExisting(id) { habit ->
            val values = habit.progressByEpochDay.toMutableMap()
            if (normalized.isEmpty()) values.remove(epochDay) else values[epochDay] = normalized
            val completed = habit.completedEpochDays.toMutableSet()
            val currentNumber = normalized.toComparableNumberOrNull()
            val targetNumber = habit.targetAmount.toComparableNumberOrNull()
            if (targetNumber != null) {
                if (currentNumber != null && currentNumber >= targetNumber) {
                    completed.add(epochDay)
                } else {
                    completed.remove(epochDay)
                }
            }
            habit.copy(progressByEpochDay = values, completedEpochDays = completed)
        }
    }

    private suspend fun updateExisting(
        id: EntityId,
        transform: (Habit) -> Habit,
    ): List<Habit> {
        purgeExpiredArchives()
        return store
            .update { snapshot ->
                require(snapshot.habits.any { it.id == id }) { "Habit does not exist" }
                snapshot.copy(
                    habits = snapshot.habits.map { habit -> if (habit.id == id) transform(habit) else habit },
                )
            }.visibleHabits()
    }

    private suspend fun readWithoutExpiredArchives(): HabitsSnapshot = purgeExpiredArchives()

    private suspend fun purgeExpiredArchives(): HabitsSnapshot {
        val now = clock()
        val current = store.read()
        if (current.habits.none { it.archiveExpired(now) }) return current
        var removedAny = false
        var removedAttachments = emptyList<AttachmentRef>()
        val updated =
            store.update { snapshot ->
                val expired = snapshot.habits.filter { it.archiveExpired(now) }
                removedAny = expired.isNotEmpty()
                removedAttachments = expired.mapNotNull(Habit::image)
                snapshot.copy(habits = snapshot.habits.filterNot { it.archiveExpired(now) })
            }
        if (removedAny) store.write(updated)
        val retainedAttachmentIds =
            updated.habits
                .mapNotNull(Habit::image)
                .map(AttachmentRef::id)
                .toSet()
        removedAttachments
            .distinctBy(AttachmentRef::id)
            .filterNot { it.id in retainedAttachmentIds }
            .forEach { attachment -> runCatching { deleteAttachment(attachment.id) } }
        return updated
    }

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

    private fun validateReminderOffsets(offsets: Set<Int>) {
        require(offsets.size <= MAX_REMINDER_OFFSETS) { "Too many reminder offsets" }
        require(offsets.all { it in 0..MAX_REMINDER_OFFSET_MINUTES }) { "Reminder offset is out of range" }
    }

    private fun HabitsSnapshot.visibleHabits(): List<Habit> =
        habits
            .asSequence()
            .filterNot(Habit::archived)
            .sortedBy(Habit::createdAtEpochMillis)
            .toList()

    private fun HabitsSnapshot.archivedHabits(): List<Habit> =
        habits
            .asSequence()
            .filter(Habit::archived)
            .sortedByDescending(Habit::createdAtEpochMillis)
            .toList()

    private fun List<Habit>.visibleHabits(): List<Habit> =
        asSequence().filterNot(Habit::archived).sortedBy(Habit::createdAtEpochMillis).toList()

    private companion object {
        const val MAX_TITLE_LENGTH = 80
        const val MAX_TARGET_AMOUNT_LENGTH = 24
        const val MAX_TARGET_UNIT_LENGTH = 24
        const val MAX_REPEAT_INTERVAL_DAYS = 3650
        const val MAX_REMINDER_OFFSETS = 24
        const val MAX_REMINDER_OFFSET_MINUTES = 7 * 24 * 60
    }
}

private fun Habit.archiveExpired(nowEpochMillis: Long): Boolean =
    archived && archivedAtEpochMillis?.let { nowEpochMillis - it >= ARCHIVE_RETENTION_MILLIS } == true

private fun String.toComparableNumberOrNull(): Double? = replace(',', '.').toDoubleOrNull()
