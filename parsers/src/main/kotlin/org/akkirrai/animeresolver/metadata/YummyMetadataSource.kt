package org.akkirrai.animeresolver.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.animeresolver.core.MetadataSource
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeTrailerTitle
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.RelatedAnimeTitle
import org.akkirrai.animeresolver.model.SearchFilterOption
import org.akkirrai.animeresolver.model.TitleRating
import org.akkirrai.animeresolver.network.bodyOrThrow

class YummyMetadataSource(
    private val client: HttpClient,
    private val applicationToken: String? = null,
    private val baseUrl: String = "https://api.yani.tv",
    private val searchLimit: Int = 20,
    private val debugLogger: ((String) -> Unit)? = null,
    private val languageProvider: () -> String = { "ru" },
) : MetadataSource {
    override val name: String = "YummyAnime"

    private val filterCatalogMutex = Mutex()
    private var cachedFilterCatalog: AnimeSearchFilterCatalog? = null

    suspend fun getCatalog(
        limit: Int = searchLimit,
        offset: Int = 0,
        sort: String = "top",
        type: String? = null,
    ): List<AnimeTitle> {
        println("[YummyMetadataSource] getCatalog(limit=$limit, offset=$offset, sort=$sort, type=$type)")
        val language = requestLanguage()
        val response = client.get("$baseUrl/anime") {
            addHeaders(language)
            parameter("limit", limit)
            parameter("offset", offset)
            parameter("sort", sort)
            type?.takeIf(String::isNotBlank)?.let { parameter("types", it) }
        }
        return response.bodyOrThrow<YummyEnvelope<List<YummyAnimePayload>>>(name)
            .response
            .map { payload -> payload.toAnimeTitle(language) }
    }

    override suspend fun search(query: String): List<AnimeTitle> {
        return search(query = query, limit = searchLimit, offset = 0)
    }

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        println(
            "[YummyMetadataSource] search(request=" +
                "query=${request.query}, limit=${request.limit}, offset=${request.offset}, " +
                "sort=${request.sort}, types=${request.typeAliases}, statuses=${request.statusAliases}, " +
                "genres=${request.includedGenreAliases}, excludedGenres=${request.excludedGenreAliases}, " +
                "yearFrom=${request.yearFrom}, yearTo=${request.yearTo})"
        )
        val language = requestLanguage()
        val response = client.get("$baseUrl/anime") {
            addHeaders(language)
            request.query.trim().takeIf(String::isNotBlank)?.let { parameter("q", it) }
            parameter("limit", request.limit)
            parameter("offset", request.offset)
            request.sort.toYummySortParam(request.query)?.let { parameter("sort", it) }
            request.typeAliases.normalizedCsvOrNull()?.let { parameter("types", it) }
            request.statusAliases.normalizedCsvOrNull()?.let { parameter("statuses", it) }
            request.includedGenreAliases.normalizedCsvOrNull()?.let { parameter("genres", it) }
            request.excludedGenreAliases.normalizedCsvOrNull()?.let { parameter("genres_exclude", it) }
            request.yearFrom?.let { parameter("year_from", it) }
            request.yearTo?.let { parameter("year_to", it) }
        }
        return response.bodyOrThrow<YummyEnvelope<List<YummyAnimePayload>>>(name)
            .response
            .map { payload -> payload.toAnimeTitle(language) }
    }

    suspend fun search(
        query: String,
        limit: Int = searchLimit,
        offset: Int = 0,
    ): List<AnimeTitle> {
        return search(
            AnimeSearchRequest(
                query = query,
                limit = limit,
                offset = offset,
            )
        )
    }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog {
        cachedFilterCatalog?.let { return it }
        return filterCatalogMutex.withLock {
            cachedFilterCatalog?.let { return@withLock it }
            val catalog = runCatching { loadFilterCatalogFromSwagger() }
                .getOrElse { error ->
                    debugLogger?.invoke("Yummy swagger filter catalog fallback: ${error.message}")
                    fallbackFilterCatalog()
                }
            cachedFilterCatalog = catalog
            catalog
        }
    }

    override suspend fun getById(id: String): AnimeTitle {
        println("[YummyMetadataSource] getById(id=$id)")
        val language = requestLanguage()
        val response = client.get("$baseUrl/anime/$id") {
            addHeaders(language)
        }
        val title = response.bodyOrThrow<YummyEnvelope<YummyAnimePayload>>(name)
            .response
            .toAnimeTitle(language)
        val trailer = runCatching { getTrailers(id).firstOrNull() }
            .onFailure { error ->
                debugLogger?.invoke("Yummy trailer request failed for anime $id: ${error.message}")
            }
            .getOrNull()
        return title.copy(trailer = trailer)
    }

    suspend fun getTrailers(id: String): List<AnimeTrailerTitle> {
        val language = requestLanguage()
        val response = client.get("$baseUrl/anime/$id/trailers") {
            addHeaders(language)
        }
        return response.bodyOrThrow<YummyEnvelope<List<YummyTrailerPayload>>>(name)
            .response
            .mapNotNull(YummyTrailerPayload::toAnimeTrailerTitle)
    }

    private suspend fun loadFilterCatalogFromSwagger(): AnimeSearchFilterCatalog {
        val root = Json.parseToJsonElement(
            client.get("$baseUrl/swagger.json").bodyAsText()
        ).jsonObject

        val genreAliases = root.enumValues(
            "components",
            "schemas",
            "GetAnimeGenresIdResponse",
            "properties",
            "response",
            "properties",
            "alias",
            "enum",
        )
        val statusAliases = root.enumValues(
            "components",
            "schemas",
            "GetAnimeCatalogResponse",
            "properties",
            "response",
            "properties",
            "data",
            "items",
            "properties",
            "anime_status",
            "properties",
            "alias",
            "enum",
        )
        val sortAliases = root.pathParameterEnumValues("/anime", "sort")

        return AnimeSearchFilterCatalog(
            sortOptions = (listOf("relevance") + sortAliases.ifEmpty { fallbackSortAliases })
                .distinct()
                .map(::aliasOption),
            typeOptions = fallbackTypeAliases.map(::aliasOption),
            statusOptions = statusAliases.ifEmpty { fallbackStatusAliases }.map(::aliasOption),
            genreOptions = genreAliases.ifEmpty { fallbackGenreAliases }.map(::aliasOption),
        )
    }

    private fun fallbackFilterCatalog(): AnimeSearchFilterCatalog {
        return AnimeSearchFilterCatalog(
            sortOptions = (listOf("relevance") + fallbackSortAliases).distinct().map(::aliasOption),
            typeOptions = fallbackTypeAliases.map(::aliasOption),
            statusOptions = fallbackStatusAliases.map(::aliasOption),
            genreOptions = fallbackGenreAliases.map(::aliasOption),
        )
    }

    private fun aliasOption(alias: String): SearchFilterOption {
        return SearchFilterOption(id = alias, title = alias)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addHeaders(language: String) {
        header("Lang", language)
        debugLogger?.invoke(
            "Yummy request: lang=$language, xApplicationAttached=${!applicationToken.isNullOrBlank()}, url=${url.buildString()}"
        )
        applicationToken?.takeIf(String::isNotBlank)?.let {
            header("X-Application", it)
        }
    }

    private fun requestLanguage(): String {
        return when (languageProvider().trim().lowercase()) {
            "en", "eng", "english" -> "en"
            else -> "ru"
        }
    }

    private fun List<String>.normalizedCsvOrNull(): String? {
        return map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(",")
    }

    private fun JsonObject.enumValues(vararg path: String): List<String> {
        val target = path.fold(this as JsonElement?) { current, key ->
            (current as? JsonObject)?.get(key)
        } ?: return emptyList()
        return (target as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter(String::isNotBlank)
            .orEmpty()
    }

    private fun JsonObject.pathParameterEnumValues(pathKey: String, parameterName: String): List<String> {
        val parameters = get("paths")
            ?.jsonObject
            ?.get(pathKey)
            ?.jsonObject
            ?.get("get")
            ?.jsonObject
            ?.get("parameters")
            ?.jsonArray
            ?: return emptyList()
        val parameter = parameters.firstOrNull { element ->
            element.jsonObject["name"]?.jsonPrimitive?.content == parameterName
        } ?: return emptyList()
        return parameter.jsonObject["schema"]
            ?.jsonObject
            ?.get("enum")
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter(String::isNotBlank)
            .orEmpty()
    }

    private fun AnimeSearchSort.toYummySortParam(query: String): String? {
        return when (this) {
            AnimeSearchSort.RELEVANCE -> if (query.isBlank()) "top" else null
            AnimeSearchSort.RATING -> "top"
            AnimeSearchSort.TITLE -> "title"
            AnimeSearchSort.YEAR -> "year"
            AnimeSearchSort.VOTES -> "votes"
            AnimeSearchSort.VIEWS -> "views"
            AnimeSearchSort.COMMENTS -> "comments"
        }
    }

    private companion object {
        val fallbackSortAliases = listOf("top", "title", "year", "votes", "views", "comments")
        val fallbackTypeAliases = listOf("tv", "movie", "short_movie", "ova", "special", "short_serial", "ona")
        val fallbackStatusAliases = listOf("released", "ongoing", "announcement")
        val fallbackGenreAliases = listOf(
            "bisenen",
            "dzesej",
            "maho-sedze",
            "sedze",
            "sedze-aj",
            "senen",
            "senen-aj",
            "sejnen",
            "etti",
            "vestern",
            "detektiv",
            "drama",
            "komediya",
            "parodiya",
            "prestupnyj-mir",
            "vori",
            "mafiya-yakudza",
            "ohotniki-za-golovami",
            "piraty",
            "terroristy",
            "ubijcy",
            "meha",
            "androidy",
            "pilotiruemye-roboty",
            "silovye-kostyumy",
            "ii",
            "transformery",
            "mistika",
            "priklyucheniya",
            "romantika",
            "lyubovnyj-treugol-nik",
            "triller",
            "ugasy",
            "fantastika",
            "inoplanetyane",
            "kiborgi",
            "kosmicheskie-priklyucheniya",
            "puteshestviya-vo-vremeni",
            "fentezi",
            "al-ternativnaya-real-nost",
            "angely",
            "bogi",
            "vampiry",
            "ved-my",
            "demony",
            "drakony",
            "zombi",
            "magiya",
            "prizraki",
            "rysalki",
            "sovremennoe-fentezi",
            "sukkuby",
            "temnoe-fentezi",
            "temnye-el-fy",
            "fei",
            "celyj-fentezi-mir",
            "el-fy",
            "virtual-naya-real-nost",
            "parallel-nyj-mir",
            "ekshen",
            "boevye-iskusstva",
            "nindzya",
            "perestrelki",
            "proksi-boi",
            "samurai",
            "srazheniya-na-mechah",
            "supersposobnosti",
            "al-ternativnaya-istoriya",
            "antivojna",
            "antiutopiya",
            "vojna",
            "voennaya-tematika",
            "garem",
            "iskusstvo",
            "muzyka",
            "istoricheskij",
            "kiberpank",
            "kulinariya",
            "lolikon",
            "nelinejnyj-syuzhet",
            "povsednevnost",
            "politika",
            "policejskie",
            "postapokaliptika",
            "rossiya-v-anime",
            "sport",
            "basketbol",
            "stimpank",
            "tajnyj-zagovor",
            "shkola",
            "garem-dlya-devochek",
            "lyudi-zveri",
            "psihologiya",
            "manga",
            "erotica",
            "ne-yaponskoe",
            "trap",
            "sverh-estestvennoe",
            "igry",
            "isekai",
            "chinese3d",
            "motorcycles",
            "badguys",
            "bezumie",
        )
    }
}

@Serializable
private data class YummyEnvelope<T>(
    val response: T,
)

@Serializable
private data class YummyAnimePayload(
    @SerialName("anime_id") val animeId: Long,
    val title: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_orig") val titleOrig: String? = null,
    @SerialName("title_original") val titleOriginal: String? = null,
    @SerialName("title_jp") val titleJp: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    val synonyms: List<String> = emptyList(),
    @SerialName("other_titles") val otherTitles: List<String> = emptyList(),
    @SerialName("alternative_titles") val alternativeTitles: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val year: Int? = null,
    val season: Int? = null,
    val episodes: JsonElement? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
    val status: String? = null,
    @SerialName("anime_status") val animeStatus: YummyStatus? = null,
    val description: String? = null,
    val original: String? = null,
    val type: YummyMetadataType? = null,
    val image: YummyMetadataImage? = null,
    val poster: YummyMetadataImage? = null,
    val rating: YummyRating? = null,
    @SerialName("min_age") val minAge: YummyMinAge? = null,
    val views: Long? = null,
    @SerialName("random_screenshots") val randomScreenshots: List<YummyScreenshot> = emptyList(),
    @SerialName("viewing_order") val viewingOrder: List<YummyViewingOrderEntry> = emptyList(),
    val genres: List<YummyCatalogValue> = emptyList(),
    val studios: List<YummyCatalogValue> = emptyList(),
) {
    fun toAnimeTitle(language: String): AnimeTitle {
        val localizedTitle = title.normalize()
        val explicitEnglishName = titleEn.normalize()
            ?: titleEnglish.normalize()
        val englishName = explicitEnglishName
            ?: localizedTitle?.takeIf { language == "en" && !it.hasCyrillic() }
        val russianName = localizedTitle?.takeIf { language != "en" || it.hasCyrillic() }
        val japaneseName = titleJp.normalize()
            ?: titleJapanese.normalize()
        val originalName = titleOrig.normalize()
            ?: titleOriginal.normalize()
            ?: japaneseName
            ?: englishName
            ?: russianName
            ?: animeId.toString()
        val synonyms = buildList {
            addAll(synonyms.mapNotNull(String::normalize))
            addAll(otherTitles.mapNotNull(String::normalize))
            addAll(alternativeTitles.mapNotNull(String::normalize))
            addAll(aliases.mapNotNull(String::normalize))
        }.distinct()

        return AnimeTitle(
            id = animeId.toString(),
            russianName = russianName,
            englishName = englishName,
            originalName = originalName,
            japaneseName = japaneseName,
            synonyms = synonyms,
            year = year,
            type = type?.alias.normalize(),
            episodeCount = episodes.extractEpisodeCount(
                preferTotal = listOf(animeStatus?.alias, animeStatus?.title, status)
                    .any { it.isReleasedAnimeStatus() },
            ) ?: episodesCount,
            posterUrl = poster?.bestUrl() ?: image?.bestUrl(),
            status = animeStatus?.title.normalize()
                ?: animeStatus?.alias.normalize()
                ?: status.normalize(),
            description = description.normalize(),
            nextEpisodeAt = episodes.extractNextDate(),
            genres = genres.mapNotNull { it.title.normalize() }.distinct(),
            ratings = rating?.toModel().orEmpty(),
            ageRating = minAge?.title.normalize() ?: minAge?.titleLong.normalize(),
            viewCount = views,
            screenshots = randomScreenshots.mapNotNull { it.sizes?.bestUrl() }.distinct(),
            sourceMaterial = original.normalize(),
            studios = studios.mapNotNull { it.title.normalize() }.distinct(),
            franchiseAnime = viewingOrder.mapNotNull(YummyViewingOrderEntry::toRelatedAnimeTitle),
            relatedAnime = viewingOrder.mapNotNull(YummyViewingOrderEntry::toRelatedAnimeTitle),
            season = season,
        )
    }
}

@Serializable
private data class YummyViewingOrderEntry(
    @SerialName("anime_id") val animeId: Long? = null,
    val title: String? = null,
    val poster: YummyMetadataImage? = null,
    @SerialName("anime_status") val animeStatus: YummyStatus? = null,
    val type: YummyMetadataType? = null,
    val year: Int? = null,
) {
    fun toRelatedAnimeTitle(): RelatedAnimeTitle? {
        val id = animeId?.toString() ?: return null
        val normalizedTitle = title.normalize() ?: return null
        return RelatedAnimeTitle(
            id = id,
            title = normalizedTitle,
            posterUrl = poster?.bestUrl(),
            type = type?.alias,
            year = year,
        )
    }
}

@Serializable
private data class YummyTrailerPayload(
    @SerialName("trailer_id") val trailerId: Long,
    @SerialName("anime_id") val animeId: Long,
    @SerialName("iframe_url") val iframeUrl: String,
    val dubbing: String? = null,
    val player: String? = null,
    val number: String? = null,
) {
    fun toAnimeTrailerTitle(): AnimeTrailerTitle? {
        val sourceUrl = iframeUrl.normalizeUrl() ?: return null
        val youtubeId = sourceUrl.extractYouTubeVideoId()
        val site = if (youtubeId != null) {
            "youtube"
        } else {
            player.normalize()?.lowercase() ?: "yummy"
        }
        return AnimeTrailerTitle(
            id = youtubeId ?: trailerId.toString(),
            site = site,
            thumbnailUrl = youtubeId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" },
            sourceUrl = sourceUrl,
        )
    }
}

@Serializable
private data class YummyStatus(
    val title: String? = null,
    val alias: String? = null,
)

@Serializable
private data class YummyCatalogValue(
    val title: String? = null,
)

@Serializable
private data class YummyRating(
    val average: Double? = null,
    val counters: Int? = null,
    @SerialName("kp_rating") val kpRating: Double? = null,
    @SerialName("shikimori_rating") val shikimoriRating: Double? = null,
    @SerialName("anidub_rating") val anidubRating: Double? = null,
    @SerialName("myanimelist_rating") val myAnimeListRating: Double? = null,
    @SerialName("worldart_rating") val worldArtRating: Double? = null,
) {
    fun toModel(): List<TitleRating> = buildList {
        average?.takeIf { it > 0.0 }?.let { add(TitleRating(source = "Yummy", value = it, votes = counters)) }
        myAnimeListRating?.takeIf { it > 0.0 }?.let { add(TitleRating(source = "MAL", value = it)) }
        shikimoriRating?.takeIf { it > 0.0 }?.let { add(TitleRating(source = "Shiki", value = it)) }
        kpRating?.takeIf { it > 0.0 }?.let { add(TitleRating(source = "KP", value = it)) }
        worldArtRating?.takeIf { it > 0.0 }?.let { add(TitleRating(source = "WA", value = it)) }
        anidubRating?.takeIf { it > 0.0 }?.let { add(TitleRating(source = "AniDub", value = it)) }
    }
}

@Serializable
private data class YummyMinAge(
    val title: String? = null,
    @SerialName("title_long") val titleLong: String? = null,
)

@Serializable
private data class YummyScreenshot(
    val sizes: YummyScreenshotSizes? = null,
)

@Serializable
private data class YummyScreenshotSizes(
    val small: String? = null,
    val full: String? = null,
) {
    fun bestUrl(): String? = full.normalizeUrl() ?: small.normalizeUrl()
}

@Serializable
private data class YummyMetadataType(
    val alias: String? = null,
)

@Serializable
private data class YummyMetadataImage(
    val fullsize: String? = null,
    val big: String? = null,
    val medium: String? = null,
    val small: String? = null,
    val huge: String? = null,
    val mega: String? = null,
    val original: String? = null,
    val preview: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
) {
    fun bestUrl(): String? {
        return listOf(fullsize, mega, huge, big, medium, small, original, preview, thumbnail, url)
            .mapNotNull { value: String? -> value.normalizeUrl() }
            .firstOrNull()
    }
}

private fun String?.normalize(): String? =
    this?.trim()?.takeIf(String::isNotBlank)

private fun String.hasCyrillic(): Boolean = any { char ->
    char in '\u0400'..'\u04FF'
}

private fun String?.normalizeUrl(): String? {
    val normalized = this.normalize() ?: return null
    return if (normalized.startsWith("//")) {
        "https:$normalized"
    } else {
        normalized
    }
}

private fun String.extractYouTubeVideoId(): String? {
    val patterns = listOf(
        Regex("(?:youtube\\.com/)?embed/([A-Za-z0-9_-]{6,})", RegexOption.IGNORE_CASE),
        Regex("[?&]v=([A-Za-z0-9_-]{6,})", RegexOption.IGNORE_CASE),
        Regex("youtu\\.be/([A-Za-z0-9_-]{6,})", RegexOption.IGNORE_CASE),
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(this)?.groupValues?.getOrNull(1)
    }
}

private fun JsonElement?.extractEpisodeCount(preferTotal: Boolean = false): Int? {
    return when (this) {
        is JsonPrimitive -> content.toIntOrNull()
        is JsonObject -> (if (preferTotal) listOf("count", "aired") else listOf("aired", "count"))
            .firstNotNullOfOrNull { key ->
                get(key)?.jsonPrimitive?.content?.toIntOrNull()
            }
        else -> null
    }
}

private fun String?.isReleasedAnimeStatus(): Boolean {
    return when (this?.trim()?.lowercase()) {
        "released", "completed", "вышел", "завершён", "завершен", "вийшов" -> true
        else -> false
    }
}

private fun JsonElement?.extractNextDate(): Long? {
    return when (this) {
        is JsonObject -> get("next_date")
            ?.jsonPrimitive
            ?.content
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        else -> null
    }
}
