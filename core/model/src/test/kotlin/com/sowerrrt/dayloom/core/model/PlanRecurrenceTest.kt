package com.sowerrrt.dayloom.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanRecurrenceTest {
    @Test
    fun `monthly plan occurs on matching day of month`() {
        val start = LocalDate.of(2026, 1, 15).toEpochDay()
        val plan = plan(start, PlanRepeat.MONTHLY)

        assertTrue(plan.occursOn(LocalDate.of(2026, 2, 15).toEpochDay()))
        assertFalse(plan.occursOn(LocalDate.of(2026, 2, 14).toEpochDay()))
    }

    @Test
    fun `recurring completion belongs only to selected occurrence`() {
        val start = LocalDate.of(2026, 1, 1).toEpochDay()
        val plan = plan(start, PlanRepeat.DAILY).copy(completedEpochDays = setOf(start + 1))

        assertFalse(plan.isCompletedOn(start))
        assertTrue(plan.isCompletedOn(start + 1))
    }

    @Test
    fun `weekday schedule supports several selected days`() {
        val monday = LocalDate.of(2026, 9, 7).toEpochDay()
        val plan =
            plan(monday, PlanRepeat.NONE).copy(
                scheduledWeekdays = setOf(Weekday.MONDAY, Weekday.FRIDAY),
            )

        assertTrue(plan.occursOn(monday))
        assertTrue(plan.occursOn(monday + 4))
        assertFalse(plan.occursOn(monday + 1))
        assertTrue(plan.occursOn(monday + 7))
    }

    @Test
    fun `interval schedule repeats from start date`() {
        val start = LocalDate.of(2026, 9, 1).toEpochDay()
        val plan = plan(start, PlanRepeat.NONE).copy(repeatEveryDays = 10)

        assertTrue(plan.occursOn(start))
        assertFalse(plan.occursOn(start + 9))
        assertTrue(plan.occursOn(start + 10))
        assertTrue(plan.occursOn(start + 20))
    }

    @Test
    fun `month dates skip missing dates and resume next month`() {
        val start = LocalDate.of(2026, 1, 3).toEpochDay()
        val plan = plan(start, PlanRepeat.NONE).copy(scheduledMonthDays = setOf(3, 15, 31))

        assertTrue(plan.occursOn(LocalDate.of(2026, 1, 31).toEpochDay()))
        assertFalse(plan.occursOn(LocalDate.of(2026, 2, 28).toEpochDay()))
        assertTrue(plan.occursOn(LocalDate.of(2026, 3, 3).toEpochDay()))
        assertTrue(plan.occursOn(LocalDate.of(2026, 3, 31).toEpochDay()))
    }

    private fun plan(
        start: Long,
        repeat: PlanRepeat,
    ) = PlanItem(EntityId("plan"), "Plan", start, 0L, repeat = repeat)
}
