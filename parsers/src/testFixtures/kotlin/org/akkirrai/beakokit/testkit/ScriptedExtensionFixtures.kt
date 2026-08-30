package org.akkirrai.beakokit.testkit

import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.extension.ScriptExtensionManifest

/**
 * Loads a scripted extension's manifest+payload from test resources for Rhino-backed tests.
 *
 * Extension sources are authored as a `beakokit/extensions/<id>.manifest.json` (metadata only) +
 * `beakokit/extensions/<id>.js` (payload) pair under test resources - the same split the
 * `hibiki-sources` repository publishes as one combined `<id>.json` file. This mirrors that merge
 * step locally so tests exercise the exact payload that gets published.
 */
object ScriptedExtensionFixtures {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Extension payloads are gitignored local-only fixtures, so tests that depend on them must
     * check this first and skip (not fail) when they're absent - e.g. on a fresh clone or CI.
     */
    fun isAvailable(id: String): Boolean =
        FixtureResources.exists("beakokit/extensions/$id.manifest.json") &&
            FixtureResources.exists("beakokit/extensions/$id.js")

    fun load(id: String): ScriptExtensionManifest {
        val metadata = json.decodeFromString(
            ScriptExtensionManifest.serializer(),
            FixtureResources.read("beakokit/extensions/$id.manifest.json"),
        )
        return metadata.copy(payload = FixtureResources.read("beakokit/extensions/$id.js"))
    }
}
