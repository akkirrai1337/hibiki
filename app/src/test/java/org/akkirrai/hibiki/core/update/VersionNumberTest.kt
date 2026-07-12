package org.akkirrai.hibiki.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNumberTest {
    @Test
    fun `compares each numeric version component`() {
        assertTrue(VersionNumber.parse("1.10")!! > VersionNumber.parse("1.9")!!)
        assertTrue(VersionNumber.parse("v1.6.1")!! > VersionNumber.parse("1.6")!!)
        assertEquals(0, VersionNumber.parse("1.6")!!.compareTo(VersionNumber.parse("1.6.0")!!))
    }

    @Test
    fun `rejects unsupported release labels`() {
        assertEquals(null, VersionNumber.parse("1.6-beta"))
        assertEquals(null, VersionNumber.parse("release-1.6"))
    }
}
