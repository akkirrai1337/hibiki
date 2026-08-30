package org.akkirrai.beakokit.testkit

import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.extension.PlayerResolverExtensionManifest

/**
 * Loads a player-resolver extension's manifest+payload from test resources for Rhino-backed
 * tests, mirroring [ScriptedExtensionFixtures] for anime source extensions. Resolvers are authored
 * as a `beakokit/resolvers/<id>.manifest.json` (metadata only) + `beakokit/resolvers/<id>.js`
 * (payload) pair under test resources - the same split hibiki-sources publishes under
 * `extensions/extractors/`.
 */
object ScriptedResolverFixtures {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Resolver payloads are gitignored local-only fixtures, so tests that depend on them must
     * check this first and skip (not fail) when they're absent - e.g. on a fresh clone or CI.
     */
    fun isAvailable(id: String): Boolean =
        FixtureResources.exists("beakokit/resolvers/$id.manifest.json") &&
            FixtureResources.exists("beakokit/resolvers/$id.js")

    /** Returns the merged manifest+payload JSON [org.akkirrai.beakokit.extension.PlayerResolverExtensionRepository.install] expects. */
    fun loadJson(id: String): String {
        val metadata = json.decodeFromString(
            PlayerResolverExtensionManifest.serializer(),
            FixtureResources.read("beakokit/resolvers/$id.manifest.json"),
        )
        val payload = FixtureResources.read("beakokit/resolvers/$id.js")
        return json.encodeToString(PlayerResolverExtensionManifest.serializer(), metadata.copy(payload = payload))
    }
}
