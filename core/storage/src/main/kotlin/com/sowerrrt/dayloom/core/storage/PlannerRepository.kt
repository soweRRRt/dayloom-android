package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.PlannerSnapshot
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.isRecurring
import java.io.File

interface PlannerRepository {
    suspend fun loadPlans(): List<PlanItem>

    suspend fun loadArchivedPlans(): List<PlanItem> = emptyList()

    suspend fun loadAllPlans(): List<PlanItem> = loadPlans()

    suspend fun replaceAll(plans: List<PlanItem>): List<PlanItem> =
        error("This planner repository does not support replacement")

    suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int? = null,
    ): List<PlanItem>

    suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long? = null,
    ): List<PlanItem> = createPlan(title, dateEpochDay, reminderMinutesOfDay)

    suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
    ): List<PlanItem> = createPlan(title, dateEpochDay, reminderMinutesOfDay, repeat, repeatUntilEpochDay)

    suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> =
        createPlan(title, dateEpochDay, reminderMinutesOfDay, repeat, repeatUntilEpochDay, reminderEnabled)

    suspend fun createPlanDetails(
        title: String,
        note: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> =
        createPlan(
            title,
            dateEpochDay,
            reminderMinutesOfDay,
            repeat,
            repeatUntilEpochDay,
            reminderEnabled,
            scheduledWeekdays,
            repeatEveryDays,
            scheduledMonthDays,
        )

    suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int? = null,
    ): List<PlanItem>

    suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long? = null,
    ): List<PlanItem> = updatePlan(id, title, dateEpochDay, reminderMinutesOfDay)

    suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
    ): List<PlanItem> = updatePlan(id, title, dateEpochDay, reminderMinutesOfDay, repeat, repeatUntilEpochDay)

    suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> =
        updatePlan(id, title, dateEpochDay, reminderMinutesOfDay, repeat, repeatUntilEpochDay, reminderEnabled)

    suspend fun updatePlanDetails(
        id: EntityId,
        title: String,
        note: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> =
        updatePlan(
            id,
            title,
            dateEpochDay,
            reminderMinutesOfDay,
            repeat,
            repeatUntilEpochDay,
            reminderEnabled,
            scheduledWeekdays,
            repeatEveryDays,
            scheduledMonthDays,
        )

    suspend fun toggleCompletion(id: EntityId): List<PlanItem>

    suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<PlanItem> = toggleCompletion(id)

    suspend fun movePlan(
        id: EntityId,
        dateEpochDay: Long,
    ): List<PlanItem> = error("This planner repository does not support moving plans")

    suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<PlanItem>

    suspend fun archivePlan(id: EntityId): List<PlanItem> =
        error("This planner repository does not support archiving plans")

    suspend fun restorePlan(id: EntityId): List<PlanItem> =
        error("This planner repository does not support restoring archived plans")

    suspend fun deletePlan(id: EntityId): List<PlanItem>
}

