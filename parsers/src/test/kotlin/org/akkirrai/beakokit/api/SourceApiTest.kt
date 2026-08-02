package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceApiTest {
    @Test
    fun `current source contract is supported`() {
        assertTrue(SourceApi.supports(SourceApi.VERSION))
        assertEquals(SourceApi.VERSION, SourceApi.MIN_SUPPORTED_VERSION)
    }

    @Test
    fun `unsupported source contract versions are rejected`() {
        assertFalse(SourceApi.supports(SourceApi.VERSION - 1))
        assertFalse(SourceApi.supports(SourceApi.VERSION + 1))
    }

    @Test
    fun `source exception exposes stable code`() {
        val exception = SourceException(
            message = "invalid payload",
            kind = SourceErrorKind.PARSE,
        )

        assertEquals(SourceErrorCode.INVALID_RESPONSE, exception.code)
        assertEquals("invalid_response", exception.code.value)
    }

    @Test
    fun `source exception can override stable code without changing legacy kind`() {
        val exception = SourceException(
            message = "runtime cancelled",
            kind = SourceErrorKind.UNKNOWN,
            code = SourceErrorCode.CANCELLED,
        )

        assertEquals(SourceErrorKind.UNKNOWN, exception.kind)
        assertEquals(SourceErrorCode.CANCELLED, exception.code)
    }
}
