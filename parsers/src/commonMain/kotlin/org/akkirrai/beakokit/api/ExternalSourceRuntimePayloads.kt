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
    fun search(request: AnimeSearchRequest): JsonObject {
        require(request.limit > 0) { "External source search limit must be positive" }
        require(request.offset >= 0) { "External source search offset must not be negative" }
        require(request.yearFrom == null || request.yearTo == null || request.yearFrom <= request.yearTo) {
            "External source search yearFrom must not be greater than yearTo"
        }
        return buildJsonObject {
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
    }

    fun details(id: String): JsonObject = buildJsonObject {
        put("id", requireRuntimeId(id, "title"))
    }

    fun latest(limit: Int): JsonObject {
        require(limit > 0) { "External source latest limit must be positive" }
        return buildJsonObject {
            put("limit", limit)
        }
    }

    fun playbackGroups(title: AnimeTitle): JsonObject = buildJsonObject {
        put("titleId", requireRuntimeId(title.id, "title"))
    }

    fun playerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): JsonObject = buildJsonObject {
        require(episode.number.isFinite()) { "External playback episode number must be finite" }
        put("titleId", requireRuntimeId(title.id, "title"))
        put("groupId", requireRuntimeId(group.id, "playback group"))
        put("episodeId", requireRuntimeId(episode.id, "episode"))
        put("episodeNumber", episode.number)
    }

    private fun requireRuntimeId(id: String, label: String): String {
        require(id.isNotBlank()) { "External source $label ID must not be blank" }
        require('\r' !in id && '\n' !in id) {
            "External source $label ID must not contain CR or LF"
        }
        return id
    }
}
