package org.akkirrai.beakokit.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourcePackageActivationPersistenceTest {
    @Test
    fun `activation state survives json persistence round trip`() {
        val state = SourcePackageActivationState(
            active = packageVersion("2.0.0"),
            previous = packageVersion("1.0.0"),
        )

        val encoded = Json.encodeToString(state)
        val restored = Json.decodeFromString<SourcePackageActivationState>(encoded)

        assertEquals(state, restored)
    }

    @Test
    fun `json persistence rejects a previous package without an active package`() {
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<SourcePackageActivationState>(
                """{"previous":{"sourceId":"external-source","packageVersion":"1.0.0","packagePath":"packages/1.0.0"}}""",
            )
        }
    }

    private fun packageVersion(version: String) = InstalledSourcePackage(
        sourceId = SourceId("external-source"),
        packageVersion = version,
        packagePath = "packages/external-source/$version",
    )
}
