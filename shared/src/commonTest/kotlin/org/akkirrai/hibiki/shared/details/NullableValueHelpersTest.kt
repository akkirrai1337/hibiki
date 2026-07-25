package org.akkirrai.hibiki.shared.details

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NullableValueHelpersTest {
    @Test
    fun detectsNullAndZero() {
        assertTrue((null as Long?).isNullOrZero())
        assertTrue(0L.isNullOrZero())
        assertFalse(1L.isNullOrZero())
    }
}
