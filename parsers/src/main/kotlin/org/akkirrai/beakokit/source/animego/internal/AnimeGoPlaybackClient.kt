package org.akkirrai.beakokit.source.animego.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.http.bodyOrThrow
import org.akkirrai.beakokit.http.resolveUrl
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.jsoup.Jsoup

internal class AnimeGoPlaybackClient(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getEpisodes(titleId: String): List<Episode> {
        val animeId = numericId(titleId)
        val content = playerResponse("/player/$animeId").data.content
        return Jsoup.parseBodyFragment(content)
            .select(".player-video-bar__item[data-episode]")
            .mapNotNull { element ->
                val id = element.attr("data-episode").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val number = element.attr("data-episode-number").toDoubleOrNull()
                    ?: element.selectFirst(".player-video-bar__number")
                        ?.text()
                        ?.trim()
                        ?.toDoubleOrNull()
                    ?: return@mapNotNull null
                Episode(
                    id = id,
                    number = number,
                    title = element.attr("data-episode-title").takeIf(String::isNotBlank),
                )
            }
            .distinctBy(Episode::id)
    }

    suspend fun getPlayerLinks(episode: Episode): List<PlayerLink> {
        val content = playerResponse("/player/videos/${episode.id}").data.content
        return Jsoup.parseBodyFragment(content)
            .select("[data-player]")
            .mapNotNull { element ->
                val rawUrl = element.attr("data-player").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                PlayerLink(
                    url = resolveUrl(baseUrl, rawUrl),
                    type = PlayerType.EMBED,
                    quality = null,
                    headers = mapOf(HttpHeaders.Referrer to "${baseUrl.trimEnd('/')}/"),
                    playerName = element.attr("data-provider-title").takeIf(String::isNotBlank),
                    translation = element.attr("data-translation-title").takeIf(String::isNotBlank),
                )
            }
            .sortedBy { playerPriority(it.playerName) }
    }

    private suspend fun playerResponse(path: String): AnimeGoResponse =
        client.get("${baseUrl.trimEnd('/')}$path") {
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.Referrer, "${baseUrl.trimEnd('/')}/")
        }.bodyOrThrow("AnimeGo")

    private fun numericId(titleId: String): String = ID_AT_END.find(titleId)?.groupValues?.get(1)
        ?: throw SourceException(
            message = "AnimeGo title id has no numeric suffix: $titleId",
            kind = SourceErrorKind.NOT_FOUND,
        )

    private fun playerPriority(name: String?): Int = when (name?.lowercase()) {
        "aniboom" -> 0
        "cvh" -> 1
        "kodik" -> 2
        "sibnet" -> 3
        else -> 10
    }

    private companion object {
        val ID_AT_END = Regex("-(\\d+)$")
    }
}

@Serializable
private data class AnimeGoResponse(
    val status: String,
    val data: AnimeGoResponseData,
)

@Serializable
private data class AnimeGoResponseData(
    val content: String,
)
