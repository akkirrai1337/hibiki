package org.akkirrai.hibiki.shared.library

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeRating
import org.akkirrai.hibiki.shared.model.AnimeTrailer
import org.akkirrai.hibiki.shared.model.RelatedAnime
import platform.Foundation.NSUserDefaults

internal class IosLibraryRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : LibraryRepository {
    override suspend fun getEntries(): List<LibraryEntry> = storedRecords().flatMap { record ->
        record.categories.map { category ->
            LibraryEntry(record.anime, category, record.addedAt)
        }
    }

    override fun getLibraryCategory(id: String): LibraryCategory? = storedRecords()
        .firstOrNull { it.anime.id == id }
        ?.categories
        ?.firstOrNull { it != LibraryCategory.Saved }
        ?: storedRecords().firstOrNull { it.anime.id == id }?.categories?.firstOrNull()

    override fun saveToLibrary(anime: Anime, category: LibraryCategory) {
        val existing = storedRecords().firstOrNull { it.anime.id == anime.id }
        val categories = existing?.categories.orEmpty()
            .filter { it == LibraryCategory.Favorite || it == LibraryCategory.Saved }
            .toMutableSet()
            .apply { add(category) }
        saveRecord(
            StoredLibraryRecord(
                anime = anime,
                categories = categories.toList(),
                addedAt = existing?.addedAt ?: currentEpochMillis(),
            ),
        )
    }

    override fun removeFromLibrary(id: String) {
        val existing = storedRecords().firstOrNull { it.anime.id == id } ?: return
        val remaining = existing.categories.filter { it == LibraryCategory.Favorite || it == LibraryCategory.Saved }
        if (remaining.isEmpty()) {
            removeRecord(id)
        } else {
            saveRecord(existing.copy(categories = remaining))
        }
    }

    private fun storedRecords(): List<StoredLibraryRecord> = defaults
        .arrayForKey(LIBRARY_IDS_KEY)
        .orEmpty()
        .filterIsInstance<String>()
        .mapNotNull { id ->
            readMap(defaults.objectForKey(recordKey(id)))?.toStoredRecord()
        }

    private fun saveRecord(record: StoredLibraryRecord) {
        val ids = defaults.arrayForKey(LIBRARY_IDS_KEY).orEmpty().filterIsInstance<String>().toMutableList()
        if (record.anime.id !in ids) ids += record.anime.id
        defaults.setObject(ids, forKey = LIBRARY_IDS_KEY)
        defaults.setObject(record.toStorageMap(), forKey = recordKey(record.anime.id))
    }

    private fun removeRecord(id: String) {
        val ids = defaults.arrayForKey(LIBRARY_IDS_KEY).orEmpty().filterIsInstance<String>().filterNot { it == id }
        defaults.setObject(ids, forKey = LIBRARY_IDS_KEY)
        defaults.removeObjectForKey(recordKey(id))
    }

    private fun recordKey(id: String): String = "$RECORD_PREFIX$id"

    private fun StoredLibraryRecord.toStorageMap(): Map<String, Any> = mapOf(
        "categories" to categories.map(LibraryCategory::storageValue),
        "addedAt" to (addedAt ?: 0L),
        "anime" to anime.toAnimeStorageMap(),
    )

    private fun Anime.toAnimeStorageMap(): Map<String, Any> = buildMap {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("episodesLabel", episodesLabel)
        put("status", status)
        nextEpisodeAt?.let { put("nextEpisodeAt", it) }
        posterUrl?.let { put("posterUrl", it) }
        posterFallbackUrl?.let { put("posterFallbackUrl", it) }
        description?.let { put("description", it) }
        put("genres", genres)
        put("alternativeTitles", alternativeTitles)
        put("ratings", ratings.map { mapOf("source" to it.source, "value" to it.value, "votes" to (it.votes ?: 0)) })
        ageRating?.let { put("ageRating", it) }
        viewCount?.let { put("viewCount", it) }
        put("screenshots", screenshots)
        trailer?.let { put("trailer", mapOf("id" to it.id, "site" to it.site, "thumbnailUrl" to (it.thumbnailUrl ?: ""), "sourceUrl" to (it.sourceUrl ?: ""))) }
        sourceMaterial?.let { put("sourceMaterial", it) }
        put("studios", studios)
        put("similarAnime", similarAnime.map { related -> related.toRelatedStorageMap() })
        put("franchiseAnime", franchiseAnime.map { related -> related.toRelatedStorageMap() })
        put("relatedAnime", relatedAnime.map { related -> related.toRelatedStorageMap() })
        releaseDate?.let { put("releaseDate", it) }
    }

