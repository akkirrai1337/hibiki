package org.akkirrai.beakokit.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The networked half of each metadata provider: requests only. Response mappings are the pure
 * `*Mapping.kt` files beside this one, and matching and caching belong to
 * [ExternalMetadataService].
 *
 * Every client answers null for every failure, and draws one distinction that matters: an empty
 * list means the provider genuinely does not carry the title, while null means the request itself
 * failed. The service has to tell them apart before it records a "no match" that would outlive the
 * outage that caused it.
 */
internal val metadataJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private suspend inline fun <reified T> HttpResponse.decode(): T? =
    runCatching { metadataJson.decodeFromString<T>(bodyAsText()) }.getOrNull()

/**
 * AniList's GraphQL endpoint.
 *
 * Note for anyone wondering why a screen is describing itself from MAL or Kitsu: AniList disabled
 * this API outright while this was written ("temporarily disabled due to severe stability issues",
 * HTTP 403 on every query), which is exactly the case the service's provider fallback exists for.
 */
class AniListClient(private val client: HttpClient) {
    // AniList documents 90 requests a minute per IP, but has been running degraded at 30 (its own
    // X-RateLimit-Limit header says so), and overshooting costs a minute's stand-down. Paced for 30;
    // batching below is what keeps that affordable.
    private val queue = MetadataRequestQueue(minIntervalMillis = 2_100, burst = 2)

    fun estimatedWaitMillis(): Long = queue.estimatedWaitMillis()

    /**
     * Name searches, many to a request as GraphQL aliases (`a0: Page { media(search: $q0) }`, ...).
     * AniList caps a query's complexity at 500, and each aliased page of ten costs about 28 - 12
     * keeps well clear of it.
     */
    private val searches = BatchLanes<String, List<ScoredEntry>>(MAX_SEARCH_ALIASES) execute@{ takeKeys ->
        var names = emptyList<String>()
        val pages = postGraphql(
            body = {
                names = takeKeys()
                val variables = names.indices.joinToString { "\$q$it: String" }
                val selections = names.indices.joinToString(" ") { i ->
                    "a$i: Page(perPage: 10) { media(search: \$q$i, type: ANIME) { $ANILIST_MEDIA_FIELDS } }"
                }
                "query ($variables) { $selections }" to buildJsonObject {
                    names.forEachIndexed { i, name -> put("q$i", name) }
                }
            },
            parse = { it.decode<AniListAliasedResponse>()?.data },
        ) ?: return@execute null
        // A name whose alias is missing from an otherwise good answer gets no entry, and so reads as a
        // failed request - never as an empty result, which would be recorded as "AniList lacks this".
        names.withIndex().mapNotNull { (i, name) ->
            pages["a$i"]?.let { page -> name to page.media.orEmpty().map { ScoredEntry(it.toMatchCandidate(), it.toExternalMetadata()) } }
        }.toMap()
    }

    private val byId = BatchLanes<Int, ExternalMetadata>(MAX_IDS_PER_REQUEST) execute@{ takeKeys ->
        val page = postGraphql(
            body = { BY_ID_QUERY to buildJsonObject { put("ids", JsonArray(takeKeys().map(::JsonPrimitive))) } },
            parse = { it.decode<AniListResponse>()?.data?.page },
        ) ?: return@execute null
        page.media.orEmpty().associate { it.id to it.toExternalMetadata() }
    }

    private val byMalId = BatchLanes<Int, ExternalMetadata>(MAX_IDS_PER_REQUEST) execute@{ takeKeys ->
        val page = postGraphql(
            body = { BY_MAL_ID_QUERY to buildJsonObject { put("ids", JsonArray(takeKeys().map(::JsonPrimitive))) } },
            parse = { it.decode<AniListResponse>()?.data?.page },
        ) ?: return@execute null
        page.media.orEmpty()
            .mapNotNull { media -> media.idMal?.let { it to media.toExternalMetadata() } }
            .distinctBy { it.first }
            .toMap()
    }

