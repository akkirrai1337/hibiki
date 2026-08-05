package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SourceConfigStateTest {
    @Test
    fun snapshotRoundTripKeepsValuesAndSecretsSeparate() {
        val source = MapSourceConfig(
            values = mapOf("base_url" to "https://source.test"),
            secrets = mapOf("token" to "secret-value"),
        )

        val restored = source.snapshot().asConfig()

        assertEquals("https://source.test", restored.value("base_url"))
        assertEquals("secret-value", restored.secret("token"))
        assertNull(restored.value("token"))
        assertNull(restored.secret("base_url"))
    }

    @Test
    fun stateRejectsOverlappingValueAndSecretKeys() {
        assertFailsWith<IllegalArgumentException> {
            SourceConfigState(
                values = mapOf("token" to "regular"),
                secrets = mapOf("token" to "secret"),
            )
        }
    }

    @Test
    fun stateEnforcesHostBoundaryLimitsBeforePersistence() {
        assertFailsWith<IllegalArgumentException> {
            SourceConfigState(values = mapOf("bad\nkey" to "value"))
        }
        assertFailsWith<IllegalArgumentException> {
            SourceConfigState(values = mapOf("value" to "x".repeat(SourceHostConfigLimits.MAX_VALUE_LENGTH + 1)))
        }
    }
}