    private fun RelatedAnime.toRelatedStorageMap(): Map<String, Any> = buildMap {
        put("id", id)
        put("title", title)
        posterUrl?.let { put("posterUrl", it) }
        posterFallbackUrl?.let { put("posterFallbackUrl", it) }
        type?.let { put("type", it) }
        year?.let { put("year", it) }
        episodeCount?.let { put("episodeCount", it) }
        status?.let { put("status", it) }
    }

    private fun Map<*, *>.toStoredRecord(): StoredLibraryRecord? {
        val anime = readMap(this["anime"])?.toAnime() ?: return null
        val categories = readList(this["categories"])
            .mapNotNull { (it as? String)?.let(LibraryCategory::fromStorageValue) }
            .distinct()
        if (categories.isEmpty()) return null
        return StoredLibraryRecord(anime, categories, (this["addedAt"] as? Number)?.toLong())
    }

    private fun Map<*, *>.toAnime(): Anime? = Anime(
        id = this["id"] as? String ?: return null,
        title = this["title"] as? String ?: return null,
        subtitle = this["subtitle"] as? String ?: "",
        episodesLabel = this["episodesLabel"] as? String ?: "",
        status = this["status"] as? String ?: "",
        nextEpisodeAt = (this["nextEpisodeAt"] as? Number)?.toLong(),
        posterUrl = this["posterUrl"] as? String,
        posterFallbackUrl = this["posterFallbackUrl"] as? String,
        description = this["description"] as? String,
        genres = readList(this["genres"]).filterIsInstance<String>(),
        alternativeTitles = readList(this["alternativeTitles"]).filterIsInstance<String>(),
        ratings = readList(this["ratings"]).mapNotNull { readMap(it)?.toRating() },
        ageRating = this["ageRating"] as? String,
        viewCount = (this["viewCount"] as? Number)?.toLong(),
        screenshots = readList(this["screenshots"]).filterIsInstance<String>(),
        trailer = readMap(this["trailer"])?.toTrailer(),
        sourceMaterial = this["sourceMaterial"] as? String,
        studios = readList(this["studios"]).filterIsInstance<String>(),
        similarAnime = readList(this["similarAnime"]).mapNotNull { readMap(it)?.toRelatedAnime() },
        franchiseAnime = readList(this["franchiseAnime"]).mapNotNull { readMap(it)?.toRelatedAnime() },
        relatedAnime = readList(this["relatedAnime"]).mapNotNull { readMap(it)?.toRelatedAnime() },
        releaseDate = this["releaseDate"] as? String,
    )

    private fun Map<*, *>.toRating(): AnimeRating? = AnimeRating(
        source = this["source"] as? String ?: return null,
        value = (this["value"] as? Number)?.toDouble() ?: return null,
        votes = (this["votes"] as? Number)?.toInt()?.takeIf { it > 0 },
    )

    private fun Map<*, *>.toTrailer(): AnimeTrailer? = AnimeTrailer(
        id = this["id"] as? String ?: return null,
        site = this["site"] as? String ?: return null,
        thumbnailUrl = (this["thumbnailUrl"] as? String).orEmpty().ifBlank { null },
        sourceUrl = (this["sourceUrl"] as? String).orEmpty().ifBlank { null },
    )

    private fun Map<*, *>.toRelatedAnime(): RelatedAnime? = RelatedAnime(
        id = this["id"] as? String ?: return null,
        title = this["title"] as? String ?: return null,
        posterUrl = this["posterUrl"] as? String,
        posterFallbackUrl = this["posterFallbackUrl"] as? String,
        type = this["type"] as? String,
        year = (this["year"] as? Number)?.toInt(),
        episodeCount = (this["episodeCount"] as? Number)?.toInt(),
        status = this["status"] as? String,
    )

    private fun readMap(value: Any?): Map<*, *>? = value as? Map<*, *>

    private fun readList(value: Any?): List<*> = value as? List<*> ?: emptyList<Any?>()

    private fun currentEpochMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private data class StoredLibraryRecord(
        val anime: Anime,
        val categories: List<LibraryCategory>,
        val addedAt: Long?,
    )

    private companion object {
        const val LIBRARY_IDS_KEY = "hibiki.library.ids"
        const val RECORD_PREFIX = "hibiki.library.record."
    }
}
