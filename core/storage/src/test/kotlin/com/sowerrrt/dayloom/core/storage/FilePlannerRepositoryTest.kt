package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.isCompletedOn
import com.sowerrrt.dayloom.core.model.occursOn
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

class FilePlannerRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `plans can be created completed edited and restored`() =
        runTest {
            val directory = temporaryFolder.newFolder("planner")
            val id = EntityId("plan-1")
            val repository =
                FilePlannerRepository(
                    directory = directory,
                    clock = { 42L },
                    idFactory = { id },
                )

            repository.createPlan("  Call the dentist  ", TEST_EPOCH_DAY, 9 * 60 + 30)
            repository.toggleCompletion(id)
            repository.updatePlan(id, "Dentist appointment", TEST_EPOCH_DAY + 1, 14 * 60)
            val image = AttachmentRef(EntityId("image-1"), "dentist.png", "image/png")
            repository.setImage(id, image)

            val restored = FilePlannerRepository(directory).loadPlans().single()
            assertEquals("Dentist appointment", restored.title)
            assertEquals(TEST_EPOCH_DAY + 1, restored.dateEpochDay)
            assertEquals(14 * 60, restored.reminderMinutesOfDay)
            assertTrue(restored.completed)
            assertEquals(image, restored.image)
        }

    @Test
    fun `plan can be deleted`() =
        runTest {
            val id = EntityId("plan-2")
            val repository =
                FilePlannerRepository(
                    directory = temporaryFolder.newFolder("delete"),
                    idFactory = { id },
                )
            repository.createPlan("Temporary", TEST_EPOCH_DAY)

            assertTrue(repository.deletePlan(id).isEmpty())
        }

    @Test
    fun `recurring plan tracks each occurrence independently and can be moved`() =
        runTest {
            val directory = temporaryFolder.newFolder("recurring")
            val id = EntityId("plan-repeat")
            val repository = FilePlannerRepository(directory, idFactory = { id })

            repository.createPlan("Weekly review", TEST_EPOCH_DAY, 10 * 60, PlanRepeat.WEEKLY)
            repository.toggleCompletion(id, TEST_EPOCH_DAY)
            repository.movePlan(id, TEST_EPOCH_DAY + 1)

            val restored = FilePlannerRepository(directory).loadPlans().single()
            assertTrue(restored.occursOn(TEST_EPOCH_DAY + 1))
            assertTrue(restored.occursOn(TEST_EPOCH_DAY + 8))
            assertTrue(restored.isCompletedOn(TEST_EPOCH_DAY))
            assertTrue(!restored.isCompletedOn(TEST_EPOCH_DAY + 8))
        }

    @Test
    fun `plan time can be stored with notification disabled`() =
        runTest {
            val directory = temporaryFolder.newFolder("silent-time")
            val repository = FilePlannerRepository(directory, idFactory = { EntityId("silent-plan") })

            repository.createPlan(
                title = "Deep work",
                dateEpochDay = TEST_EPOCH_DAY,
                reminderMinutesOfDay = 9 * 60,
                repeat = PlanRepeat.NONE,
                repeatUntilEpochDay = null,
                reminderEnabled = false,
            )

            val restored = FilePlannerRepository(directory).loadPlans().single()
            assertEquals(9 * 60, restored.reminderMinutesOfDay)
            assertFalse(restored.reminderEnabled)
        }

    @Test
    fun `advanced plan schedule is stored and restored`() =
        runTest {
            val directory = temporaryFolder.newFolder("advanced-schedule")
            val repository = FilePlannerRepository(directory, idFactory = { EntityId("advanced-plan") })

            repository.createPlan(
                title = "Training",
                dateEpochDay = TEST_EPOCH_DAY,
                reminderMinutesOfDay = 6 * 60,
                repeat = PlanRepeat.NONE,
                repeatUntilEpochDay = null,
                reminderEnabled = false,
                scheduledWeekdays = setOf(Weekday.MONDAY, Weekday.FRIDAY),
                repeatEveryDays = null,
                scheduledMonthDays = emptySet(),
            )

            val restored = FilePlannerRepository(directory).loadPlans().single()
            assertEquals(setOf(Weekday.MONDAY, Weekday.FRIDAY), restored.scheduledWeekdays)
            assertEquals(6 * 60, restored.reminderMinutesOfDay)
            assertFalse(restored.reminderEnabled)
        }

    @Test
    fun `plan archive preserves note attachment and schedule and can be restored`() =
        runTest {
            val directory = temporaryFolder.newFolder("archive")
            val id = EntityId("archived-plan")
            val repository = FilePlannerRepository(directory, idFactory = { id })
            val image = AttachmentRef(EntityId("archive-image"), "receipt.png", "image/png")

            repository.createPlanDetails(
                title = "Renew insurance",
                note = "Policy number is in the blue folder",
                dateEpochDay = TEST_EPOCH_DAY,
                reminderMinutesOfDay = 11 * 60,
                repeat = PlanRepeat.NONE,
                repeatUntilEpochDay = null,
                reminderEnabled = true,
                scheduledWeekdays = setOf(Weekday.MONDAY),
                repeatEveryDays = null,
                scheduledMonthDays = emptySet(),
            )
            repository.setImage(id, image)

            assertTrue(repository.archivePlan(id).isEmpty())
            val archived = FilePlannerRepository(directory).loadArchivedPlans().single()
            assertEquals("Policy number is in the blue folder", archived.note)
            assertEquals(image, archived.image)
            assertEquals(setOf(Weekday.MONDAY), archived.scheduledWeekdays)
            assertTrue(archived.archived)
            assertEquals(archived, FilePlannerRepository(directory).loadAllPlans().single())

            val restored = repository.restorePlan(id).single()
            assertFalse(restored.archived)
            assertEquals(image, restored.image)
            assertEquals("Policy number is in the blue folder", restored.note)
        }

    @Test
    fun `plan accepts only one advanced schedule mode`() =
        runTest {
            val repository = FilePlannerRepository(temporaryFolder.newFolder("invalid-schedule"))

            var failure: Throwable? = null
            try {
                repository.createPlan(
                    title = "Invalid",
                    dateEpochDay = TEST_EPOCH_DAY,
                    reminderMinutesOfDay = null,
                    repeat = PlanRepeat.NONE,
                    repeatUntilEpochDay = null,
                    reminderEnabled = false,
                    scheduledWeekdays = setOf(Weekday.MONDAY),
                    repeatEveryDays = 10,
                    scheduledMonthDays = emptySet(),
                )
            } catch (error: Throwable) {
                failure = error
            }

            assertTrue(failure is IllegalArgumentException)
        }

    @Test
    fun `schema one plan is upgraded without losing completion`() =
        runTest {
            val directory = temporaryFolder.newFolder("migration")
            directory.resolve("planner.json").writeText(
                """
                {
                  "schemaVersion": 1,
                  "updatedAtEpochMillis": 123,
                  "payload": {"plans": [{
                    "id": "legacy-plan",
                    "title": "Legacy plan",
                    "dateEpochDay": 21000,
                    "createdAtEpochMillis": 100,
                    "reminderMinutesOfDay": 600,
                    "completed": true
                  }]}
                }
                """.trimIndent(),
            )

            val restored = FilePlannerRepository(directory).loadPlans().single()

            assertEquals(PlanRepeat.NONE, restored.repeat)
            assertTrue(restored.completed)
            assertEquals(600, restored.reminderMinutesOfDay)
            assertTrue(restored.reminderEnabled)
            assertEquals(
                2,
                Json
                    .parseToJsonElement(directory.resolve("planner.json").readText())
                    .jsonObject["schemaVersion"]
                    ?.jsonPrimitive
                    ?.int,
            )
        }

    @Test
    fun `reminder must be within the selected day`() =
        runTest {
            val repository = FilePlannerRepository(temporaryFolder.newFolder("invalid-reminder"))

            var failure: Throwable? = null
            try {
                repository.createPlan("Invalid", TEST_EPOCH_DAY, 24 * 60)
            } catch (error: Throwable) {
                failure = error
            }
            assertTrue(failure is IllegalArgumentException)
        }

    private companion object {
        const val TEST_EPOCH_DAY = 21_000L
    }
}
