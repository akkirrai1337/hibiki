package org.akkirrai.hibiki.core.source

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.database.LibraryDatabase
import org.akkirrai.hibiki.core.database.LibraryEntryEntity
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.AnimeTrailer
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's library, one row per title in [LibraryDatabase]. Titles are stored under their
 * normalized id; ids written by older versions are normalized once, when the legacy
 * SharedPreferences library is imported.
 */
class LibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = LibraryDatabase.get(appContext)
    private val dao = database.libraryDao()

    init {
        importLegacyLibraryIfPresent(appContext, dao)
    }

    fun getLibraryEntries(): List<LibraryEntry> {
        val entries = dao.all().flatMap { row ->
            val anime = row.animeJson?.let(::decodeStoredAnime) ?: return@flatMap emptyList()
            row.categorySet()
                .sortedBy(LibraryCategory::ordinal)
                .map { category -> LibraryEntry(anime = anime, category = category, addedAt = row.addedAt) }
        }
        // Recently added first is the only order that stays stable and meaningful as the library
        // grows into the hundreds.
        return entries.sortedByDescending { it.addedAt ?: 0L }
    }

    fun getLibraryEntries(category: LibraryCategory): List<LibraryEntry> {
        return getLibraryEntries().filter { it.category == category }
    }

    fun getLibraryCategory(id: String): LibraryCategory? {
        val categories = getLibraryCategories(id)
        return categories.firstOrNull { it != LibraryCategory.Saved }
            ?: LibraryCategory.Saved.takeIf { LibraryCategory.Saved in categories }
    }

    fun getLibraryCategories(id: String): Set<LibraryCategory> =
        dao.entry(YummyIdMigration.normalizeTitleId(id))?.categorySet().orEmpty()

    fun isInLibrary(id: String): Boolean {
        return getLibraryCategory(id) != null
    }

    fun saveToLibrary(anime: Anime, category: LibraryCategory) {
        val normalizedAnime = anime.normalizeIds()
        database.runInTransaction {
            val existing = dao.entry(normalizedAnime.id)
            val categories = existing?.categorySet().orEmpty().withSelectedCategory(category)
            dao.upsert(
                LibraryEntryEntity(
                    titleId = normalizedAnime.id,
                    animeJson = encodeAnime(normalizedAnime).toString(),
                    categories = categories.toStorageValue(),
                    addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    fun removeFromLibrary(id: String) {
        updateCategories(id) { categories -> categories.filterTo(mutableSetOf()) { it == LibraryCategory.Saved } }
    }

    fun removeSavedFromLibrary(id: String) {
        updateCategories(id) { categories -> categories - LibraryCategory.Saved }
    }

    private fun updateCategories(id: String, update: (Set<LibraryCategory>) -> Set<LibraryCategory>) {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        database.runInTransaction {
            val existing = dao.entry(normalizedId) ?: return@runInTransaction
            val categories = update(existing.categorySet())
            if (categories.isEmpty()) {
                dao.delete(normalizedId)
            } else {
                dao.upsert(existing.copy(categories = categories.toStorageValue()))
            }
        }
    }

    private fun Set<LibraryCategory>.withSelectedCategory(category: LibraryCategory): Set<LibraryCategory> {
        return if (category == LibraryCategory.Saved || category == LibraryCategory.Favorite) {
            this + category
        } else {
            filterTo(mutableSetOf()) {
                it == LibraryCategory.Saved || it == LibraryCategory.Favorite
            } + category
        }
    }

    companion object {
        const val PREFS_NAME = "hibiki_library"
        private const val TAG = "LibraryRepository"
        private val importLock = Any()

        /**
         * Moves a SharedPreferences library into the database and deletes the file. Checked on
         * every construction, not once: restoring a backup made by an older version brings the file
         * back, and its titles belong in the library too.
         */
        private fun importLegacyLibraryIfPresent(context: Context, dao: org.akkirrai.hibiki.core.database.LibraryDao) {
            synchronized(importLock) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.all.isEmpty()) return
                val imported = runCatching { LegacyLibraryReader(prefs).entries() }
                    .onFailure { AppLogger.w(TAG, "legacy library import failed, keeping the file", it) }
                    .getOrNull()
                    ?: return
                // Merged with what is already there: a title added since keeps its own row.
                val merged = imported.map { legacy ->
                    val current = dao.entry(legacy.titleId) ?: return@map legacy
                    current.copy(
                        animeJson = current.animeJson ?: legacy.animeJson,
                        categories = (parseCategories(current.categories) + parseCategories(legacy.categories)).toStorageValue(),
                        addedAt = listOfNotNull(current.addedAt, legacy.addedAt).minOrNull(),
                    )
                }
                dao.upsertAll(merged)
                AppLogger.d(TAG, "imported ${merged.size} legacy library entries")
                context.deleteSharedPreferences(PREFS_NAME)
            }
        }
    }
}

private fun LibraryEntryEntity.categorySet(): Set<LibraryCategory> = parseCategories(categories)

private fun parseCategories(value: String): Set<LibraryCategory> =
    value.split(',').mapNotNullTo(mutableSetOf()) { LibraryCategory.fromStorageValue(it.trim()) }

private fun Set<LibraryCategory>.toStorageValue(): String =
    sortedBy(LibraryCategory::ordinal).joinToString(",", transform = LibraryCategory::storageValue)

private fun decodeStoredAnime(encoded: String): Anime? =
    runCatching { decodeAnime(JSONObject(encoded)).normalizeIds() }.getOrNull()

private fun Anime.normalizeIds(): Anime {
    return copy(
        id = YummyIdMigration.normalizeTitleId(id),
        similarAnime = similarAnime.map(::normalizeRelated),
        franchiseAnime = franchiseAnime.map(::normalizeRelated),
        relatedAnime = relatedAnime.map(::normalizeRelated),
    )
}

private fun normalizeRelated(related: RelatedAnime): RelatedAnime {
    return related.copy(id = YummyIdMigration.normalizeTitleId(related.id))
}

/**
 * Reads the SharedPreferences library exactly the way the old repository did - ids under every
 * historical spelling, legacy favorites, the single-category key that predates category sets.
 */
private class LegacyLibraryReader(private val prefs: SharedPreferences) {
    fun entries(): List<LibraryEntryEntity> {
        val ids = (stringSet(LIBRARY_IDS_KEY) + stringSet(LEGACY_FAVORITE_IDS_KEY))
            .map(YummyIdMigration::normalizeTitleId)
            .filter(String::isNotBlank)
            .distinct()
        return ids.mapNotNull { id ->
            val categories = categories(id)
            val animeJson = storedAnimeJson(id)
            if (categories.isEmpty() && animeJson == null) return@mapNotNull null
            LibraryEntryEntity(
                titleId = id,
                animeJson = animeJson,
                categories = categories.toStorageValue(),
                addedAt = addedAt(id),
            )
        }
    }

    private fun stringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty()

    private fun categories(id: String): Set<LibraryCategory> {
        val stored = compatible(id, "library_categories_")
            .firstNotNullOfOrNull { key -> prefs.getStringSet(key, null) }
            ?.mapNotNullTo(mutableSetOf(), LibraryCategory::fromStorageValue)
            .orEmpty()
        if (stored.isNotEmpty()) return stored
        return buildSet {
            compatible(id, "library_category_")
                .firstNotNullOfOrNull { key -> prefs.getString(key, null) }
                ?.let(LibraryCategory::fromStorageValue)
                ?.let(::add)
            if (compatible(id, "favorite_").any(prefs::contains)) add(LibraryCategory.Favorite)
        }
    }

    private fun storedAnimeJson(id: String): String? =
        (compatible(id, "library_anime_") + compatible(id, "favorite_"))
            .firstNotNullOfOrNull { key -> runCatching { prefs.getString(key, null) }.getOrNull() }

    private fun addedAt(id: String): Long? =
        compatible(id, "library_added_at_")
            .firstNotNullOfOrNull { key -> prefs.takeIf { it.contains(key) }?.getLong(key, 0L) }
            ?.takeIf { it > 0L }

    private fun compatible(id: String, prefix: String): List<String> =
        YummyIdMigration.compatibleTitleIds(id).map { prefix + it }

    private companion object {
        const val LIBRARY_IDS_KEY = "library_ids"
        const val LEGACY_FAVORITE_IDS_KEY = "favorite_ids"
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
        put("description", anime.description)
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
        put("ratings", JSONArray().apply {
            anime.ratings.forEach { rating ->
                put(JSONObject().apply {
                    put("source", rating.source)
                    put("value", rating.value)
                    put("votes", rating.votes)
                })
            }
        })
        put("genres", JSONArray(anime.genres))
        put("studios", JSONArray(anime.studios))
        put("franchiseAnime", JSONArray().apply { anime.franchiseAnime.forEach { put(encodeRelated(it)) } })
        put("similarAnime", JSONArray().apply { anime.similarAnime.forEach { put(encodeRelated(it)) } })
        put("relatedAnime", JSONArray().apply { anime.relatedAnime.forEach { put(encodeRelated(it)) } })
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
        put("status", related.status)
    }
}

private fun decodeAnime(json: JSONObject): Anime {
    return Anime(
        id = json.getString("id"),
        title = json.optString("title"),
        subtitle = json.optString("subtitle"),
        episodesLabel = json.optString("episodesLabel"),
        status = json.optString("status"),
        nextEpisodeAt = json.optLong("nextEpisodeAt").takeIf { it > 0L },
        posterUrl = json.optString("posterUrl").ifBlank { null },
        posterFallbackUrl = json.optString("posterFallbackUrl").ifBlank { null },
        description = json.optString("description").ifBlank { null },
        alternativeTitles = json.optJSONArray("alternativeTitles").toStringList(),
        ratings = json.optJSONArray("ratings").toRatingsList(),
        ageRating = json.optString("ageRating").ifBlank { null },
        viewCount = json.optLong("viewCount").takeIf { it > 0L },
        screenshots = json.optJSONArray("screenshots").toStringList(),
        trailer = json.optJSONObject("trailer")?.toAnimeTrailer(),
        sourceMaterial = json.optString("sourceMaterial").ifBlank { null },
        genres = json.optJSONArray("genres").toStringList(),
        studios = json.optJSONArray("studios").toStringList(),
        similarAnime = json.optJSONArray("similarAnime").toRelatedAnimeList(),
        franchiseAnime = json.optJSONArray("franchiseAnime").toRelatedAnimeList(),
        relatedAnime = json.optJSONArray("relatedAnime").toRelatedAnimeList(),
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
            add(optString(index))
        }
    }.filter(String::isNotBlank)
}