    /** [body] is built only once the queue admits the request - which is when a batch takes its keys. */
    private suspend fun <T> postGraphql(body: () -> Pair<String, JsonObject>, parse: suspend (HttpResponse) -> T?): T? = queue.run(
        request = {
            val (query, variables) = body()
            client.post(ENDPOINT) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(
                    metadataJson.encodeToString(
                        JsonObject.serializer(),
                        buildJsonObject {
                            put("query", query)
                            put("variables", variables)
                        },
                    ),
                )
            }
        },
        parse = parse,
    )

    private suspend fun graphql(query: String, variables: JsonObject): AniListData? =
        postGraphql(body = { query to variables }, parse = { it.decode<AniListResponse>()?.data })

    suspend fun fetchById(anilistId: Int): ExternalMetadata? = (byId.load(anilistId) as? BatchOutcome.Done)?.value

    /** Looks a title up by its MAL id - what lets a match established elsewhere be reused here
     * without searching by name again. */
    suspend fun fetchByMalId(malId: Int): ExternalMetadata? = (byMalId.load(malId) as? BatchOutcome.Done)?.value

    /**
     * A page of AniList's catalog. Its "trending" is genuinely trending - what people are watching
     * and talking about this week - rather than a lifetime popularity ranking, which is the one
     * thing it does better than Kitsu for a catalog.
     */
    suspend fun browse(request: ExternalCatalogRequest): List<ExternalMetadata>? {
        val (nowSeason, nowYear) = seasonNow()
        val seasonal = request.mode == ExternalCatalogRequest.Mode.SEASON
        val sort = when {
            !request.query.isNullOrBlank() -> "SEARCH_MATCH"
            request.mode == ExternalCatalogRequest.Mode.POPULAR -> "POPULARITY_DESC"
            else -> "TRENDING_DESC"
        }
        val data = graphql(
            """query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}season: MediaSeason, ${'$'}seasonYear: Int, ${'$'}genreIn: [String], ${'$'}genreNotIn: [String], ${'$'}formatIn: [MediaFormat], ${'$'}formatNotIn: [MediaFormat], ${'$'}statusIn: [MediaStatus], ${'$'}statusNotIn: [MediaStatus], ${'$'}startAfter: FuzzyDateInt, ${'$'}startBefore: FuzzyDateInt, ${'$'}search: String) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, sort: $sort, season: ${'$'}season, seasonYear: ${'$'}seasonYear, genre_in: ${'$'}genreIn, genre_not_in: ${'$'}genreNotIn, format_in: ${'$'}formatIn, format_not_in: ${'$'}formatNotIn, status_in: ${'$'}statusIn, status_not_in: ${'$'}statusNotIn, startDate_greater: ${'$'}startAfter, startDate_lesser: ${'$'}startBefore, search: ${'$'}search, isAdult: false) { $ANILIST_MEDIA_FIELDS }
              }
            }""",
            buildJsonObject {
                // AniList pages by number, not by offset, so a caller's offset has to divide evenly
                // by the page size - which it does, since a catalog screen asks for whole pages.
                put("page", request.offset / request.limit + 1)
                put("perPage", request.limit)
                if (seasonal) {
                    put("season", (request.season ?: nowSeason).name)
                    put("seasonYear", request.seasonYear ?: nowYear)
                }
                // Each of these is sent only when set, never as null: AniList answers a null
                // `*_not_in` with a 500 and an all-null set with "Illegal operator and value
                // combination", either of which would take down the unfiltered catalog too.
                if (request.genres.isNotEmpty()) put("genreIn", JsonArray(request.genres.map(::JsonPrimitive)))
                if (request.excludedGenres.isNotEmpty()) put("genreNotIn", JsonArray(request.excludedGenres.map(::JsonPrimitive)))
                putEnumList("formatIn", request.types.flatMap(::aniListFormatsForType))
                putEnumList("formatNotIn", request.excludedTypes.flatMap(::aniListFormatsForType))
                putEnumList("statusIn", request.statuses.mapNotNull(::aniListStatusFor))
                putEnumList("statusNotIn", request.excludedStatuses.mapNotNull(::aniListStatusFor))
                // Start dates are YYYYMMDD integers, and one known only to the year is stored with a
                // zero month and day - so the bounds sit just outside the range, not on its first day.
                request.yearFrom?.let { put("startAfter", (it - 1) * 10_000 + 9_999) }
                request.yearTo?.let { put("startBefore", (it + 1) * 10_000) }
                request.query?.trim()?.takeIf { it.isNotEmpty() }?.let { put("search", it) }
            },
        ) ?: return null
        return data.page?.media.orEmpty().map { it.toExternalMetadata() }
    }

    suspend fun search(name: String): List<ScoredEntry>? = when (val outcome = searches.load(name)) {
        BatchOutcome.Failed -> null
        is BatchOutcome.Done -> outcome.value
    }

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
        const val MAX_SEARCH_ALIASES = 12
        // AniList's page size cap.
        const val MAX_IDS_PER_REQUEST = 50
        val BY_ID_QUERY = "query (\$ids: [Int]) { Page(perPage: 50) { media(id_in: \$ids, type: ANIME) { $ANILIST_MEDIA_FIELDS } } }"
        val BY_MAL_ID_QUERY = "query (\$ids: [Int]) { Page(perPage: 50) { media(idMal_in: \$ids, type: ANIME) { $ANILIST_MEDIA_FIELDS } } }"
    }
}

