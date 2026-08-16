package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

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