private fun JSONArray?.toRelatedAnimeList(): List<RelatedAnime> = buildList {
    if (this@toRelatedAnimeList == null) return@buildList
    for (index in 0 until this@toRelatedAnimeList.length()) {
        val item = this@toRelatedAnimeList.optJSONObject(index) ?: continue
        add(
            RelatedAnime(
                id = item.optString("id"),
                title = item.optString("title"),
                posterUrl = item.optString("posterUrl").ifBlank { null },
                posterFallbackUrl = item.optString("posterFallbackUrl").ifBlank { null },
                type = item.optString("type").ifBlank { null },
                year = item.optInt("year").takeIf { it > 0 },
                episodeCount = item.optInt("episodeCount").takeIf { it > 0 },
                status = item.optString("status").ifBlank { null },
            )
        )
    }
}

private fun JSONArray?.toRatingsList(): List<AnimeRating> = buildList {
    if (this@toRatingsList == null) return@buildList
    for (index in 0 until this@toRatingsList.length()) {
        val item = this@toRatingsList.optJSONObject(index) ?: continue
        val source = item.optString("source").takeIf(String::isNotBlank) ?: continue
        val value = item.optDouble("value")
        if (value <= 0.0) continue
        add(
            AnimeRating(
                source = source,
                value = value,
                votes = item.optInt("votes").takeIf { it > 0 },
            )
        )
    }
}

data class LibraryEntry(
    val anime: Anime,
    val category: LibraryCategory,
    val addedAt: Long? = null,
)

enum class LibraryCategory(
    val storageValue: String,
    @param:StringRes val labelResId: Int,
) {
    Watching("watching", R.string.library_category_watching),
    Planned("planned", R.string.library_category_planned),
    Completed("completed", R.string.library_category_completed),
    Dropped("dropped", R.string.library_category_dropped),
    OnHold("on_hold", R.string.library_category_on_hold),
    Favorite("favorite", R.string.library_category_favorite),
    Saved("saved", R.string.library_category_saved);

    companion object {
        fun fromStorageValue(value: String): LibraryCategory? {
            return entries.firstOrNull { it.storageValue == value }
        }
    }
}
