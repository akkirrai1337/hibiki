package org.akkirrai.animeresolver.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.animeresolver.core.TitleMatcher
import org.akkirrai.animeresolver.core.VideoProvider
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.akkirrai.animeresolver.model.ProviderMatch
import org.akkirrai.animeresolver.network.bodyOrThrow
import java.util.concurrent.ConcurrentHashMap

class AniLibertyProvider(
    private val client: HttpClient,
    private val matcher: TitleMatcher,
    private val baseUrl: String = "https://anilibria.top/api/v1",
) : VideoProvider {
    override val id: String = "aniliberty"
    override val name: String = "AniLiberty"

    override suspend fun search(title: AnimeTitle): List<ProviderMatch> {
        val queries = title.allNames().take(3)
        val releases = queries.flatMap { query ->
            val response = client.get("$baseUrl/app/search/releases") {
                parameter("query", query)
            }
            response.bodyOrThrow<List<AniLibertyReleaseSummary>>(name)
        }.distinctBy(AniLibertyReleaseSummary::id)

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

    override suspend fun getEpisodes(match: ProviderMatch): List<Episode> {
        val release = getRelease(match.mediaId)
        return release.episodes.map {
            Episode(
                id = it.id,
                number = it.ordinal,
                title = it.name,
            )
        }
    }

    override suspend fun getPlayerLinks(
        match: ProviderMatch,
        episode: Episode,
    ): List<PlayerLink> {
        val releaseEpisode = getRelease(match.mediaId).episodes
            .firstOrNull { it.id == episode.id }
            ?: throw SourceException("AniLiberty не нашёл серию ${episode.number}")

        return listOfNotNull(
            releaseEpisode.hls1080?.toPlayerLink("1080p"),
            releaseEpisode.hls720?.toPlayerLink("720p"),
            releaseEpisode.hls480?.toPlayerLink("480p"),
        )
    }

    private suspend fun getRelease(id: String): AniLibertyRelease {
        cachedReleases[id]?.let { return it }
        val response = client.get("$baseUrl/anime/releases/$id")
        return response.bodyOrThrow<AniLibertyRelease>(name)
            .also { cachedReleases[id] = it }
    }

    private fun String.toPlayerLink(quality: String) = PlayerLink(
        url = this,
        type = PlayerType.DIRECT_HLS,
        quality = quality,
        headers = mapOf("Referer" to "https://anilibria.top/"),
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.70
        val cachedReleases = ConcurrentHashMap<String, AniLibertyRelease>()
    }
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
)

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
