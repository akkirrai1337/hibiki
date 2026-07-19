package org.akkirrai.animeresolver.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.animeresolver.core.VideoProvider
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.ProviderMatch
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.animeresolver.network.pathOf
import org.jsoup.Jsoup

class AnimeGoProvider(
    private val client: HttpClient,
    private val matcher: TitleMatcher,
    private val baseUrl: String = "https://animego.me",
) : VideoProvider {
    override val id: String = "animego"
    override val name: String = "AnimeGo"

    override suspend fun search(title: AnimeTitle): List<ProviderMatch> {
        val results = title.allNames().take(3).flatMap { query ->
            val response = client.get("$baseUrl/search/all") {
                parameter("q", query)
            }
            if (!response.status.isSuccess()) {
                throw SourceException(
                    "$name вернул HTTP ${response.status.value}",
                    response.status.value,
                )
            }
            parseSearch(response.bodyAsText())
        }.distinctBy(SearchResult::id)

        return results.map { result ->
            ProviderMatch(
                providerId = id,
                providerName = name,
                mediaId = result.id,
                title = result.russianName,
                confidence = matcher.confidence(
                    title = title,
                    candidateNames = listOfNotNull(result.russianName, result.originalName),
                    candidateYear = result.year,
                    candidateType = result.type,
                    candidateEpisodes = null,
                ),
                year = result.year,
                type = result.type,
                episodeCount = null,
            )
        }.filter { it.confidence >= MIN_CONFIDENCE }
    }

    override suspend fun getEpisodes(match: ProviderMatch): List<Episode> {
        val content = getPlayerContent(match.mediaId)
        val document = Jsoup.parseBodyFragment(content)
        return document.select(".player-video-bar__item[data-episode]").mapNotNull { element ->
            val id = element.attr("data-episode").takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val number = element.attr("data-episode-number").toDoubleOrNull()
                ?: return@mapNotNull null
            Episode(
                id = id,
                number = number,
                title = element.attr("data-episode-title").takeIf(String::isNotBlank),
            )
        }.distinctBy(Episode::id)
    }

    override suspend fun getPlayerLinks(
        match: ProviderMatch,
        episode: Episode,
    ): List<PlayerLink> {
        val response = client.get("$baseUrl/player/videos/${episode.id}") {
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.Referrer, "$baseUrl/")
        }
        val payload = response.bodyOrThrow<AnimeGoResponse>(name)
        val document = Jsoup.parseBodyFragment(payload.data.content)
        return document.select("[data-player]").mapNotNull { element ->
            val rawUrl = element.attr("data-player").takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            PlayerLink(
                url = absoluteUrl(rawUrl),
                type = PlayerType.EMBED,
                quality = null,
                headers = mapOf(HttpHeaders.Referrer to "$baseUrl/"),
                playerName = element.attr("data-provider-title").takeIf(String::isNotBlank),
                translation = element.attr("data-translation-title").takeIf(String::isNotBlank),
            )
        }.sortedBy { playerPriority(it.playerName) }
    }

    private suspend fun getPlayerContent(animeId: String): String {
        val response = client.get("$baseUrl/player/$animeId") {
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.Referrer, "$baseUrl/")
        }
        return response.bodyOrThrow<AnimeGoResponse>(name).data.content
    }

    private fun parseSearch(html: String): List<SearchResult> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select(".ani-grid__item").mapNotNull { card ->
            val link = card.selectFirst(".ani-grid__item-title a[href*=/anime/]")
                ?: return@mapNotNull null
            val path = pathOf(link.absUrl("href"))
            val id = ID_AT_END.find(path)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val metadata = card.select(".ani-grid__item-genres__link").map { it.text().trim() }
            SearchResult(
                id = id,
                russianName = link.attr("title").ifBlank { link.text() },
                originalName = card.selectFirst(".ani-grid__item-body > .fw-lighter")?.text(),
                type = metadata.firstOrNull(),
                year = metadata.firstNotNullOfOrNull(String::toIntOrNull),
            )
        }
    }

    private fun absoluteUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> url
    }

    private fun playerPriority(name: String?): Int = when (name?.lowercase()) {
        "aniboom" -> 0
        "cvh" -> 1
        "kodik" -> 2
        "sibnet" -> 3
        else -> 10
    }

    private data class SearchResult(
        val id: String,
        val russianName: String,
        val originalName: String?,
        val type: String?,
        val year: Int?,
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.70
        val ID_AT_END = Regex("""-(\d+)$""")
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
