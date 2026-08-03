package org.akkirrai.beakokit.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeTrailerTitle
import org.akkirrai.beakokit.model.CharacterTitle
import org.akkirrai.beakokit.model.RelatedAnimeTitle
import org.akkirrai.beakokit.model.TitleRating
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.VideoSegment
import org.akkirrai.beakokit.model.VideoSegmentType

/** Canonical JSON codec for the first external-source response payloads. */
object AnimeTitleRuntimePayloadCodec : ExternalSourcePlaybackRuntimePayloadCodec {
    override fun decodeSearch(payload: JsonObject): List<AnimeTitle> = payload.requiredArray("items")
        .map { it.jsonObject.decodeTitle() }

    override fun decodeDetails(payload: JsonObject): AnimeTitle = payload.decodeTitle()

    override fun decodePlaybackGroups(payload: JsonObject): List<PlaybackGroup> =
        payload.requiredArray("groups").map { group ->
            val value = group.jsonObject
            PlaybackGroup(
                id = value.requiredString("id"),
                title = value.requiredString("title"),
                qualityLabel = value.nullableString("qualityLabel"),
                episodes = value.requiredArray("episodes").map { episode ->
                    val item = episode.jsonObject
                    Episode(
                        id = item.requiredString("id"),
                        number = item.requiredPrimitive("number").content.toDouble(),
                        title = item.nullableString("title"),
                    )
                },
            )
        }

    override fun decodePlayerLinks(payload: JsonObject): List<PlayerLink> =
        payload.requiredArray("links").map { link ->
            val value = link.jsonObject
            PlayerLink(
                url = value.requiredString("url"),
                type = PlayerType.valueOf(value.requiredString("type")),
                quality = value.nullableString("quality"),
                headers = value.get("headers")?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                    ?: emptyMap(),
                playerName = value.nullableString("playerName"),
                translation = value.nullableString("translation"),
                segments = value.get("segments")?.jsonArray?.map { segment ->
                    val item = segment.jsonObject
                    VideoSegment(
                        type = VideoSegmentType.valueOf(item.requiredString("type")),
                        startMs = item.requiredPrimitive("startMs").content.toLong(),
                        endMs = item.requiredPrimitive("endMs").content.toLong(),
                    )
                } ?: emptyList(),
                videoId = value.nullableLong("videoId"),
            )
        }

    fun encodePlaybackGroups(groups: List<PlaybackGroup>): JsonObject = buildJsonObject {
        putJsonArray("groups") { groups.forEach { add(it.encodePlaybackGroup()) } }
    }

    fun encodePlayerLinks(links: List<PlayerLink>): JsonObject = buildJsonObject {
        putJsonArray("links") { links.forEach { add(it.encodePlayerLink()) } }
    }

    fun encodeSearch(items: List<AnimeTitle>): JsonObject = buildJsonObject {
        putJsonArray("items") { items.forEach { add(it.encodeTitle()) } }
    }

    fun encodeDetails(title: AnimeTitle): JsonObject = title.encodeTitle()

    private fun AnimeTitle.encodeTitle(): JsonObject = buildJsonObject {
        put("id", id)
        putNullable("russianName", russianName)
        putNullable("englishName", englishName)
        put("originalName", originalName)
        putNullable("japaneseName", japaneseName)
        putStrings("synonyms", synonyms)
        putNullable("year", year)
        putNullable("type", type)
        putNullable("episodeCount", episodeCount)
        putNullable("posterUrl", posterUrl)
        putNullable("status", status)
        putNullable("description", description)
        putNullable("nextEpisodeAt", nextEpisodeAt)
        putStrings("genres", genres)
        putJsonArray("ratings") { ratings.forEach { add(it.encodeRating()) } }
        putNullable("ageRating", ageRating)
        putNullable("viewCount", viewCount)
        putStrings("screenshots", screenshots)
        if (trailer == null) {
            put("trailer", null as String?)
        } else {
            putJsonObject("trailer") {
                put("id", trailer.id)
                put("site", trailer.site)
                putNullable("thumbnailUrl", trailer.thumbnailUrl)
                putNullable("sourceUrl", trailer.sourceUrl)
            }
        }
        putNullable("sourceMaterial", sourceMaterial)
        putStrings("studios", studios)
        putJsonArray("mainCharacters") { mainCharacters.forEach { add(it.encodeCharacter()) } }
        putJsonArray("similarAnime") { similarAnime.forEach { add(it.encodeRelated()) } }
        putJsonArray("franchiseAnime") { franchiseAnime.forEach { add(it.encodeRelated()) } }
        putJsonArray("relatedAnime") { relatedAnime.forEach { add(it.encodeRelated()) } }
        putNullable("season", season)
        putNullable("availableEpisodeCount", availableEpisodeCount)
        putNullable("posterFallbackUrl", posterFallbackUrl)
    }

    private fun PlaybackGroup.encodePlaybackGroup(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        putNullable("qualityLabel", qualityLabel)
        putJsonArray("episodes") {
            episodes.forEach { episode ->
                add(buildJsonObject {
                    put("id", episode.id)
                    put("number", episode.number)
                    putNullable("title", episode.title)
                })
            }
        }
    }

