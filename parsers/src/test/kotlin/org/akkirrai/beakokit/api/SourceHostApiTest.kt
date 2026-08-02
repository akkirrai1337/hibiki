package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceHostApiTest {
    @Test
    fun `current host API version is supported`() {
        assertTrue(SourceHostApi.supports(SourceHostApi.VERSION))
    }

    @Test
    fun `unknown host API versions are rejected`() {
        assertFalse(SourceHostApi.supports(SourceHostApi.VERSION - 1))
        assertFalse(SourceHostApi.supports(SourceHostApi.VERSION + 1))
    }
}
