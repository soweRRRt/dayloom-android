package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.HabitsSnapshot
import java.io.File

interface HabitsRepository {
    suspend fun loadHabits(): List<Habit>

    suspend fun createHabit(title: String): List<Habit>

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
            currentSchemaVersion = 1,
            defaultValue = ::HabitsSnapshot,
        )

    override suspend fun loadHabits(): List<Habit> = store.read().visibleHabits()

    override suspend fun createHabit(title: String): List<Habit> {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Habit title must not be blank" }
        require(normalizedTitle.length <= MAX_TITLE_LENGTH) { "Habit title is too long" }
        return store
            .update { snapshot ->
                snapshot.copy(
                    habits =
                        snapshot.habits +
                            Habit(
                                id = idFactory(),
                                title = normalizedTitle,
                                createdAtEpochMillis = clock(),
                            ),
                )
            }.visibleHabits()
    }

    override suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<Habit> =
        store
            .update { snapshot ->
                require(snapshot.habits.any { it.id == id }) { "Habit does not exist" }
                snapshot.copy(
                    habits =
                        snapshot.habits.map { habit ->
                            if (habit.id != id) {
                                habit
                            } else {
                                val days = habit.completedEpochDays.toMutableSet()
                                if (!days.add(epochDay)) days.remove(epochDay)
                                habit.copy(completedEpochDays = days)
                            }
                        },
                )
            }.visibleHabits()

    private fun HabitsSnapshot.visibleHabits(): List<Habit> =
        habits
            .asSequence()
            .filterNot(Habit::archived)
            .sortedBy(Habit::createdAtEpochMillis)
            .toList()

    private companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}
