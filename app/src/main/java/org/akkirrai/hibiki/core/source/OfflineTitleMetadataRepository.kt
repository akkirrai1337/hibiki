package org.akkirrai.hibiki.core.source

import android.content.Context
import org.akkirrai.hibiki.core.database.HibikiDatabase
import org.akkirrai.hibiki.core.database.OfflineTitleEntity
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.AnimeTrailer
import org.json.JSONArray
import org.json.JSONObject

/** The last full description of each opened title, so its pages render offline. One row per title
 * in [HibikiDatabase], under the normalized title id. */
class OfflineTitleMetadataRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = HibikiDatabase.get(appContext).offlineTitleDao()

    init {
        importLegacyIfPresent()
    }

    fun save(anime: Anime) {
        val normalized = anime.copy(id = YummyIdMigration.normalizeTitleId(anime.id))
        dao.upsert(OfflineTitleEntity(normalized.id, encodeAnime(normalized).toString(), System.currentTimeMillis()))
    }

    fun get(id: String): Anime? {
        val candidates = YummyIdMigration.compatibleTitleIds(id)
        val rows = dao.find(candidates).associateBy(OfflineTitleEntity::titleId)
        val encoded = candidates.firstNotNullOfOrNull { rows[it]?.json } ?: return null
        return runCatching { decodeAnime(JSONObject(encoded)) }.getOrNull()
    }

    /** Moves the SharedPreferences store into the database, once, and deletes the file. */
    private fun importLegacyIfPresent() {
        synchronized(importLock) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val entries = prefs.all
            if (entries.isEmpty()) return
            val rows = entries.mapNotNull { (key, value) ->
                if (!key.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
                val titleId = YummyIdMigration.normalizeTitleId(key.removePrefix(KEY_PREFIX))
                if (titleId.isBlank()) null else OfflineTitleEntity(titleId, value, 0L)
            }
            dao.insertIfAbsent(rows)
            AppLogger.d(TAG, "imported ${rows.size} legacy offline titles")
            appContext.deleteSharedPreferences(PREFS_NAME)
        }
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
            put("bannerUrl", anime.bannerUrl)
            put("description", anime.description)
            put("genres", JSONArray(anime.genres))
            put("alternativeTitles", JSONArray(anime.alternativeTitles))
            put("ageRating", anime.ageRating)
            put("viewCount", anime.viewCount)
            put("screenshots", JSONArray(anime.screenshots))
            anime.trailer?.let { trailer ->
                put("trailer", JSONObject().apply {
                    put("id", trailer.id)
                    put("site", trailer.site)
                    put("thumbnailUrl", trailer.thumbnailUrl)
                    put("sourceUrl", trailer.sourceUrl)
                })
            }
            put("sourceMaterial", anime.sourceMaterial)
            put("studios", JSONArray(anime.studios))
            put("producers", JSONArray(anime.producers))
            put("ratings", JSONArray().apply {
                anime.ratings.forEach { rating ->
                    put(JSONObject().apply {
                        put("source", rating.source)
                        put("value", rating.value)
                        put("votes", rating.votes)
                    })
                }
            })
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
            bannerUrl = json.optString("bannerUrl").ifBlank { null },
            description = json.optString("description").ifBlank { null },
            genres = json.optJSONArray("genres").toStringList(),
            alternativeTitles = json.optJSONArray("alternativeTitles").toStringList(),
            ratings = json.optJSONArray("ratings").toRatingsList(),
            ageRating = json.optString("ageRating").ifBlank { null },
            viewCount = json.optLong("viewCount").takeIf { it > 0L },
            screenshots = json.optJSONArray("screenshots").toStringList(),
            trailer = json.optJSONObject("trailer")?.toAnimeTrailer(),
            sourceMaterial = json.optString("sourceMaterial").ifBlank { null },
            studios = json.optJSONArray("studios").toStringList(),
            producers = json.optJSONArray("producers").toStringList(),
        )
    }

    private fun JSONObject.toAnimeTrailer(): AnimeTrailer? {
        val id = optString("id").takeIf(String::isNotBlank) ?: return null
        val site = optString("site").takeIf(String::isNotBlank) ?: return null
        val sourceUrl = optString("sourceUrl").takeIf(String::isNotBlank) ?: return null
        return AnimeTrailer(
            id = id,
            site = site,
            thumbnailUrl = optString("thumbnailUrl").ifBlank { null },
            sourceUrl = sourceUrl,
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

    companion object {
        const val PREFS_NAME = "hibiki_offline_title_metadata"
        private const val KEY_PREFIX = "offline_title_"
        private const val TAG = "OfflineTitleMetadata"
        private val importLock = Any()
    }
}
