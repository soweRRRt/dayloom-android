package com.sowerrrt.dayloom.core.designsystem

import com.sowerrrt.dayloom.core.model.AccentPalette
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTest {
    @Test
    fun `body text color contrast meets wcag aa in every palette and theme`() {
        AccentPalette.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val colors = dayloomColorScheme(accent, dark)
                assertTrue(contrastRatio(colors.onSurface, colors.surface) >= 4.5f)
                assertTrue(contrastRatio(colors.onBackground, colors.background) >= 4.5f)
                assertTrue(contrastRatio(colors.onPrimary, colors.primary) >= 4.5f)
                assertTrue(contrastRatio(colors.onPrimaryContainer, colors.primaryContainer) >= 4.5f)
            }
        }
    }
}
