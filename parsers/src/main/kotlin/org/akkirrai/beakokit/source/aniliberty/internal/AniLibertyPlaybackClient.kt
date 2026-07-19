package org.akkirrai.beakokit.source.aniliberty.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.animeresolver.core.TitleMatcher
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.ProviderMatch
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.animeresolver.network.normalizeUrl
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.http.MirrorRequestExecutor
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

internal class AniLibertyPlaybackClient(
    private val client: HttpClient,
    private val matcher: TitleMatcher,
    private val baseUrls: List<String> = DEFAULT_BASE_URLS,
    logger: SourceLogger = SourceLogger.NONE,
) {
    val id: String = "aniliberty"
    val name: String = "AniLiberty"
    private val mirrors = MirrorRequestExecutor(name, baseUrls, logger)

    suspend fun search(title: AnimeTitle): List<ProviderMatch> {
        val results = title.allNames().take(MAX_SEARCH_QUERIES).map { query ->
            runCatching { searchReleases(query) }
        }
        val releases = results.flatMap { it.getOrElse { emptyList() } }
            .distinctBy(AniLibertyReleaseSummary::id)
        if (releases.isEmpty() && results.all { it.isFailure }) {
            throw SourceException("AniLiberty search is temporarily unavailable", cause = results.first().exceptionOrNull())
        }

        return releases.map { release ->
            ProviderMatch(
                providerId = id,
                providerName = name,
                mediaId = release.id.toString(),
                title = release.name.main,
                confidence = matcher.confidence(
                    title = title,
                    candidateNames = listOfNotNull(
                        release.name.main,
                        release.name.english,
                        release.name.alternative,
                    ),
                    candidateYear = release.year,
                    candidateType = release.type?.value,
                    candidateEpisodes = release.episodesTotal,
                ),
                year = release.year,
                type = release.type?.value,
                episodeCount = release.episodesTotal,
            )
        }.filter { it.confidence >= MIN_CONFIDENCE }
    }

    suspend fun getEpisodes(match: ProviderMatch): List<Episode> {
        val release = getRelease(match.mediaId)
        return release.episodes
            .filter { it.id.isNotBlank() && it.ordinal > 0.0 }
            .distinctBy(AniLibertyEpisode::id)
            .sortedBy(AniLibertyEpisode::ordinal)
            .map {
            Episode(
                id = it.id,
                number = it.ordinal,
                title = it.name,
            )
        }
    }

    suspend fun getPlayerLinks(
        match: ProviderMatch,
        episode: Episode,
    ): List<PlayerLink> {
        val releaseEpisode = getRelease(match.mediaId).episodes
            .firstOrNull { it.id == episode.id }
            ?: throw SourceException("AniLiberty не нашёл серию ${episode.number}")
        val segments = releaseEpisode.videoSegments()

        return listOfNotNull(
            releaseEpisode.hls1080?.toPlayerLink("1080p", segments),
            releaseEpisode.hls720?.toPlayerLink("720p", segments),
            releaseEpisode.hls480?.toPlayerLink("480p", segments),
            releaseEpisode.hls360?.toPlayerLink("360p", segments),
            releaseEpisode.hls240?.toPlayerLink("240p", segments),
        )
            .distinctBy(PlayerLink::url)
    }

    private suspend fun getRelease(id: String): AniLibertyRelease {
        cachedReleases[id]?.takeIf { it.cachedAt + CACHE_TTL.inWholeMilliseconds > System.currentTimeMillis() }
            ?.let { return it.release }
        val release = mirrors.execute { baseUrl ->
            client.get("$baseUrl/anime/releases/$id") {
                header(HttpHeaders.Accept, "application/json")
            }.bodyOrThrow<AniLibertyRelease>(name)
        }
        cachedReleases[id] = CachedRelease(release, System.currentTimeMillis())
        return release
    }

    private suspend fun searchReleases(query: String): List<AniLibertyReleaseSummary> = mirrors.execute { baseUrl ->
        client.get("$baseUrl/app/search/releases") {
            header(HttpHeaders.Accept, "application/json")
            parameter("query", query)
        }.bodyOrThrow(name)
    }

    private fun String.toPlayerLink(
        quality: String,
        segments: List<VideoSegment>,
    ) = PlayerLink(
        url = normalizeUrl(this),
        type = PlayerType.DIRECT_HLS,
        quality = quality,
        headers = mapOf("Referer" to "https://anilibria.top/"),
        playerName = name,
        translation = name,
        segments = segments,
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.70
        const val MAX_SEARCH_QUERIES = 3
        val CACHE_TTL = 15.minutes
        val DEFAULT_BASE_URLS = listOf(
            "https://anilibria.top/api/v1",
            "https://api.anilibria.app/api/v1",
        )
    }

    private val cachedReleases = ConcurrentHashMap<String, CachedRelease>()
    private data class CachedRelease(val release: AniLibertyRelease, val cachedAt: Long)
}

@Serializable
private data class AniLibertyReleaseSummary(
    val id: Long,
    val type: AniLibertyValue? = null,
    val year: Int? = null,
    val name: AniLibertyName,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
)

@Serializable
private data class AniLibertyRelease(
    val id: Long,
    val episodes: List<AniLibertyEpisode> = emptyList(),
)

@Serializable
private data class AniLibertyEpisode(
    val id: String,
    val name: String? = null,
    val ordinal: Double,
    @SerialName("hls_480") val hls480: String? = null,
    @SerialName("hls_720") val hls720: String? = null,
    @SerialName("hls_1080") val hls1080: String? = null,
    @SerialName("hls_360") val hls360: String? = null,
    @SerialName("hls_240") val hls240: String? = null,
    val duration: Long? = null,
    val opening: AniLibertyTimecode? = null,
    val ending: AniLibertyTimecode? = null,
)

@Serializable
private data class AniLibertyTimecode(
    val start: Long? = null,
    val stop: Long? = null,
)

private fun AniLibertyEpisode.videoSegments(): List<VideoSegment> = buildList {
    opening.toVideoSegment(VideoSegmentType.OPENING, duration)?.let(::add)
    ending.toVideoSegment(VideoSegmentType.ENDING, duration)?.let(::add)
}

private fun AniLibertyTimecode?.toVideoSegment(
    type: VideoSegmentType,
    durationSeconds: Long?,
): VideoSegment? {
    val startSeconds = this?.start?.coerceAtLeast(0L) ?: return null
    val rawEndSeconds = stop ?: return null
    val endSeconds = durationSeconds
        ?.takeIf { it > 0L }
        ?.let { rawEndSeconds.coerceAtMost(it) }
        ?: rawEndSeconds
    if (endSeconds <= startSeconds) return null
    return VideoSegment(
        type = type,
        startMs = startSeconds * MILLIS_PER_SECOND,
        endMs = endSeconds * MILLIS_PER_SECOND,
    )
}

private const val MILLIS_PER_SECOND = 1_000L

@Serializable
private data class AniLibertyName(
    val main: String,
    val english: String? = null,
    val alternative: String? = null,
)

@Serializable
private data class AniLibertyValue(
    val value: String,
)
