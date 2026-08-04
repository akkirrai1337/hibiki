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
        requireValidExternalSearchRequest(request)
        return runtime.search(request).also {
            requireValidExternalResultCount(it.size, request.limit, "search")
            requireValidExternalTitles(it, "search")
        }
    }

    override suspend fun getById(id: String): AnimeTitle {
        SourceOperationGate.requireSupported(this, SourceOperation.DETAILS)
        requireValidExternalRuntimeId(id, "title")
        return runtime.details(id).also { requireValidExternalTitle(it, "details") }
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
        return latestRuntime.latest(limit).also {
            requireValidExternalResultCount(it.size, limit, "latest")
            requireValidExternalTitles(it, "latest")
        }
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

private fun requireValidExternalTitles(titles: List<AnimeTitle>, operation: String) {
    require(titles.map(AnimeTitle::id).distinct().size == titles.size) {
        "External source returned duplicate title ids in $operation"
    }
    titles.forEach { requireValidExternalTitle(it, operation) }
}

private fun requireValidExternalSearchRequest(request: AnimeSearchRequest) {
    require(request.limit > 0) { "External source search limit must be positive" }
    require(request.offset >= 0) { "External source search offset must not be negative" }
    require(request.yearFrom == null || request.yearTo == null || request.yearFrom <= request.yearTo) {
        "External source search yearFrom must not be greater than yearTo"
    }
}

private fun requireValidExternalResultCount(actual: Int, requested: Int, operation: String) {
    require(actual <= requested) {
        "External source returned $actual titles for $operation limit $requested"
    }
}

private fun requireValidExternalRuntimeId(id: String, label: String) {
    require(id.isNotBlank()) { "External source $label ID must not be blank" }
    require('\r' !in id && '\n' !in id) {
        "External source $label ID must not contain CR or LF"
    }
}

private fun requireValidExternalTitle(title: AnimeTitle, operation: String) {
    require(title.id.isNotBlank()) {
        "External source returned a blank title id in $operation"
    }
    require('\r' !in title.id && '\n' !in title.id) {
        "External source returned a title id containing CR or LF in $operation"
    }
    require(title.displayName.isNotBlank()) {
        "External source returned a blank display name for ${title.id} in $operation"
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
        return latestRuntime.latest(limit).also {
            requireValidExternalResultCount(it.size, limit, "latest")
            requireValidExternalTitles(it, "latest")
        }
    }
}