    private fun PlayerLink.encodePlayerLink(): JsonObject = buildJsonObject {
        put("url", url)
        put("type", type.name)
        putNullable("quality", quality)
        putJsonObject("headers") { headers.forEach { (key, value) -> put(key, value) } }
        putNullable("playerName", playerName)
        putNullable("translation", translation)
        putJsonArray("segments") {
            segments.forEach { segment ->
                add(buildJsonObject {
                    put("type", segment.type.name)
                    put("startMs", segment.startMs)
                    put("endMs", segment.endMs)
                })
            }
        }
        putNullable("videoId", videoId)
    }

    private fun JsonObject.decodeTitle(): AnimeTitle = AnimeTitle(
        id = requiredString("id"),
        russianName = nullableString("russianName"),
        englishName = nullableString("englishName"),
        originalName = requiredString("originalName"),
        japaneseName = nullableString("japaneseName"),
        synonyms = strings("synonyms"),
        year = nullableInt("year"),
        type = nullableString("type"),
        episodeCount = nullableInt("episodeCount"),
        posterUrl = nullableString("posterUrl"),
        status = nullableString("status"),
        description = nullableString("description"),
        nextEpisodeAt = nullableLong("nextEpisodeAt"),
        genres = strings("genres"),
        ratings = requiredArray("ratings").map { it.jsonObject.decodeRating() },
        ageRating = nullableString("ageRating"),
        viewCount = nullableLong("viewCount"),
        screenshots = strings("screenshots"),
        trailer = get("trailer")?.takeUnless { it is JsonNull }?.jsonObject?.decodeTrailer(),
        sourceMaterial = nullableString("sourceMaterial"),
        studios = strings("studios"),
        mainCharacters = requiredArray("mainCharacters").map { it.jsonObject.decodeCharacter() },
        similarAnime = requiredArray("similarAnime").map { it.jsonObject.decodeRelated() },
        franchiseAnime = requiredArray("franchiseAnime").map { it.jsonObject.decodeRelated() },
        relatedAnime = requiredArray("relatedAnime").map { it.jsonObject.decodeRelated() },
        season = nullableInt("season"),
        availableEpisodeCount = nullableInt("availableEpisodeCount"),
        posterFallbackUrl = nullableString("posterFallbackUrl"),
    )

    private fun TitleRating.encodeRating(): JsonObject = buildJsonObject {
        put("source", source)
        put("value", value)
        putNullable("votes", votes)
    }

    private fun JsonObject.decodeRating() = TitleRating(
        source = requiredString("source"),
        value = requiredPrimitive("value").content.toDouble(),
        votes = nullableInt("votes"),
    )

    private fun AnimeTrailerTitle.encodeTrailer(): JsonObject = buildJsonObject {
        put("id", id)
        put("site", site)
        putNullable("thumbnailUrl", thumbnailUrl)
        putNullable("sourceUrl", sourceUrl)
    }

    private fun JsonObject.decodeTrailer() = AnimeTrailerTitle(
        id = requiredString("id"),
        site = requiredString("site"),
        thumbnailUrl = nullableString("thumbnailUrl"),
        sourceUrl = nullableString("sourceUrl"),
    )

    private fun CharacterTitle.encodeCharacter(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        putNullable("posterUrl", posterUrl)
    }

    private fun JsonObject.decodeCharacter() = CharacterTitle(
        id = requiredString("id"),
        title = requiredString("title"),
        posterUrl = nullableString("posterUrl"),
    )

    private fun RelatedAnimeTitle.encodeRelated(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        putNullable("posterUrl", posterUrl)
        putNullable("type", type)
        putNullable("year", year)
        putNullable("episodeCount", episodeCount)
        putNullable("status", status)
    }

    private fun JsonObject.decodeRelated() = RelatedAnimeTitle(
        id = requiredString("id"),
        title = requiredString("title"),
        posterUrl = nullableString("posterUrl"),
        type = nullableString("type"),
        year = nullableInt("year"),
        episodeCount = nullableInt("episodeCount"),
        status = nullableString("status"),
    )

    private fun JsonObject.requiredString(key: String): String = requiredPrimitive(key).content

    private fun JsonObject.nullableString(key: String): String? = get(key)
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

    private fun JsonObject.nullableInt(key: String): Int? = get(key)
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content
        ?.toInt()

    private fun JsonObject.nullableLong(key: String): Long? = get(key)
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content
        ?.toLong()

    private fun JsonObject.requiredPrimitive(key: String): JsonPrimitive = get(key)
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?: error("Missing required runtime payload field: $key")

    private fun JsonObject.requiredArray(key: String): JsonArray = get(key)
        ?.jsonArray
        ?: error("Missing required runtime payload array: $key")

    private fun JsonObject.strings(key: String): List<String> = requiredArray(key)
        .map { it.jsonPrimitive.content }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putStrings(
        key: String,
        values: List<String>,
    ) {
        putJsonArray(key) { values.forEach { add(JsonPrimitive(it)) } }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
        put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Int?) {
        put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Long?) {
        put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Double?) {
        put(key, value)
    }
}
