package org.akkirrai.beakokit.api

import io.ktor.http.Url
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink

/** Minimal runtime surface for the first external-source milestone. */
interface ExternalSourceRuntime {
    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle>

    suspend fun details(id: String): AnimeTitle
}

/** Optional runtime contract for sources that expose their latest updated titles. */
interface ExternalSourceLatestRuntime : ExternalSourceRuntime {
    suspend fun latest(limit: Int): List<AnimeTitle>
}

/** Optional runtime contract for sources that expose episodes and playable links. */
interface ExternalSourcePlaybackRuntime : ExternalSourceRuntime {
    suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup>

    suspend fun playerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink>
}

/** Combined optional runtime contract for latest titles and playback. */
interface ExternalSourceLatestPlaybackRuntime : ExternalSourcePlaybackRuntime, ExternalSourceLatestRuntime

/** Platform adapter that creates a runtime for one already active source package. */
fun interface ExternalSourceRuntimeFactory {
    fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
    ): ExternalSourceRuntime
}

/** Adapts a runtime-backed external source to the regular BeakoKit source contract. */
open class RuntimeBackedAnimeSource(
    override val info: SourceInfo,
    override val catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    protected val runtime: ExternalSourceRuntime,
) : ConfigurableSource {
    override val configSchema: SourceConfigSchema
        get() = info.configSchema

    override suspend fun search(query: String): List<AnimeTitle> = search(
        AnimeSearchRequest(query = query),
    )

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        SourceOperationGate.requireSupported(this, SourceOperation.SEARCH)
        return runtime.search(request)
    }

    override suspend fun getById(id: String): AnimeTitle {
        SourceOperationGate.requireSupported(this, SourceOperation.DETAILS)
        return runtime.details(id)
    }
}

/** Adapts the optional latest operation from an external runtime. */
class RuntimeBackedLatestAnimeSource(
    info: SourceInfo,
    catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    runtime: ExternalSourceLatestRuntime,
) : RuntimeBackedAnimeSource(
    info = info,
    catalogCapabilities = catalogCapabilities,
    runtime = runtime,
), LatestSource {
    private val latestRuntime = runtime

    override suspend fun latest(limit: Int): List<AnimeTitle> {
        require(limit > 0) { "Latest source limit must be positive" }
        SourceOperationGate.requireSupported(this, SourceOperation.LATEST)
        return latestRuntime.latest(limit)
    }
}

/** BeakoKit adapter for an external runtime with the optional playback contract. */
open class RuntimeBackedPlaybackAnimeSource(
    info: SourceInfo,
    catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    runtime: ExternalSourcePlaybackRuntime,
) : RuntimeBackedAnimeSource(
    info = info,
    catalogCapabilities = catalogCapabilities,
    runtime = runtime,
), PlaybackSource {
    private val playbackRuntime = runtime

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> {
        SourceOperationGate.requireSupported(this, SourceOperation.PLAYBACK_GROUPS)
        return playbackRuntime.playbackGroups(title).also {
            requireValidExternalPlaybackGroups(it)
        }
    }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> {
        SourceOperationGate.requireSupported(this, SourceOperation.PLAYER_LINKS)
        return playbackRuntime.playerLinks(title, group, episode).also {
            requireValidExternalPlayerLinks(info, it)
        }
    }
}

private fun requireValidExternalPlayerLinks(info: SourceInfo, links: List<PlayerLink>) {
    links.forEach { link ->
        require(link.url.isNotBlank()) { "External player link URL must not be blank" }
        val parsed = runCatching { Url(link.url) }.getOrNull()
        require(parsed != null && parsed.host.isNotBlank() && parsed.protocol.name in setOf("http", "https")) {
            "External player link must be an absolute HTTP(S) URL"
        }
        if (parsed.protocol.name == "http") {
            require(parsed.host.lowercase() in info.networkRequirements.cleartextPlaybackHosts) {
                "External source ${info.id} returned an undeclared cleartext playback host"
            }
        }
        requireSafeHttpHeaders(link.headers)
    }
}

private fun requireValidExternalPlaybackGroups(groups: List<PlaybackGroup>) {
    require(groups.isNotEmpty()) { "External playback runtime returned no groups" }
    require(groups.map(PlaybackGroup::id).distinct().size == groups.size) {
        "External playback group ids must be unique"
    }
    groups.forEach { group ->
        require(group.episodes.isNotEmpty()) {
            "External playback group ${group.id} returned no episodes"
        }
        require(group.episodes.all { it.id.isNotBlank() }) {
            "External playback episode ids must not be blank"
        }
        require(group.episodes.map { it.id }.distinct().size == group.episodes.size) {
            "External playback episode ids must be unique in group ${group.id}"
        }
        require(group.episodes.all { it.number.isFinite() }) {
            "External playback episode numbers must be finite in group ${group.id}"
        }
    }
}

/** Adapts an external runtime that exposes both latest titles and playback. */
class RuntimeBackedLatestPlaybackAnimeSource(
    info: SourceInfo,
    catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    runtime: ExternalSourceLatestPlaybackRuntime,
) : RuntimeBackedPlaybackAnimeSource(
    info = info,
    catalogCapabilities = catalogCapabilities,
    runtime = runtime,
), LatestSource {
    private val latestRuntime = runtime

    override suspend fun latest(limit: Int): List<AnimeTitle> {
        require(limit > 0) { "Latest source limit must be positive" }
        SourceOperationGate.requireSupported(this, SourceOperation.LATEST)
        return latestRuntime.latest(limit)
    }
}
