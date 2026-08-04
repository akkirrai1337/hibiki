package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceManifestInfoTest {
    @Test
    fun manifestMetadataProducesSourceInfoForRegistration() {
        val manifest = manifest(
            SourceManifestInfo(
                displayName = "External source",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
                website = "https://example.com",
                iconUrl = "https://example.com/icon.png",
            ),
        )

        assertEquals("External source", manifest.requireSourceInfo().name)
        assertEquals(SourceCapability.PLAYBACK, manifest.requireSourceInfo().capabilities.single())
    }

    @Test
    fun manifestWithoutMetadataCannotBeRegistered() {
        assertFailsWith<SourceManifestException> {
            manifest(null).requireSourceInfo()
        }
    }

    @Test
    fun manifestMetadataRejectsInvalidIdentityAndLinks() {
        val invalid = manifest(
            SourceManifestInfo(
                displayName = "",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.RUSSIAN,
                website = "http://example.com",
                iconUrl = "http://example.com/icon.png",
            ),
        )

        val violations = invalid.violations(
            clientVersion = 1,
            supportedApiVersion = SourceApi.VERSION,
        )

        assertEquals(true, violations.any { it.contains("display name") })
        assertEquals(true, violations.any { it.contains("Primary source language") })
        assertEquals(true, violations.any { it.contains("website") })
        assertEquals(true, violations.any { it.contains("icon URL") })
    }

    @Test
    fun manifestMetadataRejectsEmptyHttpsLinks() {
        val violations = manifest(
            SourceManifestInfo(
                displayName = "External source",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
                website = "https://",
                iconUrl = "https://",
            ),
        ).violations(
            clientVersion = 1,
            supportedApiVersion = SourceApi.VERSION,
        )

        assertEquals(true, violations.any { it.contains("website") })
        assertEquals(true, violations.any { it.contains("icon URL") })
    }

    private fun manifest(info: SourceManifestInfo?) = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-test"),
        packageVersion = "1.0.0",
        sourceInfo = info,
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 1,
        minClientVersion = 1,
        capabilities = setOf(SourceCapability.PLAYBACK),
    )
}
