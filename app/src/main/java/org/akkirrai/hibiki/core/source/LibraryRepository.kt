package org.akkirrai.hibiki.core.source

import android.content.Context
import androidx.annotation.StringRes
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.AnimeTrailer
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.json.JSONArray
import org.json.JSONObject

class LibraryRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLibraryEntries(): List<LibraryEntry> {
        val ids = getLibraryIds()
        val legacyFavoriteIds = getLegacyFavoriteIds()
        val mergedIds = (ids + legacyFavoriteIds)
            .map(YummyIdMigration::normalizeTitleId)
            .distinct()

        return mergedIds.flatMap { id ->
            val anime = getStoredAnime(id) ?: return@flatMap emptyList()
            getLibraryCategories(id)
                .sortedBy(LibraryCategory::ordinal)
                .map { category ->
                    LibraryEntry(
                        anime = anime,
                        category = category,
                        addedAt = getLibraryAddedAt(id),
                    )
                }
        }
    }

    fun getLibraryEntries(category: LibraryCategory): List<LibraryEntry> {
        return getLibraryEntries().filter { it.category == category }
    }

    fun getLibraryAnime(category: LibraryCategory): List<Anime> {
        return getLibraryEntries(category).map(LibraryEntry::anime)
    }

    fun getLibraryCategory(id: String): LibraryCategory? {
        val categories = getLibraryCategories(id)
        return categories.firstOrNull { it != LibraryCategory.Saved }
            ?: LibraryCategory.Saved.takeIf { LibraryCategory.Saved in categories }
    }

    fun getLibraryCategories(id: String): Set<LibraryCategory> {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        val storedCategories = libraryCategorySetKeys(id)
            .firstNotNullOfOrNull { key -> prefs.getStringSet(key, null) }
            ?.mapNotNull(LibraryCategory::fromStorageValue)
            ?.toSet()
            .orEmpty()
        if (storedCategories.isNotEmpty()) {
            return storedCategories
        }

        return buildSet {
            libraryCategoryKeys(id)
                .firstNotNullOfOrNull { key -> prefs.getString(key, null) }
                ?.let(LibraryCategory::fromStorageValue)
                ?.let(::add)
            if (favoriteKeys(normalizedId).any(prefs::contains)) {
                add(LibraryCategory.Favorite)
            }
        }
    }

    fun isInLibrary(id: String): Boolean {
        return getLibraryCategory(id) != null
    }

    fun saveToLibrary(anime: Anime, category: LibraryCategory) {
        val normalizedAnime = anime.normalizeIds()
        val ids = getLibraryIds().toMutableSet()
        ids += normalizedAnime.id
        val categories = getLibraryCategories(normalizedAnime.id).withSelectedCategory(category)

        prefs.edit()
            .putStringSet(LIBRARY_IDS_KEY, ids)
            .removeLegacyFavoriteEntries(anime.id)
            .removeLegacyLibraryCategoryEntries(anime.id)
            .putString(libraryAnimeKey(normalizedAnime.id), encodeAnime(normalizedAnime).toString())
            .putStringSet(libraryCategorySetKey(normalizedAnime.id), categories.toStorageValues())
            .putLongIfAbsent(libraryAddedAtKey(normalizedAnime.id), System.currentTimeMillis())
            .apply()
    }

    /** Imports an entry which does not exist locally while preserving its remote timestamp. */
    fun importLibraryEntry(
        anime: Anime,
        categories: Set<LibraryCategory>,
        addedAt: Long?,
    ) {
        if (categories.isEmpty()) return
        val normalizedAnime = anime.normalizeIds()
        if (getLibraryCategories(normalizedAnime.id).isNotEmpty()) return

        val ids = getLibraryIds().toMutableSet().apply { add(normalizedAnime.id) }
        val editor = prefs.edit()
            .putStringSet(LIBRARY_IDS_KEY, ids)
            .putString(libraryAnimeKey(normalizedAnime.id), encodeAnime(normalizedAnime).toString())
            .putStringSet(libraryCategorySetKey(normalizedAnime.id), categories.toStorageValues())
        val normalizedAddedAt = addedAt?.takeIf { it > 0L } ?: System.currentTimeMillis()
        editor.putLong(libraryAddedAtKey(normalizedAnime.id), normalizedAddedAt).apply()
    }

    fun replacePrimaryCategory(id: String, category: LibraryCategory) {
        require(category !in setOf(LibraryCategory.Favorite, LibraryCategory.Saved))
        val categories = getLibraryCategories(id)
            .filterTo(linkedSetOf()) { it == LibraryCategory.Favorite || it == LibraryCategory.Saved }
            .apply { add(category) }
        saveCategoriesOrRemove(id, categories)
    }

    fun addSupplementalCategory(id: String, category: LibraryCategory) {
        require(category == LibraryCategory.Favorite || category == LibraryCategory.Saved)
        saveCategoriesOrRemove(id, getLibraryCategories(id) + category)
    }

    fun removeFromLibrary(id: String) {
        val remainingCategories = getLibraryCategories(id)
            .filterTo(mutableSetOf()) { it == LibraryCategory.Saved }
        saveCategoriesOrRemove(id, remainingCategories)
    }

    fun removeSavedFromLibrary(id: String) {
        val remainingCategories = getLibraryCategories(id) - LibraryCategory.Saved
        saveCategoriesOrRemove(id, remainingCategories)
    }

    fun getFavorites(): List<Anime> {
        return getLibraryAnime(LibraryCategory.Favorite)
    }

    fun getFavorite(id: String): Anime? {
        return getStoredAnime(id).takeIf { LibraryCategory.Favorite in getLibraryCategories(id) }
    }

    fun isFavorite(id: String): Boolean {
        return LibraryCategory.Favorite in getLibraryCategories(id)
    }

    fun addFavorite(anime: Anime) {
        saveToLibrary(anime, LibraryCategory.Favorite)
    }

    fun removeFavorite(id: String) {
        if (LibraryCategory.Favorite in getLibraryCategories(id)) {
            saveCategoriesOrRemove(id, getLibraryCategories(id) - LibraryCategory.Favorite)
        }
    }

    private fun getStoredAnime(id: String): Anime? {
        val encoded = libraryAnimeKeys(id)
            .firstNotNullOfOrNull { key -> prefs.getString(key, null) }
            ?: favoriteKeys(id).firstNotNullOfOrNull { key -> prefs.getString(key, null) }
            ?: return null
        val anime = runCatching { decodeAnime(JSONObject(encoded)) }.getOrNull() ?: return null
        return anime.copy(id = YummyIdMigration.normalizeTitleId(anime.id))
    }

    private fun getLibraryIds(): Set<String> {
        return prefs.getStringSet(LIBRARY_IDS_KEY, emptySet()).orEmpty()
            .map(YummyIdMigration::normalizeTitleId)
            .toSet()
    }

    private fun getLegacyFavoriteIds(): Set<String> {
        return prefs.getStringSet(LEGACY_FAVORITE_IDS_KEY, emptySet()).orEmpty()
            .map(YummyIdMigration::normalizeTitleId)
            .toSet()
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
            put("franchiseAnime", JSONArray().apply {
                anime.franchiseAnime.forEach { related ->
                    put(JSONObject().apply {
                        put("id", related.id)
                        put("title", related.title)
                        put("posterUrl", related.posterUrl)
                        put("posterFallbackUrl", related.posterFallbackUrl)
                        put("type", related.type)
                        put("year", related.year)
                        put("episodeCount", related.episodeCount)
                        put("status", related.status)
                    })
                }
            })
            put("similarAnime", JSONArray().apply {
                anime.similarAnime.forEach { related ->
                    put(JSONObject().apply {
                        put("id", related.id)
                        put("title", related.title)
                        put("posterUrl", related.posterUrl)
                        put("posterFallbackUrl", related.posterFallbackUrl)
                        put("type", related.type)
                        put("year", related.year)
                        put("episodeCount", related.episodeCount)
                        put("status", related.status)
                    })
                }
            })
            put("relatedAnime", JSONArray().apply {
                anime.relatedAnime.forEach { related ->
                    put(JSONObject().apply {
                        put("id", related.id)
                        put("title", related.title)
                        put("posterUrl", related.posterUrl)
                        put("posterFallbackUrl", related.posterFallbackUrl)
                        put("type", related.type)
                        put("year", related.year)
                        put("episodeCount", related.episodeCount)
                        put("status", related.status)
                    })
                }
            })
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

    private fun libraryAnimeKey(id: String): String = "library_anime_$id"

    private fun libraryCategoryKey(id: String): String = "library_category_$id"

    private fun libraryCategorySetKey(id: String): String = "library_categories_$id"

    private fun libraryAddedAtKey(id: String): String = "library_added_at_$id"

    private fun favoriteKey(id: String): String = "favorite_$id"

    private fun getLibraryAddedAt(id: String): Long? {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        return listOf(libraryAddedAtKey(normalizedId), libraryAddedAtKey(id))
            .firstNotNullOfOrNull { key -> prefs.takeIf { it.contains(key) }?.getLong(key, 0L) }
            ?.takeIf { it > 0L }
    }

    private fun libraryAnimeKeys(id: String): List<String> {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        return listOf(libraryAnimeKey(normalizedId), libraryAnimeKey(id)).distinct()
    }

    private fun libraryCategoryKeys(id: String): List<String> {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        return listOf(libraryCategoryKey(normalizedId), libraryCategoryKey(id)).distinct()
    }

    private fun libraryCategorySetKeys(id: String): List<String> {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        return listOf(libraryCategorySetKey(normalizedId), libraryCategorySetKey(id)).distinct()
    }

    private fun favoriteKeys(id: String): List<String> {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        return listOf(favoriteKey(normalizedId), favoriteKey(id)).distinct()
    }

    private fun saveCategoriesOrRemove(
        id: String,
        categories: Set<LibraryCategory>,
    ) {
        val normalizedId = YummyIdMigration.normalizeTitleId(id)
        // Read before legacy keys are removed so favorite-only records are migrated
        // instead of losing their only serialized Anime payload.
        val storedAnime = getStoredAnime(id)?.normalizeIds()
        val ids = getLibraryIds().toMutableSet()
        val editor = prefs.edit()
            .removeLegacyFavoriteEntries(id)
            .removeLegacyLibraryCategoryEntries(id)

        if (categories.isEmpty()) {
            ids -= normalizedId
            editor
                .putStringSet(LIBRARY_IDS_KEY, ids)
            .removeLibraryEntries(id)
                .apply()
            return
        }

        ids += normalizedId
        editor
            .putStringSet(LIBRARY_IDS_KEY, ids)
            .putStringSet(libraryCategorySetKey(normalizedId), categories.toStorageValues())
            .apply {
                storedAnime?.let { anime ->
                    putString(libraryAnimeKey(normalizedId), encodeAnime(anime).toString())
                }
            }
            .apply()
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

    private fun Set<LibraryCategory>.toStorageValues(): Set<String> {
        return mapTo(mutableSetOf(), LibraryCategory::storageValue)
    }

    private fun android.content.SharedPreferences.Editor.removeLibraryEntries(id: String):
        android.content.SharedPreferences.Editor {
        libraryAnimeKeys(id).forEach(::remove)
        libraryCategoryKeys(id).forEach(::remove)
        libraryCategorySetKeys(id).forEach(::remove)
        listOf(libraryAddedAtKey(YummyIdMigration.normalizeTitleId(id)), libraryAddedAtKey(id))
            .distinct()
            .forEach(::remove)
        return this
    }

    private fun android.content.SharedPreferences.Editor.putLongIfAbsent(
        key: String,
        value: Long,
    ): android.content.SharedPreferences.Editor {
        if (!prefs.contains(key)) putLong(key, value)
        return this
    }

    private fun android.content.SharedPreferences.Editor.removeLegacyLibraryCategoryEntries(id: String):
        android.content.SharedPreferences.Editor {
        libraryCategoryKeys(id).forEach(::remove)
        return this
    }

    private fun android.content.SharedPreferences.Editor.removeLegacyFavoriteEntries(id: String):
        android.content.SharedPreferences.Editor {
        favoriteKeys(id).forEach(::remove)
        val legacyIds = getLegacyFavoriteIds().toMutableSet()
        legacyIds -= YummyIdMigration.normalizeTitleId(id)
        putStringSet(LEGACY_FAVORITE_IDS_KEY, legacyIds)
        return this
    }

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

    companion object {
        const val PREFS_NAME = "hibiki_library"
        private const val LIBRARY_IDS_KEY = "library_ids"
        private const val LEGACY_FAVORITE_IDS_KEY = "favorite_ids"
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
