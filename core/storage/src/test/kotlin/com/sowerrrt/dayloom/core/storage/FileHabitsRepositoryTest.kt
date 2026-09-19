package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Weekday
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneId

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

            val created =
                firstRepository
                    .createHabit("  Read ten pages  ", Weekday.entries.toSet(), TEST_EPOCH_DAY, 8 * 60 + 15)
                    .single()
            assertEquals("Read ten pages", created.title)
            assertFalse(TEST_EPOCH_DAY in created.completedEpochDays)

            firstRepository.toggleCompletion(id, TEST_EPOCH_DAY)
            val image = AttachmentRef(EntityId("image-1"), "habit.png", "image/png")
            firstRepository.setImage(id, image)

            val restored = FileHabitsRepository(directory).loadHabits().single()
            assertEquals(id, restored.id)
            assertEquals(8 * 60 + 15, restored.reminderMinutesOfDay)
            assertTrue(TEST_EPOCH_DAY in restored.completedEpochDays)
            assertEquals(image, restored.image)
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

            repository.createHabit("Stretch", Weekday.entries.toSet(), TEST_EPOCH_DAY)
            repository.toggleCompletion(id, TEST_EPOCH_DAY)
            val habit = repository.toggleCompletion(id, TEST_EPOCH_DAY).single()

            assertFalse(TEST_EPOCH_DAY in habit.completedEpochDays)
        }

    @Test
    fun `habit can be edited and archived without deleting stored data`() =
        runTest {
            val directory = temporaryFolder.newFolder("editing")
            val id = EntityId("habit-3")
            val repository = FileHabitsRepository(directory, idFactory = { id })
            repository.createHabit("Run", setOf(Weekday.MONDAY), TEST_EPOCH_DAY)
            repository.setImage(id, AttachmentRef(EntityId("image-2"), "run.png", "image/png"))

            val edited = repository.updateHabit(id, "Morning run", setOf(Weekday.MONDAY, Weekday.FRIDAY)).single()
            assertEquals("Morning run", edited.title)
            assertEquals(setOf(Weekday.MONDAY, Weekday.FRIDAY), edited.scheduledWeekdays)

            assertTrue(repository.archiveHabit(id).isEmpty())
            assertTrue(FileHabitsRepository(directory).loadHabits().isEmpty())
        }

    @Test
    fun `schema one habits receive the default daily schedule`() =
        runTest {
            val directory = temporaryFolder.newFolder("migration")
            directory.resolve("habits.json").writeText(
                """
                {
                  "schemaVersion": 1,
                  "updatedAtEpochMillis": 123,
                  "payload": {
                    "habits": [{
                      "id": "legacy-habit",
                      "title": "Legacy habit",
                      "createdAtEpochMillis": 100,
                      "completedEpochDays": [],
                      "archived": false
                    }]
                  }
                }
                """.trimIndent(),
            )

            val restored = FileHabitsRepository(directory).loadHabits().single()

            val expectedStartDay =
                Instant
                    .ofEpochMilli(100L)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toEpochDay()
            assertEquals(expectedStartDay, restored.startEpochDay)
            assertEquals(Weekday.entries.toSet(), restored.scheduledWeekdays)
            assertEquals(null, restored.reminderMinutesOfDay)
        }

    @Test
    fun `habit reminder must be within a day`() =
        runTest {
            val repository = FileHabitsRepository(temporaryFolder.newFolder("invalid-reminder"))

            var failure: Throwable? = null
            try {
                repository.createHabit("Invalid", Weekday.entries.toSet(), TEST_EPOCH_DAY, 24 * 60)
            } catch (error: Throwable) {
                failure = error
            }
            assertTrue(failure is IllegalArgumentException)
        }

    private companion object {
        const val TEST_EPOCH_DAY = 20_000L
    }
}
