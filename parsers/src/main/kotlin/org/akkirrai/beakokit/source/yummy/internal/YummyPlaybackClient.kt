package org.akkirrai.beakokit.source.yummy.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.animeresolver.core.TitleMatcher
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.akkirrai.animeresolver.model.ProviderMatch
import org.akkirrai.animeresolver.model.VideoSegment
import org.akkirrai.animeresolver.model.VideoSegmentType
import org.akkirrai.animeresolver.network.bodyOrThrow
import java.util.concurrent.ConcurrentHashMap

internal class YummyPlaybackClient(
    private val client: HttpClient,
    private val matcher: TitleMatcher,
    private val applicationToken: String? = null,
    private val baseUrl: String = "https://api.yani.tv",
    private val debugLogger: ((String) -> Unit)? = null,
) {
    val id: String = "yummyanime"
    val name: String = "YummyAnime"

    suspend fun search(title: AnimeTitle): List<ProviderMatch> {
        val results = title.allNames().take(3).flatMap { query ->
            val response = client.get("$baseUrl/anime") {
                addHeaders()
                parameter("q", query)
                parameter("limit", 10)
            }
            response.bodyOrThrow<YummyResponse<List<YummyAnimeSummary>>>(name).response
        }.distinctBy(YummyAnimeSummary::animeId)

        return results.map { anime ->
            val confidence = if (anime.animeId.toString() == title.id) {
                1.0
            } else {
                matcher.confidence(
                    title = title,
                    candidateNames = listOf(anime.title),
                    candidateYear = anime.year,
                    candidateType = anime.type?.alias,
                    candidateEpisodes = null,
                )
            }
            ProviderMatch(
                providerId = id,
                providerName = name,
                mediaId = anime.animeId.toString(),
                title = anime.title,
                confidence = confidence,
                year = anime.year,
                type = anime.type?.alias,
                episodeCount = null,
            )
        }.filter { it.confidence >= MIN_CONFIDENCE }
    }

    suspend fun getEpisodes(match: ProviderMatch): List<Episode> {
        return getEpisodeIndex(match.mediaId).values.toList()
    }

    suspend fun getPlayerLinks(
        match: ProviderMatch,
        episode: Episode,
    ): List<PlayerLink> {
        val videos = getVideos(match.mediaId)
            .filter { parseEpisodeNumber(it.number) == episode.number }
        if (videos.isEmpty()) {
            throw SourceException("YummyAnime не нашёл серию ${episode.number}")
        }
        return videos.map { video ->
            PlayerLink(
                url = absoluteUrl(video.iframeUrl),
                type = PlayerType.EMBED,
                quality = null,
                headers = mapOf(HttpHeaders.Referrer to "https://ru.yummyani.me/"),
                playerName = video.data.player.removePrefix("Плеер ").trim(),
                translation = video.data.dubbing.removePrefix("Озвучка ").trim(),
                segments = video.skips.toVideoSegments(),
                videoId = video.videoId,
            )
        }.sortedBy { playerPriority(it.playerName) }
    }

    suspend fun getDubbingCatalog(match: ProviderMatch): List<YummyDubbingCatalog> {
        val episodesByNumber = getEpisodeIndex(match.mediaId)
        return getVideos(match.mediaId)
            .groupBy { it.data.dubbing.removePrefix("Озвучка ").trim() }
            .mapNotNull { (dubbing, videos) ->
                val normalizedDubbing = dubbing.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val episodes = videos
                    .mapNotNull { video -> episodesByNumber[video.number] }
                    .distinctBy(Episode::id)
                    .sortedBy(Episode::number)
                if (episodes.isEmpty()) return@mapNotNull null
                YummyDubbingCatalog(
                    title = normalizedDubbing,
                    episodes = episodes,
                    qualityLabel = null,
                )
            }
    }

    private suspend fun getVideos(animeId: String): List<YummyVideo> {
        cachedVideos[animeId]?.let { return it }
        val response = client.get("$baseUrl/anime/$animeId/videos") {
            addHeaders()
        }
        return response.bodyOrThrow<YummyResponse<List<YummyVideo>>>(name).response
            .also { cachedVideos[animeId] = it }
    }

    private suspend fun getEpisodeIndex(animeId: String): Map<String, Episode> {
        cachedEpisodes[animeId]?.let { return it }
        return getVideos(animeId)
            .groupBy(YummyVideo::number)
            .mapNotNull { (number, videos) ->
                val parsedNumber = parseEpisodeNumber(number) ?: return@mapNotNull null
                number to Episode(
                    id = number,
                    number = parsedNumber,
                    title = videos.firstNotNullOfOrNull { it.title },
                )
            }
            .toMap()
            .also { cachedEpisodes[animeId] = it }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addHeaders() {
        header("Lang", "ru")
        debugLogger?.invoke(
            "Yummy request: xApplicationAttached=${!applicationToken.isNullOrBlank()}, url=${url.buildString()}"
        )
        applicationToken?.takeIf(String::isNotBlank)?.let {
            header("X-Application", it)
        }
    }

    private fun parseEpisodeNumber(value: String): Double? =
        EPISODE_NUMBER.find(value.replace(',', '.'))?.value?.toDoubleOrNull()

    private fun absoluteUrl(url: String): String =
        if (url.startsWith("//")) "https:$url" else url

    private fun YummyVideoSkips?.toVideoSegments(): List<VideoSegment> {
        if (this == null) return emptyList()
        return listOfNotNull(
            opening?.toVideoSegment(VideoSegmentType.OPENING),
            ending?.toVideoSegment(VideoSegmentType.ENDING),
        )
    }

    private fun YummySkipRange.toVideoSegment(type: VideoSegmentType): VideoSegment? {
        if (time < 0 || length <= 0) return null
        return VideoSegment(
            type = type,
            startMs = time * 1_000L,
            endMs = (time + length) * 1_000L,
        )
    }

    private fun playerPriority(name: String?): Int = when (name?.lowercase()) {
        "kodik" -> 0
        "aksor" -> 1
        "alloha" -> 2
        "sibnet" -> 3
        "cvh" -> 4
        "vk" -> 5
        else -> 10
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.70
        val EPISODE_NUMBER = Regex("""\d+(?:\.\d+)?""")
        val cachedVideos = ConcurrentHashMap<String, List<YummyVideo>>()
        val cachedEpisodes = ConcurrentHashMap<String, Map<String, Episode>>()
    }
}

internal data class YummyDubbingCatalog(
    val title: String,
    val episodes: List<Episode>,
    val qualityLabel: String?,
)

@Serializable
private data class YummyResponse<T>(
    val response: T,
)

@Serializable
private data class YummyAnimeSummary(
    @SerialName("anime_id") val animeId: Long,
    val title: String,
    val year: Int? = null,
    val type: YummyAnimeType? = null,
)

@Serializable
private data class YummyAnimeType(
    val alias: String? = null,
)

@Serializable
private data class YummyVideo(
    @SerialName("video_id") val videoId: Long,
    @SerialName("iframe_url") val iframeUrl: String,
    val data: YummyVideoData,
    val number: String,
    val title: String? = null,
    val skips: YummyVideoSkips? = null,
)

@Serializable
private data class YummyVideoData(
    val dubbing: String,
    val player: String,
    @SerialName("player_id") val playerId: Long,
)

@Serializable
private data class YummyVideoSkips(
    val opening: YummySkipRange? = null,
    val ending: YummySkipRange? = null,
)

@Serializable
private data class YummySkipRange(
    val time: Long,
    val length: Long,
)
