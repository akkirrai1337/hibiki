package org.akkirrai.beakokit.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.akkirrai.beakokit.model.AnimeTitle

/**
 * The networked half of the AniList metadata provider: one GraphQL client, the store behind it, and
 * the matching flow that turns a source's title into an AniList entry. The rules for *what* that
 * entry then replaces are pure and live in `AniListMetadata.kt`.
 *
 * Mirrors the desktop client's `main/metadata/anilistProvider.ts`.
 */
class AniListMetadataProvider(
    private val client: HttpClient,
    private val store: AniListMetadataStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    // One request at a time, spaced out - a home screen resolving twelve cards at once would
    // otherwise open twelve sockets and collect a 429 for most of them. AniList allows about 90
    // requests a minute per IP; nothing here is latency-critical (a screen paints from the source
    // first and fills metadata in as it arrives), so this stays well under the limit rather than
    // racing to it and living on 429s.
    private val requestLock = Mutex()
    private var lastRequestAtMillis = 0L

    /**
     * The AniList entry for one source title, or null when there is none to be had.
     *
     * Never throws: a caller merges whatever comes back, and null simply means the screen keeps the
     * source's own metadata.
     */
    suspend fun metadataFor(anime: AnimeTitle): ExternalMetadata? {
        val match = store.readMatch(anime.id)
        if (match != null) {
            if (match.anilistId == null) {
                // A remembered failure. Manual "no match" is not a thing, so only the TTL retires
                // it - without this, every visit to a title AniList does not have re-runs the same
                // fruitless search.
                if (nowMillis() - match.matchedAtMillis < TTL_NO_MATCH_MILLIS) return null
            } else {
                val cached = store.readMedia(match.anilistId)
                if (cached != null && nowMillis() - cached.cachedAtMillis < ttlFor(cached.media)) {
                    return cached.media
                }
                val refreshed = fetchMediaById(match.anilistId)
                if (refreshed != null) {
                    store.writeMedia(refreshed, nowMillis())
                    return refreshed
                }
                // Offline, or AniList is down. A stale entry beats an empty screen.
                return cached?.media
            }
        }

        // Two shots at most - a third search costs another request for a title that is very likely
        // simply absent.
        val searchNames = (listOf(anime.englishName, anime.originalName) + anime.synonyms)
            .filterNotNull()
            .filter(String::isNotBlank)
            .distinct()
            .take(2)

        for (name in searchNames) {
            val candidates = searchCandidates(name)
            val best = pickBestMatch(anime, candidates.map(ScoredCandidate::candidate)) ?: continue
            val found = candidates.firstOrNull { it.candidate.anilistId == best.anilistId }?.media ?: continue
            store.writeMedia(found, nowMillis())
            store.writeMatch(
                MetadataMatchRecord(
                    titleId = anime.id,
                    anilistId = best.anilistId,
                    confidencePercent = (best.confidence * 100).toInt(),
                    manual = false,
                    matchedAtMillis = nowMillis(),
                ),
            )
            return found
        }

        store.writeMatch(
            MetadataMatchRecord(
                titleId = anime.id,
                anilistId = null,
                confidencePercent = null,
                manual = false,
                matchedAtMillis = nowMillis(),
            ),
        )
        return null
    }

    /** Whatever is already stored for this title, with no network call at all - what a list screen
     * draws from while the per-title path above fills the gaps. */
    fun cachedMetadataFor(titleId: String): ExternalMetadata? {
        val anilistId = store.readMatch(titleId)?.anilistId ?: return null
        return store.readMedia(anilistId)?.media
    }

    /** Binds a title to an AniList entry by hand, from the details screen. Marked manual, which is
     * what stops the automatic matcher from ever overwriting it again. */
    suspend fun setManualMatch(titleId: String, anilistId: Int): ExternalMetadata? {
        val media = fetchMediaById(anilistId) ?: store.readMedia(anilistId)?.media
        if (media != null) store.writeMedia(media, nowMillis())
        store.writeMatch(
            MetadataMatchRecord(
                titleId = titleId,
                anilistId = anilistId,
                confidencePercent = null,
                manual = true,
                matchedAtMillis = nowMillis(),
            ),
        )
        return media
    }

    /** Drops a title's binding entirely, so the next lookup matches it again from scratch. */
    fun clearMatch(titleId: String) = store.clearMatch(titleId)

    /** Candidates for the details screen's manual picker, in AniList's own relevance order. */
    suspend fun search(query: String): List<ExternalMetadata> =
        searchCandidates(query).map(ScoredCandidate::media)

    private fun ttlFor(media: ExternalMetadata): Long =
        if (media.status == "released") TTL_SETTLED_MILLIS else TTL_AIRING_MILLIS

    private suspend fun fetchMediaById(anilistId: Int): ExternalMetadata? {
        val data = graphql(
            query = "query (\$id: Int) { Media(id: \$id, type: ANIME) { $MEDIA_FIELDS } }",
            variables = buildJsonObject { put("id", anilistId) },
        ) ?: return null
        return data.media?.toExternalMetadata()
    }

    private suspend fun searchCandidates(name: String): List<ScoredCandidate> {
        val data = graphql(
            query = "query (\$search: String) { Page(perPage: 10) { media(search: \$search, type: ANIME) { $MEDIA_FIELDS } } }",
            variables = buildJsonObject { put("search", name) },
        ) ?: return emptyList()
        return data.page?.media.orEmpty().map { raw ->
            val media = raw.toExternalMetadata()
            ScoredCandidate(
                candidate = MatchCandidate(
                    anilistId = media.anilistId,
                    names = listOfNotNull(raw.title?.romaji, raw.title?.english, raw.title?.native) + raw.synonyms.orEmpty(),
                    year = media.year,
                    type = media.type,
                ),
                media = media,
            )
        }
    }

    private suspend fun graphql(query: String, variables: JsonObject): AniListData? = requestLock.withLock {
        repeat(MAX_ATTEMPTS) {
            val wait = lastRequestAtMillis + MIN_REQUEST_INTERVAL_MILLIS - nowMillis()
            if (wait > 0) delay(wait)
            lastRequestAtMillis = nowMillis()
            val response = runCatching {
                client.post(ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    setBody(
                        json.encodeToString(
                            JsonObject.serializer(),
                            buildJsonObject {
                                put("query", query)
                                put("variables", variables)
                            },
                        ),
                    )
                }
            }.getOrNull() ?: return null
            if (response.status.value == 429) {
                // AniList states how long to wait; its own header is far more accurate than any
                // backoff guessed here, and ignoring it just earns another 429. The shared client's
                // retry plugin does not cover this call - GraphQL is a POST, which it treats as
                // unsafe to retry.
                val retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                delay(retryAfterSeconds?.times(1_000) ?: DEFAULT_RATE_LIMIT_DELAY_MILLIS)
                return@repeat
            }
            if (response.status.value !in 200..299) return null
            val body = runCatching {
                json.decodeFromString(AniListResponse.serializer(), response.bodyAsText())
            }.getOrNull() ?: return null
            return body.data
        }
        return null
    }

    private data class ScoredCandidate(val candidate: MatchCandidate, val media: ExternalMetadata)

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
        const val MIN_REQUEST_INTERVAL_MILLIS = 700L
        const val DEFAULT_RATE_LIMIT_DELAY_MILLIS = 60_000L
        const val MAX_ATTEMPTS = 3

        // A finished show's metadata is effectively frozen; an airing one moves every week and
        // carries the next-episode countdown the details screen prints.
        const val TTL_SETTLED_MILLIS = 14L * 24 * 60 * 60 * 1_000
        const val TTL_AIRING_MILLIS = 12L * 60 * 60 * 1_000
        // Short enough that an entry AniList adds later is picked up within a week.
        const val TTL_NO_MATCH_MILLIS = 7L * 24 * 60 * 60 * 1_000

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        const val MEDIA_FIELDS = """
            id
            idMal
            title { romaji english native }
            synonyms
            description
            coverImage { extraLarge large }
            bannerImage
            genres
            seasonYear
            startDate { year }
            format
            status
            episodes
            averageScore
            isAdult
            studios(isMain: true) { nodes { name } }
            nextAiringEpisode { airingAt }
        """
    }
}

