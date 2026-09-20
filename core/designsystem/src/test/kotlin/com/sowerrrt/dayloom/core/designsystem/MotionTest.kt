package com.sowerrrt.dayloom.core.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun `zero animator scale disables motion`() {
        assertFalse(isDayloomMotionEnabled(0f))
    }

    @Test
    fun `positive animator scales keep motion enabled`() {
        assertTrue(isDayloomMotionEnabled(0.5f))
        assertTrue(isDayloomMotionEnabled(1f))
        assertTrue(isDayloomMotionEnabled(2f))
    }
}