/** A response of aliased pages - `{ "data": { "a0": { "media": [...] }, "a1": ... } }`. */
@Serializable
internal data class AniListAliasedResponse(val data: Map<String, AniListPage?>? = null)

/** Jikan, MAL's unofficial read-only API. No key and no account, which is why it is here: the
 * official MAL API needs a registered client id even to read. */
class MalClient(private val client: HttpClient, clientId: String? = null) {
    // Jikan publishes two limits, 3 requests a second and 60 a minute; the minute one binds.
    // The per-second limit is the burst.
    private val queue = MetadataRequestQueue(minIntervalMillis = 1_100, burst = 3)
    private val byId = RequestCoalescer<Int, ExternalMetadata?>()
    private val searches = RequestCoalescer<String, List<ScoredEntry>?>()

    /** MAL's own API, when the app was built with a client id. Jikan then only covers for it. */
    private val official = clientId?.takeIf(String::isNotBlank)?.let { MalOfficialClient(client, it) }

    fun estimatedWaitMillis(): Long {
        val officialWait = official?.estimatedWaitMillis() ?: return queue.estimatedWaitMillis()
        // Stood down: lookups fall through to Jikan, so that is the wait that matters.
        return if (officialWait == Long.MAX_VALUE) queue.estimatedWaitMillis() else officialWait
    }

    private suspend inline fun <reified T> get(path: String): T? = queue.run(
        request = { client.get(BASE_URL + path) { header(HttpHeaders.Accept, "application/json") } },
        parse = { it.decode<T>() },
    )

    // A null from the official API is a failed request (or, for an id, a missing entry) - either way
    // Jikan gets its chance. An empty search result is an answer, and is not asked again.
    suspend fun fetchById(malId: Int): ExternalMetadata? = byId.load(malId) {
        official?.fetchById(malId)
            ?: get<JikanSingleResponse>("/anime/$malId")?.data?.toExternalMetadata()
    }

    suspend fun search(name: String): List<ScoredEntry>? = searches.load(name.trim().lowercase()) {
        official?.search(name) ?: run {
            // `sfw` keeps adult entries out of the candidate pool, which matters because a name that
            // matches a mainstream show also matches its parody often enough to pick the wrong one.
            val body = get<JikanListResponse>("/anime?q=${name.urlEncoded()}&limit=10&sfw=true") ?: return@run null
            body.data.orEmpty()
                .filter { it.approved != false }
                .map { ScoredEntry(it.toMatchCandidate(), it.toExternalMetadata()) }
        }
    }