class FilePlannerRepository(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> EntityId = EntityId::random,
) : PlannerRepository {
    private val store =
        FileJsonStore(
            directory = directory,
            fileName = "planner.json",
            payloadSerializer = PlannerSnapshot.serializer(),
            currentSchemaVersion = 2,
            defaultValue = ::PlannerSnapshot,
            migrations =
                mapOf(
                    1 to
                        StorageMigration { envelope ->
                            kotlinx.serialization.json.JsonObject(
                                envelope +
                                    ("schemaVersion" to kotlinx.serialization.json.JsonPrimitive(2)),
                            )
                        },
                ),
        )

    override suspend fun loadPlans(): List<PlanItem> = store.read().activePlans()

    override suspend fun loadArchivedPlans(): List<PlanItem> = store.read().archivedPlans()

    override suspend fun loadAllPlans(): List<PlanItem> = store.read().plans.sortedPlans()

    override suspend fun replaceAll(plans: List<PlanItem>): List<PlanItem> {
        require(plans.map(PlanItem::id).distinct().size == plans.size) { "Plan IDs must be unique" }
        plans.forEach { plan ->
            normalize(plan.title)
            normalizeNote(plan.note)
            validateReminder(plan.reminderMinutesOfDay)
            validateRepeat(plan.dateEpochDay, plan.repeatUntilEpochDay)
            validateSchedule(plan.scheduledWeekdays, plan.repeatEveryDays, plan.scheduledMonthDays)
        }
        store.write(PlannerSnapshot(plans))
        return plans.filterNot(PlanItem::archived).sortedPlans()
    }

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<PlanItem> {
        val normalizedTitle = normalize(title)
        validateReminder(reminderMinutesOfDay)
        return store
            .update { snapshot ->
                snapshot.copy(
                    plans =
                        snapshot.plans +
                            PlanItem(
                                id = idFactory(),
                                title = normalizedTitle,
                                dateEpochDay = dateEpochDay,
                                createdAtEpochMillis = clock(),
                                reminderMinutesOfDay = reminderMinutesOfDay,
                            ),
                )
            }.activePlans()
    }

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
    ): List<PlanItem> =
        createPlan(
            title = title,
            dateEpochDay = dateEpochDay,
            reminderMinutesOfDay = reminderMinutesOfDay,
            repeat = repeat,
            repeatUntilEpochDay = repeatUntilEpochDay,
            reminderEnabled = reminderMinutesOfDay != null,
        )

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
    ): List<PlanItem> =
        createPlan(
            title = title,
            dateEpochDay = dateEpochDay,
            reminderMinutesOfDay = reminderMinutesOfDay,
            repeat = repeat,
            repeatUntilEpochDay = repeatUntilEpochDay,
            reminderEnabled = reminderEnabled,
            scheduledWeekdays = emptySet(),
            repeatEveryDays = null,
            scheduledMonthDays = emptySet(),
        )

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> {
        val normalizedTitle = normalize(title)
        validateReminder(reminderMinutesOfDay)
        validateRepeat(dateEpochDay, repeatUntilEpochDay)
        validateSchedule(scheduledWeekdays, repeatEveryDays, scheduledMonthDays)
        return store
            .update { snapshot ->
                snapshot.copy(
                    plans =
                        snapshot.plans +
                            PlanItem(
                                id = idFactory(),
                                title = normalizedTitle,
                                dateEpochDay = dateEpochDay,
                                createdAtEpochMillis = clock(),
                                reminderMinutesOfDay = reminderMinutesOfDay,
                                reminderEnabled = reminderEnabled && reminderMinutesOfDay != null,
                                repeat = repeat,
                                repeatUntilEpochDay = repeatUntilEpochDay,
                                scheduledWeekdays = scheduledWeekdays,
                                repeatEveryDays = repeatEveryDays,
                                scheduledMonthDays = scheduledMonthDays,
                            ),
                )
            }.activePlans()
    }

    override suspend fun createPlanDetails(
        title: String,
        note: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> {
        val normalizedTitle = normalize(title)
        val normalizedNote = normalizeNote(note)
        validateReminder(reminderMinutesOfDay)
        validateRepeat(dateEpochDay, repeatUntilEpochDay)
        validateSchedule(scheduledWeekdays, repeatEveryDays, scheduledMonthDays)
        return store
            .update { snapshot ->
                snapshot.copy(
                    plans =
                        snapshot.plans +
                            PlanItem(
                                id = idFactory(),
                                title = normalizedTitle,
                                note = normalizedNote,
                                dateEpochDay = dateEpochDay,
                                createdAtEpochMillis = clock(),
                                reminderMinutesOfDay = reminderMinutesOfDay,
                                reminderEnabled = reminderEnabled && reminderMinutesOfDay != null,
                                repeat = repeat,
                                repeatUntilEpochDay = repeatUntilEpochDay,
                                scheduledWeekdays = scheduledWeekdays,
                                repeatEveryDays = repeatEveryDays,
                                scheduledMonthDays = scheduledMonthDays,
                            ),
                )
            }.activePlans()
    }

    override suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<PlanItem> {
        val normalizedTitle = normalize(title)
        validateReminder(reminderMinutesOfDay)
        return updateExisting(id) {
            it.copy(
                title = normalizedTitle,
                dateEpochDay = dateEpochDay,
                reminderMinutesOfDay = reminderMinutesOfDay,
            )
        }
    }

    override suspend fun updatePlanDetails(
        id: EntityId,
        title: String,
        note: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> {
        val normalizedTitle = normalize(title)
        val normalizedNote = normalizeNote(note)
        validateReminder(reminderMinutesOfDay)
        validateRepeat(dateEpochDay, repeatUntilEpochDay)
        validateSchedule(scheduledWeekdays, repeatEveryDays, scheduledMonthDays)
        return updateExisting(id) {
            it.copy(
                title = normalizedTitle,
                note = normalizedNote,
                dateEpochDay = dateEpochDay,
                reminderMinutesOfDay = reminderMinutesOfDay,
                reminderEnabled = reminderEnabled && reminderMinutesOfDay != null,
                repeat = repeat,
                repeatUntilEpochDay = repeatUntilEpochDay,
                scheduledWeekdays = scheduledWeekdays,
                repeatEveryDays = repeatEveryDays,
                scheduledMonthDays = scheduledMonthDays,
            )
        }
    }

    override suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
    ): List<PlanItem> =
        updatePlan(
            id = id,
            title = title,
            dateEpochDay = dateEpochDay,
            reminderMinutesOfDay = reminderMinutesOfDay,
            repeat = repeat,
            repeatUntilEpochDay = repeatUntilEpochDay,
            reminderEnabled = reminderMinutesOfDay != null,
        )

    override suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
    ): List<PlanItem> =
        updatePlan(
            id = id,
            title = title,
            dateEpochDay = dateEpochDay,
            reminderMinutesOfDay = reminderMinutesOfDay,
            repeat = repeat,
            repeatUntilEpochDay = repeatUntilEpochDay,
            reminderEnabled = reminderEnabled,
            scheduledWeekdays = emptySet(),
            repeatEveryDays = null,
            scheduledMonthDays = emptySet(),
        )

    override suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> {
        val normalizedTitle = normalize(title)
        validateReminder(reminderMinutesOfDay)
        validateRepeat(dateEpochDay, repeatUntilEpochDay)
        validateSchedule(scheduledWeekdays, repeatEveryDays, scheduledMonthDays)
        return updateExisting(id) {
            it.copy(
                title = normalizedTitle,
                dateEpochDay = dateEpochDay,
                reminderMinutesOfDay = reminderMinutesOfDay,
                reminderEnabled = reminderEnabled && reminderMinutesOfDay != null,
                repeat = repeat,
                repeatUntilEpochDay = repeatUntilEpochDay,
                scheduledWeekdays = scheduledWeekdays,
                repeatEveryDays = repeatEveryDays,
                scheduledMonthDays = scheduledMonthDays,
            )
        }
    }

    override suspend fun toggleCompletion(id: EntityId): List<PlanItem> =
        updateExisting(id) { it.copy(completed = !it.completed) }

    override suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<PlanItem> =
        updateExisting(id) { plan ->
            if (!plan.isRecurring()) {
                plan.copy(completed = !plan.completed)
            } else {
                val days = plan.completedEpochDays.toMutableSet()
                if (!days.add(epochDay)) days.remove(epochDay)
                plan.copy(completedEpochDays = days)
            }
        }

    override suspend fun movePlan(
        id: EntityId,
        dateEpochDay: Long,
    ): List<PlanItem> = updateExisting(id) { it.copy(dateEpochDay = dateEpochDay) }

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<PlanItem> = updateExisting(id) { plan -> plan.copy(image = image) }

    override suspend fun archivePlan(id: EntityId): List<PlanItem> =
        updateExisting(id) { plan -> plan.copy(archived = true) }

    override suspend fun restorePlan(id: EntityId): List<PlanItem> =
        updateExisting(id) { plan -> plan.copy(archived = false) }

    override suspend fun deletePlan(id: EntityId): List<PlanItem> =
        store
            .update { snapshot -> snapshot.copy(plans = snapshot.plans.filterNot { it.id == id }) }
            .activePlans()

    private suspend fun updateExisting(
        id: EntityId,
        transform: (PlanItem) -> PlanItem,
    ): List<PlanItem> =
        store
            .update { snapshot ->
                require(snapshot.plans.any { it.id == id }) { "Plan does not exist" }
                snapshot.copy(plans = snapshot.plans.map { plan -> if (plan.id == id) transform(plan) else plan })
            }.activePlans()

    private fun normalize(title: String): String {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "Plan title must not be blank" }
        require(normalized.length <= MAX_TITLE_LENGTH) { "Plan title is too long" }
        return normalized
    }

    private fun normalizeNote(note: String): String {
        val normalized = note.trim()
        require(normalized.length <= MAX_NOTE_LENGTH) { "Plan note is too long" }
        return normalized
    }

    private fun validateReminder(reminderMinutesOfDay: Int?) {
        require(reminderMinutesOfDay == null || reminderMinutesOfDay in 0 until MINUTES_PER_DAY) {
            "Reminder time must be within a day"
        }
    }

    private fun validateRepeat(
        dateEpochDay: Long,
        repeatUntilEpochDay: Long?,
    ) {
        require(repeatUntilEpochDay == null || repeatUntilEpochDay >= dateEpochDay) {
            "Repeat end must not precede the start date"
        }
    }

    private fun validateSchedule(
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ) {
        val configuredModes =
            listOf(
                scheduledWeekdays.isNotEmpty(),
                repeatEveryDays != null,
                scheduledMonthDays.isNotEmpty(),
            ).count { it }
        require(configuredModes <= 1) { "Only one advanced plan schedule can be active" }
        require(repeatEveryDays == null || repeatEveryDays in 1..MAX_REPEAT_INTERVAL_DAYS) {
            "Plan repeat interval is out of range"
        }
        require(scheduledMonthDays.all { it in 1..31 }) { "Plan month days are out of range" }
    }

    private fun PlannerSnapshot.activePlans(): List<PlanItem> = plans.filterNot(PlanItem::archived).sortedPlans()

    private fun PlannerSnapshot.archivedPlans(): List<PlanItem> =
        plans.filter(PlanItem::archived).sortedByDescending(PlanItem::createdAtEpochMillis)

    private fun List<PlanItem>.sortedPlans(): List<PlanItem> =
        sortedWith(compareBy(PlanItem::dateEpochDay, PlanItem::createdAtEpochMillis))

    private companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_NOTE_LENGTH = 1000
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_REPEAT_INTERVAL_DAYS = 3650
    }
}