@Serializable
private data class AniListResponse(val data: AniListData? = null)

@Serializable
private data class AniListData(
    @kotlinx.serialization.SerialName("Media") val media: RawMedia? = null,
    @kotlinx.serialization.SerialName("Page") val page: RawPage? = null,
)

@Serializable
private data class RawPage(val media: List<RawMedia>? = null)

@Serializable
private data class RawMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: RawTitle? = null,
    val synonyms: List<String>? = null,
    val description: String? = null,
    val coverImage: RawCoverImage? = null,
    val bannerImage: String? = null,
    val genres: List<String>? = null,
    val seasonYear: Int? = null,
    val startDate: RawDate? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val isAdult: Boolean? = null,
    val studios: RawStudios? = null,
    val nextAiringEpisode: RawAiringEpisode? = null,
)

@Serializable
private data class RawTitle(val romaji: String? = null, val english: String? = null, val native: String? = null)

@Serializable
private data class RawCoverImage(val extraLarge: String? = null, val large: String? = null)

@Serializable
private data class RawDate(val year: Int? = null)

@Serializable
private data class RawStudios(val nodes: List<RawStudio>? = null)

@Serializable
private data class RawStudio(val name: String? = null)

@Serializable
private data class RawAiringEpisode(val airingAt: Long? = null)

private fun RawMedia.toExternalMetadata() = ExternalMetadata(
    anilistId = id,
    malId = idMal,
    romajiName = title?.romaji,
    englishName = title?.english,
    nativeName = title?.native,
    synonyms = synonyms.orEmpty(),
    description = sanitizeAniListDescription(description),
    posterUrl = coverImage?.extraLarge ?: coverImage?.large,
    bannerUrl = bannerImage,
    studios = studios?.nodes.orEmpty().mapNotNull(RawStudio::name),
    genres = genres.orEmpty(),
    year = seasonYear ?: startDate?.year,
    type = mapAniListFormat(format),
    status = mapAniListStatus(status),
    episodeCount = episodes,
    averageScore = averageScore,
    // AniList reports airing times in epoch seconds, the same unit AnimeTitle.nextEpisodeAt uses.
    nextEpisodeAt = nextAiringEpisode?.airingAt,
    isAdult = isAdult == true,
)