    private companion object {
        const val BASE_URL = "https://api.jikan.moe/v4"
    }
}

/**
 * MAL's official v2 API, reading public data with the app's client id - no user account, no OAuth.
 * Faster than Jikan, which scrapes MAL through its own cache under a 60-a-minute limit.
 */
class MalOfficialClient(private val client: HttpClient, private val clientId: String) {
    // MAL documents no limit; about one request a second is what it tolerates in practice.
    private val queue = MetadataRequestQueue(minIntervalMillis = 1_000, burst = 3)

    fun estimatedWaitMillis(): Long = queue.estimatedWaitMillis()

    private suspend inline fun <reified T> get(path: String): T? = queue.run(
        request = {
            client.get(BASE_URL + path) {
                header(CLIENT_ID_HEADER, clientId)
                header(HttpHeaders.Accept, "application/json")
            }
        },
        parse = { it.decode<T>() },
    )

    suspend fun fetchById(malId: Int): ExternalMetadata? =
        get<MalOfficialAnime>("/anime/$malId?fields=$MAL_OFFICIAL_FIELDS")?.toExternalMetadata()

    suspend fun search(name: String): List<ScoredEntry>? {
        // `nsfw=false` for the same reason Jikan is asked for `sfw`.
        val body = get<MalOfficialListResponse>("/anime?q=${name.urlEncoded()}&limit=10&nsfw=false&fields=$MAL_OFFICIAL_FIELDS")
            ?: return null
        return body.data.orEmpty()
            .mapNotNull(MalOfficialNode::node)
            .map { ScoredEntry(it.toMatchCandidate(), it.toExternalMetadata()) }
    }

    private companion object {
        const val BASE_URL = "https://api.myanimelist.net/v2"
        const val CLIENT_ID_HEADER = "X-MAL-CLIENT-ID"
    }
}

/** Kitsu's public JSON:API. No key, no account. */
class KitsuClient(private val client: HttpClient) {
    // Kitsu publishes no hard rate limit, so this is a courtesy pace rather than a documented one.
    private val queue = MetadataRequestQueue(minIntervalMillis = 400, burst = 5)
    private val byId = RequestCoalescer<Int, ExternalMetadata?>()
    private val bySlug = RequestCoalescer<String, ExternalMetadata?>()
    private val byMalId = RequestCoalescer<Int, ExternalMetadata?>()
    private val searches = RequestCoalescer<String, List<ScoredEntry>?>()


    fun estimatedWaitMillis(): Long = queue.estimatedWaitMillis()

    private suspend inline fun <reified T> get(path: String): T? = queue.run(
        // JSON:API's own media type, which is what Kitsu's documentation asks for.
        request = { client.get(BASE_URL + path) { header(HttpHeaders.Accept, "application/vnd.api+json") } },
        parse = { it.decode<T>() },
    )

    suspend fun fetchById(kitsuId: Int): ExternalMetadata? = byId.load(kitsuId) {
        val body = get<KitsuSingleResponse>("/anime/$kitsuId?include=$KITSU_INCLUDE") ?: return@load null
        body.data?.toExternalMetadata(body.included.orEmpty())
    }

    /** Kitsu's web URLs name a title by slug, so a pasted link resolves through this rather than by
     * id. */
    suspend fun fetchBySlug(slug: String): ExternalMetadata? = bySlug.load(slug.trim().lowercase()) {
        val body = get<KitsuListResponse>("/anime?filter[slug]=${slug.urlEncoded()}&include=$KITSU_INCLUDE") ?: return@load null
        body.data.orEmpty().firstOrNull()?.toExternalMetadata(body.included.orEmpty())
    }

