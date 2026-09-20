package com.sowerrrt.dayloom.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalImagesTest {
    @Test
    fun `small images keep their original sample size`() {
        assertEquals(1, calculateSampleSize(width = 800, height = 600, maxDimensionPixels = 1_200))
    }

    @Test
    fun `large images are sampled to a bounded size`() {
        assertEquals(4, calculateSampleSize(width = 4_000, height = 3_000, maxDimensionPixels = 1_200))
    }
}
