package com.sowerrrt.dayloom.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetsTest {
    @Test
    fun `habit preset round trips all reusable fields`() {
        val preset =
            HabitPreset(
                title = "Walk",
                scheduledWeekdays = setOf(Weekday.MONDAY, Weekday.FRIDAY),
                scheduledMonthDays = setOf(3, 15),
                reminderMinutesOfDay = 8 * 60 + 30,
                targetAmount = "10000",
                targetUnit = "steps",
            )

        assertEquals(preset, preset.toStorageValue().toHabitPresetOrNull())
    }

    @Test
    fun `legacy title remains a usable preset`() {
        assertEquals(PlanPreset("Call parents"), "Call parents".toPlanPresetOrNull())
    }

    @Test
    fun `malformed versioned preset is ignored`() {
        assertNull("dayloom-preset-v2:{broken".toListItemPresetOrNull())
    }
}
