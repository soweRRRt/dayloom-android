package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlannerSnapshot
import java.io.File

interface PlannerRepository {
    suspend fun loadPlans(): List<PlanItem>

    suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int? = null,
    ): List<PlanItem>

    suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int? = null,
    ): List<PlanItem>

    suspend fun toggleCompletion(id: EntityId): List<PlanItem>

    suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<PlanItem>

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
            currentSchemaVersion = 1,
            defaultValue = ::PlannerSnapshot,
        )

    override suspend fun loadPlans(): List<PlanItem> = store.read().sortedPlans()

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
            }.sortedPlans()
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

    override suspend fun toggleCompletion(id: EntityId): List<PlanItem> =
        updateExisting(id) { it.copy(completed = !it.completed) }

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<PlanItem> = updateExisting(id) { plan -> plan.copy(image = image) }

    override suspend fun deletePlan(id: EntityId): List<PlanItem> =
        store
            .update { snapshot -> snapshot.copy(plans = snapshot.plans.filterNot { it.id == id }) }
            .sortedPlans()

    private suspend fun updateExisting(
        id: EntityId,
        transform: (PlanItem) -> PlanItem,
    ): List<PlanItem> =
        store
            .update { snapshot ->
                require(snapshot.plans.any { it.id == id }) { "Plan does not exist" }
                snapshot.copy(plans = snapshot.plans.map { plan -> if (plan.id == id) transform(plan) else plan })
            }.sortedPlans()

    private fun normalize(title: String): String {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "Plan title must not be blank" }
        require(normalized.length <= MAX_TITLE_LENGTH) { "Plan title is too long" }
        return normalized
    }

    private fun validateReminder(reminderMinutesOfDay: Int?) {
        require(reminderMinutesOfDay == null || reminderMinutesOfDay in 0 until MINUTES_PER_DAY) {
            "Reminder time must be within a day"
        }
    }

    private fun PlannerSnapshot.sortedPlans(): List<PlanItem> =
        plans.sortedWith(compareBy(PlanItem::dateEpochDay, PlanItem::createdAtEpochMillis))

    private companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MINUTES_PER_DAY = 24 * 60
    }
}
