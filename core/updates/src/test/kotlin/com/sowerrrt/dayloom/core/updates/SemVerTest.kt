package com.sowerrrt.dayloom.core.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun `stable release is newer than prerelease`() {
        assertTrue(version("1.0.0") > version("1.0.0-rc.1"))
    }

    @Test
    fun `numeric prerelease identifiers use numeric ordering`() {
        assertTrue(version("1.0.0-beta.11") > version("1.0.0-beta.2"))
    }

    @Test
    fun `v prefix and build metadata are accepted`() {
        assertEquals(version("2.4.1"), SemVer.parseOrNull("v2.4.1+42"))
    }

    @Test
    fun `invalid version is rejected`() {
        assertNull(SemVer.parseOrNull("1.2"))
    }

    private fun version(value: String) = requireNotNull(SemVer.parseOrNull(value))
}
