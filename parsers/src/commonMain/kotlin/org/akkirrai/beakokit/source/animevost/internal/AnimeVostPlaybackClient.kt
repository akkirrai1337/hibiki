package org.akkirrai.beakokit.source.animevost.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType

internal class AnimeVostPlaybackClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val apiBaseUrl: String,
) {
    suspend fun getEpisodes(titleId: String): List<Episode> = playlist(titleId).mapIndexed { index, item ->
        Episode(
            id = "$titleId:$index",
            number = EPISODE_NUMBER.find(item.name)?.groupValues?.get(1)?.toDoubleOrNull() ?: index + 1.0,
            title = item.name,
        )
    }

    suspend fun getPlayerLinks(episode: Episode): List<PlayerLink> {
        val (titleId, index) = episode.id.split(':', limit = 2).let { parts ->
            parts.getOrNull(0).orEmpty() to parts.getOrNull(1)?.toIntOrNull()
        }
        if (titleId.isBlank() || index == null) throw SourceException(
            "AnimeVost episode id is invalid: ${episode.id}",
            kind = SourceErrorKind.NOT_FOUND,
        )
        val item = playlist(titleId).getOrNull(index) ?: throw SourceException(
            "AnimeVost episode is unavailable: ${episode.id}",
            kind = SourceErrorKind.NOT_FOUND,
        )
        return listOfNotNull(
            item.hd?.toLink("720p"),
            item.standard?.toLink("480p"),
        ).distinctBy(PlayerLink::url)
    }

    private suspend fun playlist(titleId: String): List<PlaylistItem> {
        val numericId = TITLE_ID.find(titleId)?.groupValues?.get(1) ?: throw SourceException(
            "AnimeVost title id is invalid: $titleId",
            kind = SourceErrorKind.NOT_FOUND,
        )
        val response = client.post("${apiBaseUrl.trimEnd('/')}/v1/playlist") {
            header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
            header(HttpHeaders.Referrer, "${baseUrl.trimEnd('/')}/")
            setBody(FormDataContent(Parameters.build { append("id", numericId) }))
        }
        if (!response.status.isSuccess()) throw SourceException(
            "AnimeVost playlist returned HTTP ${response.status.value}",
            statusCode = response.status.value,
            kind = if (response.status.value in 400..499) SourceErrorKind.UNAVAILABLE else SourceErrorKind.NETWORK,
        )
        return runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonArray }.getOrElse { error ->
            throw SourceException("AnimeVost playlist is invalid", cause = error, kind = SourceErrorKind.PARSE)
        }.mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            PlaylistItem(
                name = name,
                standard = item["std"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank),
                hd = item["hd"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank),
            )
        }
    }

    private fun String.toLink(quality: String) = PlayerLink(
        url = this,
        type = PlayerType.DIRECT_MP4,
        quality = quality,
        headers = mapOf(HttpHeaders.Referrer to "${baseUrl.trimEnd('/')}/"),
        playerName = "AnimeVost",
    )

    private data class PlaylistItem(val name: String, val standard: String?, val hd: String?)

    private companion object {
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
        val EPISODE_NUMBER = Regex("^\\s*(\\d+(?:[.,]\\d+)?)")
        val TITLE_ID = Regex("/?(?:tip/[a-z-]+/)?(\\d+)-[^/]+\\.html")
    }
}
