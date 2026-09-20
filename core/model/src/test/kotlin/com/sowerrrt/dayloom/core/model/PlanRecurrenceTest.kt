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

    private fun plan(
        start: Long,
        repeat: PlanRepeat,
    ) = PlanItem(EntityId("plan"), "Plan", start, 0L, repeat = repeat)
}
