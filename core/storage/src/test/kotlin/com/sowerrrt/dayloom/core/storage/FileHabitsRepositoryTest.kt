package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Weekday
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                    .createHabit(
                        "  Read ten pages  ",
                        Weekday.entries.toSet(),
                        TEST_EPOCH_DAY,
                        8 * 60 + 15,
                        "20",
                        "pages",
                    ).single()
            assertEquals("Read ten pages", created.title)
            assertFalse(TEST_EPOCH_DAY in created.completedEpochDays)

            firstRepository.toggleCompletion(id, TEST_EPOCH_DAY)
            val image = AttachmentRef(EntityId("image-1"), "habit.png", "image/png")
            firstRepository.setImage(id, image)

            val restored = FileHabitsRepository(directory).loadHabits().single()
            assertEquals(id, restored.id)
            assertEquals(8 * 60 + 15, restored.reminderMinutesOfDay)
            assertEquals("20", restored.targetAmount)
            assertEquals("pages", restored.targetUnit)
            assertTrue(TEST_EPOCH_DAY in restored.completedEpochDays)
            assertEquals(image, restored.image)
        }

    @Test
    fun `interval schedule survives repository recreation`() =
        runTest {
            val directory = temporaryFolder.newFolder("interval")
            FileHabitsRepository(directory).createHabit(
                title = "Water plants",
                scheduledWeekdays = emptySet(),
                startEpochDay = TEST_EPOCH_DAY,
                repeatEveryDays = 10,
                reminderMinutesOfDay = 5 * 60,
            )

            val restored = FileHabitsRepository(directory).loadHabits().single()

            assertEquals(10, restored.repeatEveryDays)
            assertEquals(5 * 60, restored.reminderMinutesOfDay)
        }

    @Test
    fun `monthly schedule survives repository recreation`() =
        runTest {
            val directory = temporaryFolder.newFolder("monthly")
            FileHabitsRepository(directory).createHabit(
                title = "Review budget",
                scheduledWeekdays = emptySet(),
                startEpochDay = TEST_EPOCH_DAY,
                scheduledMonthDays = setOf(3, 15),
            )

            assertEquals(setOf(3, 15), FileHabitsRepository(directory).loadHabits().single().scheduledMonthDays)
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
    fun `numeric progress completes target and survives recreation`() =
        runTest {
            val directory = temporaryFolder.newFolder("progress")
            val id = EntityId("habit-progress")
            val repository = FileHabitsRepository(directory, idFactory = { id })
            repository.createHabit(
                title = "Walk",
                scheduledWeekdays = Weekday.entries.toSet(),
                startEpochDay = TEST_EPOCH_DAY,
                targetAmount = "10000",
                targetUnit = "steps",
            )

            val partial = repository.setProgress(id, TEST_EPOCH_DAY, "7500").single()
            assertEquals("7500", partial.progressByEpochDay[TEST_EPOCH_DAY])
            assertFalse(TEST_EPOCH_DAY in partial.completedEpochDays)

            repository.setProgress(id, TEST_EPOCH_DAY, "10000")
            val restored = FileHabitsRepository(directory).loadHabits().single()
            assertEquals("10000", restored.progressByEpochDay[TEST_EPOCH_DAY])
            assertTrue(TEST_EPOCH_DAY in restored.completedEpochDays)
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
            val archived = FileHabitsRepository(directory).loadArchivedHabits().single()
            assertEquals("Morning run", archived.title)
            assertEquals("run.png", archived.image?.displayName)
            assertTrue(archived.archivedAtEpochMillis != null)

            val restored = FileHabitsRepository(directory).restoreHabit(id).single()
            assertEquals("Morning run", restored.title)
            assertEquals("run.png", restored.image?.displayName)
            assertEquals(null, restored.archivedAtEpochMillis)
            assertTrue(FileHabitsRepository(directory).loadArchivedHabits().isEmpty())
        }

    @Test
    fun `expired habits are deleted independently after seven days`() =
        runTest {
            val directory = temporaryFolder.newFolder("archive-retention")
            var now = 1_000L
            val ids = ArrayDeque(listOf(EntityId("old-habit"), EntityId("recent-habit")))
            val deletedAttachments = mutableListOf<EntityId>()
            val repository =
                FileHabitsRepository(
                    directory = directory,
                    clock = { now },
                    idFactory = { ids.removeFirst() },
                    deleteAttachment = { id ->
                        deletedAttachments += id
                        true
                    },
                )
            val oldImage = AttachmentRef(EntityId("old-image"), "old.png", "image/png")
            val recentImage = AttachmentRef(EntityId("recent-image"), "recent.png", "image/png")

            repository.createHabit("Old", Weekday.entries.toSet(), TEST_EPOCH_DAY)
            repository.setImage(EntityId("old-habit"), oldImage)
            repository.archiveHabit(EntityId("old-habit"))

            now += DAY_MILLIS
            repository.createHabit("Recent", Weekday.entries.toSet(), TEST_EPOCH_DAY)
            repository.setImage(EntityId("recent-habit"), recentImage)
            repository.archiveHabit(EntityId("recent-habit"))

            now = 1_000L + ARCHIVE_RETENTION_MILLIS
            assertEquals(listOf("Recent"), repository.loadArchivedHabits().map { it.title })
            assertEquals(listOf(oldImage.id), deletedAttachments)
            assertFalse(directory.resolve("habits.json.bak").readText().contains("old-habit"))

            now += DAY_MILLIS
            assertTrue(repository.loadArchivedHabits().isEmpty())
            assertEquals(listOf(oldImage.id, recentImage.id), deletedAttachments)
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
            assertEquals(
                4,
                Json
                    .parseToJsonElement(directory.resolve("habits.json").readText())
                    .jsonObject["schemaVersion"]
                    ?.jsonPrimitive
                    ?.int,
            )
        }

    @Test
    fun `legacy archived habit receives a fresh seven day retention period`() =
        runTest {
            val directory = temporaryFolder.newFolder("archive-migration")
            var now = 50_000L
            directory.resolve("habits.json").writeText(
                """
                {
                  "schemaVersion": 3,
                  "updatedAtEpochMillis": 123,
                  "payload": {"habits": [{
                    "id": "legacy-archive",
                    "title": "Legacy archive",
                    "createdAtEpochMillis": 100,
                    "archived": true
                  }]}
                }
                """.trimIndent(),
            )
            val repository = FileHabitsRepository(directory, clock = { now })

            assertEquals(now, repository.loadArchivedHabits().single().archivedAtEpochMillis)
            now += ARCHIVE_RETENTION_MILLIS - 1
            assertEquals(1, repository.loadArchivedHabits().size)
            now += 1
            assertTrue(repository.loadArchivedHabits().isEmpty())
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
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val ARCHIVE_RETENTION_MILLIS = 7L * DAY_MILLIS
    }
}
