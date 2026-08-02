package org.akkirrai.hibiki.desktop

import java.security.MessageDigest
import java.util.Base64
import java.util.prefs.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeRating
import org.akkirrai.hibiki.shared.model.AnimeTrailer
import org.akkirrai.hibiki.shared.model.RelatedAnime

/** Desktop persistent adapter matching the Android library category semantics. */
internal class DesktopLibraryRepository(
    private val preferences: Preferences = Preferences.userNodeForPackage(DesktopLibraryRepository::class.java),
) : LibraryRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getEntries(): List<LibraryEntry> = getLibraryEntries()

    override fun getLibraryCategory(id: String): LibraryCategory? = getCategories(id)
        .firstOrNull { it != LibraryCategory.Saved }
        ?: LibraryCategory.Saved.takeIf { LibraryCategory.Saved in getCategories(id) }

    override fun saveToLibrary(anime: Anime, category: LibraryCategory) {
        val id = anime.id
        val existing = getCategories(id)
        val categories = if (category == LibraryCategory.Saved || category == LibraryCategory.Favorite) {
            existing + category
        } else {
            existing.filterTo(linkedSetOf()) {
                it == LibraryCategory.Saved || it == LibraryCategory.Favorite
            } + category
        }
        val addedAt = preferences.getLong(addedAtKey(id), 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
        preferences.put(animeKey(id), json.encodeToString(StoredAnime.from(anime)))
        preferences.put(categoriesKey(id), categories.joinToString(CATEGORY_SEPARATOR) { it.storageValue })
        preferences.putLong(addedAtKey(id), addedAt)
        writeIds(readIds() + id)
        flush()
    }

    override fun removeFromLibrary(id: String) {
        val remaining = getCategories(id).filterTo(linkedSetOf()) { it == LibraryCategory.Saved }
        if (remaining.isEmpty()) {
            removeRecord(id)
        } else {
            preferences.put(categoriesKey(id), remaining.joinToString(CATEGORY_SEPARATOR) { it.storageValue })
            flush()
        }
    }

    override fun removeSavedFromLibrary(id: String) {
        val remaining = getCategories(id).filterTo(linkedSetOf()) { it != LibraryCategory.Saved }
        if (remaining.isEmpty()) {
            removeRecord(id)
        } else {
            preferences.put(categoriesKey(id), remaining.joinToString(CATEGORY_SEPARATOR) { it.storageValue })
            flush()
        }
    }

    private fun getLibraryEntries(): List<LibraryEntry> = readIds().flatMap { id ->
        val anime = preferences.get(animeKey(id), null)
            ?.let { encoded -> runCatching { json.decodeFromString<StoredAnime>(encoded).toAnime() }.getOrNull() }
            ?: return@flatMap emptyList()
        getCategories(id)
            .sortedBy(LibraryCategory::ordinal)
            .map { category ->
                LibraryEntry(
                    anime = anime,
                    category = category,
                    addedAt = preferences.getLong(addedAtKey(id), 0L).takeIf { it > 0L },
                )
            }
    }

    private fun getCategories(id: String): Set<LibraryCategory> = preferences.get(categoriesKey(id), "")
        .split(CATEGORY_SEPARATOR)
        .mapNotNull(LibraryCategory::fromStorageValue)
        .toSet()

    private fun readIds(): Set<String> = preferences.get(LIBRARY_IDS_KEY, "")
        .split(ID_SEPARATOR)
        .mapNotNull { encoded ->
            if (encoded.isBlank()) null else decodeId(encoded)
        }
        .toSet()

    private fun writeIds(ids: Set<String>) {
        preferences.put(
            LIBRARY_IDS_KEY,
            ids.joinToString(ID_SEPARATOR) { encodeId(it) },
        )
    }

    private fun removeRecord(id: String) {
        preferences.remove(animeKey(id))
        preferences.remove(categoriesKey(id))
        preferences.remove(addedAtKey(id))
        writeIds(readIds() - id)
        flush()
    }

    private fun flush() = preferences.flush()

    private fun encodeId(id: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(id.toByteArray(Charsets.UTF_8))

    private fun decodeId(encoded: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
    }.getOrNull()

    private fun storageHash(id: String): String = MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { "%02x".format(it) }

    private fun animeKey(id: String): String = "library_anime_${storageHash(id)}"

    private fun categoriesKey(id: String): String = "library_categories_${storageHash(id)}"

    private fun addedAtKey(id: String): String = "library_added_at_${storageHash(id)}"

    private companion object {
        const val LIBRARY_IDS_KEY = "library_ids"
        const val ID_SEPARATOR = ","
        const val CATEGORY_SEPARATOR = "\u001F"
    }
}

@Serializable
private data class StoredAnime(
    val id: String,
    val title: String,
    val subtitle: String,
    val episodesLabel: String,
    val status: String,
    val nextEpisodeAt: Long? = null,
    val posterUrl: String? = null,
    val posterFallbackUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val alternativeTitles: List<String> = emptyList(),
    val ratings: List<StoredRating> = emptyList(),
    val ageRating: String? = null,
    val viewCount: Long? = null,
    val screenshots: List<String> = emptyList(),
    val trailer: StoredTrailer? = null,
    val sourceMaterial: String? = null,
    val studios: List<String> = emptyList(),
    val similarAnime: List<StoredRelatedAnime> = emptyList(),
    val franchiseAnime: List<StoredRelatedAnime> = emptyList(),
    val relatedAnime: List<StoredRelatedAnime> = emptyList(),
    val releaseDate: String? = null,
) {
    fun toAnime(): Anime = Anime(
        id = id,
        title = title,
        subtitle = subtitle,
        episodesLabel = episodesLabel,
        status = status,
        nextEpisodeAt = nextEpisodeAt,
        posterUrl = posterUrl,
        posterFallbackUrl = posterFallbackUrl,
        description = description,
        genres = genres,
        alternativeTitles = alternativeTitles,
        ratings = ratings.map { it.toRating() },
        ageRating = ageRating,
        viewCount = viewCount,
        screenshots = screenshots,
        trailer = trailer?.toTrailer(),
        sourceMaterial = sourceMaterial,
        studios = studios,
        similarAnime = similarAnime.map(StoredRelatedAnime::toRelatedAnime),
        franchiseAnime = franchiseAnime.map(StoredRelatedAnime::toRelatedAnime),
        relatedAnime = relatedAnime.map(StoredRelatedAnime::toRelatedAnime),
        releaseDate = releaseDate,
    )

    companion object {
        fun from(anime: Anime): StoredAnime = StoredAnime(
            id = anime.id,
            title = anime.title,
            subtitle = anime.subtitle,
            episodesLabel = anime.episodesLabel,
            status = anime.status,
            nextEpisodeAt = anime.nextEpisodeAt,
            posterUrl = anime.posterUrl,
            posterFallbackUrl = anime.posterFallbackUrl,
            description = anime.description,
            genres = anime.genres,
            alternativeTitles = anime.alternativeTitles,
            ratings = anime.ratings.map(StoredRating::from),
            ageRating = anime.ageRating,
            viewCount = anime.viewCount,
            screenshots = anime.screenshots,
            trailer = anime.trailer?.let(StoredTrailer::from),
            sourceMaterial = anime.sourceMaterial,
            studios = anime.studios,
            similarAnime = anime.similarAnime.map(StoredRelatedAnime::from),
            franchiseAnime = anime.franchiseAnime.map(StoredRelatedAnime::from),
            relatedAnime = anime.relatedAnime.map(StoredRelatedAnime::from),
            releaseDate = anime.releaseDate,
        )
    }
}

@Serializable
private data class StoredRating(val source: String, val value: Double, val votes: Int? = null) {
    fun toRating() = AnimeRating(source, value, votes)

    companion object {
        fun from(rating: AnimeRating) = StoredRating(rating.source, rating.value, rating.votes)
    }
}

@Serializable
private data class StoredTrailer(
    val id: String,
    val site: String,
    val thumbnailUrl: String? = null,
    val sourceUrl: String? = null,
) {
    fun toTrailer() = AnimeTrailer(id, site, thumbnailUrl, sourceUrl)

    companion object {
        fun from(trailer: AnimeTrailer) = StoredTrailer(trailer.id, trailer.site, trailer.thumbnailUrl, trailer.sourceUrl)
    }
}

@Serializable
private data class StoredRelatedAnime(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val posterFallbackUrl: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val status: String? = null,
) {
    fun toRelatedAnime() = RelatedAnime(id, title, posterUrl, posterFallbackUrl, type, year, episodeCount, status)

    companion object {
        fun from(anime: RelatedAnime) = StoredRelatedAnime(
            anime.id,
            anime.title,
            anime.posterUrl,
            anime.posterFallbackUrl,
            anime.type,
            anime.year,
            anime.episodeCount,
            anime.status,
        )
    }
}