    /** Kitsu indexes the other providers' ids as first-class records, so a title already matched
     * elsewhere can be bound here exactly, with no search and no guessing. */
    suspend fun fetchByMalId(malId: Int): ExternalMetadata? = byMalId.load(malId) {
        val body = get<KitsuMappingsResponse>("/mappings?filter[externalSite]=myanimelist/anime&filter[externalId]=$malId")
            ?: return@load null
        val kitsuId = body.data.orEmpty().firstNotNullOfOrNull { it.relationships?.item?.data?.id?.toIntOrNull() }
            ?: return@load null
        fetchById(kitsuId)
    }

    /**
     * A page of Kitsu's catalog.
     *
     * "Trending" is its own endpoint with no paging and no filters of its own, so a request for a
     * later page of it has nothing to return - the catalog screen stops there rather than
     * pretending.
     */
    suspend fun browse(request: ExternalCatalogRequest): List<ExternalMetadata>? {
        val query = request.query?.trim()?.takeIf { it.isNotEmpty() }
        if (query == null && request.mode == ExternalCatalogRequest.Mode.TRENDING) {
            // A fixed list rather than something to page through: a later page has nothing to return.
            if (request.offset > 0) return emptyList()
            return get<KitsuListResponse>("/trending/anime?limit=${request.limit}&include=$KITSU_INCLUDE")?.entries()
        }
        val listing = when {
            // A text search pages like any other listing. Kitsu cannot filter it - the service never
            // asks it to, see FILTERABLE_CATALOG_PROVIDERS.
            query != null -> "/anime?filter[text]=${query.urlEncoded()}"
            request.mode == ExternalCatalogRequest.Mode.SEASON -> {
                val (nowSeason, nowYear) = seasonNow()
                "/anime?filter[season]=${(request.season ?: nowSeason).id}&filter[seasonYear]=${request.seasonYear ?: nowYear}&sort=-userCount"
            }
            // Kitsu's own popularity ranking, a lifetime count rather than a recent one - the
            // difference between this and "trending" above.
            else -> "/anime?sort=-userCount"
        }
        // Kitsu refuses a listing page larger than 20 with a 400 - and the catalog's own page is larger -
        // so a bigger window is read in pages of 20 from the same offset and stitched back together.
        val collected = mutableListOf<ExternalMetadata>()
        var offset = request.offset
        while (collected.size < request.limit) {
            val pageLimit = minOf(MAX_LISTING_PAGE, request.limit - collected.size)
            val page = get<KitsuListResponse>("$listing&page[limit]=$pageLimit&page[offset]=$offset&include=$KITSU_INCLUDE")
                ?.entries()
                ?: return if (collected.isEmpty()) null else collected
            collected += page
            if (page.size < pageLimit) break
            offset += page.size
        }
        return collected
    }

    private fun KitsuListResponse.entries(): List<ExternalMetadata> {
        val included = included.orEmpty()
        return data.orEmpty().map { it.toExternalMetadata(included) }
    }

    suspend fun search(name: String): List<ScoredEntry>? = searches.load(name.trim().lowercase()) {
        val body = get<KitsuListResponse>("/anime?filter[text]=${name.urlEncoded()}&page[limit]=10&include=$KITSU_INCLUDE")
            ?: return@load null
        val included = body.included.orEmpty()
        body.data.orEmpty().map { ScoredEntry(it.toMatchCandidate(), it.toExternalMetadata(included)) }
    }

    private companion object {
        const val BASE_URL = "https://kitsu.io/api/edge"
        const val MAX_LISTING_PAGE = 20
    }
}

/** Adds a list variable only when it has anything in it - see [AniListClient.browse] for why an unset one is left out rather than sent as null. */
private fun kotlinx.serialization.json.JsonObjectBuilder.putEnumList(name: String, values: List<String>) {
    if (values.isNotEmpty()) put(name, JsonArray(values.distinct().map(::JsonPrimitive)))
}

/** One search result: what the matcher compares, and what the merge would use if it is chosen. */
data class ScoredEntry(val candidate: MatchCandidate, val media: ExternalMetadata)

private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")
