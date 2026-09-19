package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.EntityId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileHabitsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `habit and completion survive repository recreation`() =
        runTest {
            val directory = temporaryFolder.newFolder("habits")
            val id = EntityId("habit-1")
            val firstRepository =
                FileHabitsRepository(
                    directory = directory,
                    clock = { 123L },
                    idFactory = { id },
                )

            val created = firstRepository.createHabit("  Read ten pages  ").single()
            assertEquals("Read ten pages", created.title)
            assertFalse(TEST_EPOCH_DAY in created.completedEpochDays)

            firstRepository.toggleCompletion(id, TEST_EPOCH_DAY)

            val restored = FileHabitsRepository(directory).loadHabits().single()
            assertEquals(id, restored.id)
            assertTrue(TEST_EPOCH_DAY in restored.completedEpochDays)
        }

    @Test
    fun `toggling a completed day removes it`() =
        runTest {
            val id = EntityId("habit-2")
            val repository =
                FileHabitsRepository(
                    directory = temporaryFolder.newFolder("toggle"),
                    idFactory = { id },
                )

            repository.createHabit("Stretch")
            repository.toggleCompletion(id, TEST_EPOCH_DAY)
            val habit = repository.toggleCompletion(id, TEST_EPOCH_DAY).single()

            assertFalse(TEST_EPOCH_DAY in habit.completedEpochDays)
        }

    private companion object {
        const val TEST_EPOCH_DAY = 20_000L
    }
}
