package org.akkirrai.beakokit.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode

/** Canonical host-to-runtime payloads for the first external-source operations. */
object ExternalSourceRuntimePayloads {
    fun search(request: AnimeSearchRequest): JsonObject = buildJsonObject {
        put("query", request.query)
        put("limit", request.limit)
        put("offset", request.offset)
        put("sort", request.sort.name)
        putJsonArray("typeAliases") { request.typeAliases.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("statusAliases") { request.statusAliases.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("includedGenreAliases") {
            request.includedGenreAliases.forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("excludedGenreAliases") {
            request.excludedGenreAliases.forEach { add(JsonPrimitive(it)) }
        }
        put("yearFrom", request.yearFrom)
        put("yearTo", request.yearTo)
    }

    fun details(id: String): JsonObject = buildJsonObject {
        put("id", requireRuntimeId(id, "title"))
    }

    fun latest(limit: Int): JsonObject = buildJsonObject {
        put("limit", limit)
    }

    fun playbackGroups(title: AnimeTitle): JsonObject = buildJsonObject {
        put("titleId", requireRuntimeId(title.id, "title"))
    }

    fun playerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): JsonObject = buildJsonObject {
        put("titleId", requireRuntimeId(title.id, "title"))
        put("groupId", requireRuntimeId(group.id, "playback group"))
        put("episodeId", requireRuntimeId(episode.id, "episode"))
        put("episodeNumber", episode.number)
    }

    private fun requireRuntimeId(id: String, label: String): String {
        require(id.isNotBlank()) { "External source $label ID must not be blank" }
        return id
    }
}
