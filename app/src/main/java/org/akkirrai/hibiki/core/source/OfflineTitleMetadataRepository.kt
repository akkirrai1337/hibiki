package org.akkirrai.hibiki.core.source

import android.content.Context
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.json.JSONArray
import org.json.JSONObject

class OfflineTitleMetadataRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(anime: Anime) {
        val normalized = anime.copy(
            id = YummyIdMigration.normalizeTitleId(anime.id),
            franchiseAnime = anime.franchiseAnime.map { it.copy(id = YummyIdMigration.normalizeTitleId(it.id)) },
            relatedAnime = anime.relatedAnime.map { it.copy(id = YummyIdMigration.normalizeTitleId(it.id)) },
        )
        prefs.edit()
            .putString(key(normalized.id), encodeAnime(normalized).toString())
            .apply()
    }

    fun get(id: String): Anime? {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        val encoded = prefs.getString(key(normalizedId), null) ?: return null
        return runCatching { decodeAnime(JSONObject(encoded)) }.getOrNull()
    }

    private fun encodeAnime(anime: Anime): JSONObject {
        return JSONObject().apply {
            put("id", anime.id)
            put("title", anime.title)
            put("subtitle", anime.subtitle)
            put("episodesLabel", anime.episodesLabel)
            put("status", anime.status)
            put("nextEpisodeAt", anime.nextEpisodeAt)
            put("posterUrl", anime.posterUrl)
            put("posterFallbackUrl", anime.posterFallbackUrl)
            put("description", anime.description)
            put("genres", JSONArray(anime.genres))
            put("alternativeTitles", JSONArray(anime.alternativeTitles))
            put("ageRating", anime.ageRating)
            put("viewCount", anime.viewCount)
            put("screenshots", JSONArray(anime.screenshots))
            put("sourceMaterial", anime.sourceMaterial)
            put("studios", JSONArray(anime.studios))
            put("ratings", JSONArray().apply {
                anime.ratings.forEach { rating ->
                    put(JSONObject().apply {
                        put("source", rating.source)
                        put("value", rating.value)
                        put("votes", rating.votes)
                    })
                }
            })
            put("franchiseAnime", JSONArray().apply {
                anime.franchiseAnime.forEach { related -> put(encodeRelated(related)) }
            })
            put("relatedAnime", JSONArray().apply {
                anime.relatedAnime.forEach { related -> put(encodeRelated(related)) }
            })
        }
    }

    private fun encodeRelated(related: RelatedAnime): JSONObject {
        return JSONObject().apply {
            put("id", related.id)
            put("title", related.title)
            put("posterUrl", related.posterUrl)
            put("posterFallbackUrl", related.posterFallbackUrl)
            put("type", related.type)
            put("year", related.year)
            put("episodeCount", related.episodeCount)
        }
    }

    private fun decodeAnime(json: JSONObject): Anime {
        return Anime(
            id = YummyIdMigration.normalizeTitleId(json.getString("id")),
            title = json.optString("title"),
            subtitle = json.optString("subtitle"),
            episodesLabel = json.optString("episodesLabel"),
            status = json.optString("status"),
            nextEpisodeAt = json.optLong("nextEpisodeAt").takeIf { it > 0L },
            posterUrl = json.optString("posterUrl").ifBlank { null },
            posterFallbackUrl = json.optString("posterFallbackUrl").ifBlank { null },
            description = json.optString("description").ifBlank { null },
            genres = json.optJSONArray("genres").toStringList(),
            alternativeTitles = json.optJSONArray("alternativeTitles").toStringList(),
            ratings = json.optJSONArray("ratings").toRatingsList(),
            ageRating = json.optString("ageRating").ifBlank { null },
            viewCount = json.optLong("viewCount").takeIf { it > 0L },
            screenshots = json.optJSONArray("screenshots").toStringList(),
            sourceMaterial = json.optString("sourceMaterial").ifBlank { null },
            studios = json.optJSONArray("studios").toStringList(),
            franchiseAnime = json.optJSONArray("franchiseAnime").toRelatedAnimeList(),
            relatedAnime = json.optJSONArray("relatedAnime").toRelatedAnimeList(),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun JSONArray?.toRatingsList(): List<AnimeRating> = buildList {
        if (this@toRatingsList == null) return@buildList
        for (index in 0 until this@toRatingsList.length()) {
            val item = this@toRatingsList.optJSONObject(index) ?: continue
            val source = item.optString("source").takeIf(String::isNotBlank) ?: continue
            val value = item.optDouble("value")
            if (value > 0.0) {
                add(AnimeRating(source = source, value = value, votes = item.optInt("votes").takeIf { it > 0 }))
            }
        }
    }

    private fun JSONArray?.toRelatedAnimeList(): List<RelatedAnime> = buildList {
        if (this@toRelatedAnimeList == null) return@buildList
        for (index in 0 until this@toRelatedAnimeList.length()) {
            val item = this@toRelatedAnimeList.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
            add(
                RelatedAnime(
                    id = YummyIdMigration.normalizeTitleId(id),
                    title = item.optString("title"),
                    posterUrl = item.optString("posterUrl").ifBlank { null },
                    posterFallbackUrl = item.optString("posterFallbackUrl").ifBlank { null },
                    type = item.optString("type").ifBlank { null },
                    year = item.optInt("year").takeIf { it > 0 },
                    episodeCount = item.optInt("episodeCount").takeIf { it > 0 },
                )
            )
        }
    }

    private fun key(id: String): String = "offline_title_${YummyIdMigration.normalizeTitleId(id)}"

    companion object {
        const val PREFS_NAME = "hibiki_offline_title_metadata"
    }
}
