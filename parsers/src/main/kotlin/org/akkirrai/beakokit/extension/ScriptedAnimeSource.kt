package org.akkirrai.beakokit.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.HealthCheckSource
import org.akkirrai.beakokit.api.LatestSource
import org.akkirrai.beakokit.api.PlaybackGroup
import org.akkirrai.beakokit.api.PlaybackSource
import org.akkirrai.beakokit.api.SourceInfo
import org.akkirrai.beakokit.api.cache.SourceCacheTtl
import org.akkirrai.beakokit.api.context.SourceContext
import org.akkirrai.beakokit.api.execution.SourceOperation
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink

/**
 * An [AnimeSource] backed by a scripted (JS) [ScriptExtensionManifest] instead of compiled Kotlin.
 *
 * Identity/capability metadata ([info], [catalogCapabilities]) comes from the trusted manifest,
 * never from the script, so a broken or malicious payload can't corrupt a source's identity or
 * claim capabilities it doesn't have - only actual content (titles, episodes, links, filter option
 * labels) flows through the sandboxed [RhinoExtensionRuntime]. [getSearchFilterCatalog] is the one
 * exception that legitimately needs the script: filter *option lists* (which genres/types/statuses
 * exist) are real content a source may need a network call to produce, so they come from the JS
 * `getSettings()` function - but the [CatalogCapabilities] describing what's supported is still
 * manifest-derived and simply spliced back in, never trusted from the script's return value.
 *
 * Every scripted source today unconditionally implements [LatestSource], [PlaybackSource] and
 * [HealthCheckSource] - [ScriptExtensionManifest.violations] enforces that a manifest declares both
 * required capabilities so [org.akkirrai.beakokit.api.contract.SourceContractValidator] never rejects it.
 */
class ScriptedAnimeSource(
    private val context: SourceContext,
    private val manifest: ScriptExtensionManifest,
) : AnimeSource, LatestSource, PlaybackSource, HealthCheckSource {
    private val execution = context.sourceExecutionPolicy
    private val runtimePool by lazy {
        RhinoRuntimePool(RUNTIME_POOL_SIZE) { RhinoExtensionRuntime(manifest.id, manifest.payload, context) }
    }
    private val requestJson = Json { encodeDefaults = true }

    private suspend inline fun <reified T> callScript(functionName: String, vararg args: Any?): T =
        withContext(Dispatchers.IO) { runtimePool.use { it.call(functionName, *args) } }

    override val info: SourceInfo = manifest.toSourceInfo()

    override val catalogCapabilities: CatalogCapabilities = CatalogCapabilities(
        supportedSorts = manifest.supportedSorts,
        supportedFilters = manifest.supportedFilters,
        features = setOf(CatalogFeature.LATEST_RELEASES),
        fallbackSort = manifest.fallbackSort,
    )

    override suspend fun search(query: String): List<AnimeTitle> = search(AnimeSearchRequest(query = query))

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> =
        execution.execute(info.id, SourceOperation.SEARCH, "request:$request", SourceCacheTtl.SEARCH_MILLIS) {
            val adapted = catalogCapabilities.adapt(request)
            callScript<List<AnimeTitle>>(
                "search",
                requestJson.encodeToString(AnimeSearchRequest.serializer(), adapted),
            )
        }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog =
        execution.execute(info.id, SourceOperation.FILTER_CATALOG, "default", SourceCacheTtl.FILTER_CATALOG_MILLIS) {
            callScript<AnimeSearchFilterCatalog>("getSettings").copy(capabilities = catalogCapabilities)
        }

    override suspend fun getById(id: String): AnimeTitle =
        execution.execute(info.id, SourceOperation.DETAILS, id, SourceCacheTtl.DETAILS_MILLIS) {
            callScript<AnimeTitle>("getById", id)
        }

    override suspend fun latest(limit: Int): List<AnimeTitle> =
        execution.execute(info.id, SourceOperation.LATEST, "limit:$limit", SourceCacheTtl.LATEST_MILLIS) {
            callScript<List<AnimeTitle>>("latest", limit)
        }

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> =
        execution.execute(info.id, SourceOperation.PLAYBACK_GROUPS, title.id, SourceCacheTtl.PLAYBACK_GROUPS_MILLIS) {
            callScript<List<PlaybackGroup>>("getPlaybackGroups", title.id)
        }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = execution.execute(info.id, SourceOperation.PLAYER_LINKS) {
        callScript<List<PlayerLink>>("getPlayerLinks", title.id, group.id, episode.id)
    }

    override suspend fun checkHealth() {
        execution.execute(info.id, SourceOperation.HEALTH_CHECK) {
            callScript<List<AnimeTitle>>("latest", 1)
        }
    }

    private companion object {
        /** Matches AnimeSearchRepository.MAX_CONCURRENT_DETAILS_REQUESTS on the app side, so
         * concurrent per-card enrichment calls (see CatalogViewModel/HomeViewModel) can actually
         * run in parallel instead of queueing on a single runtime's lock. */
        const val RUNTIME_POOL_SIZE = 3
    }
}
