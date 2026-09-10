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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    // AniList allows about 90 requests a minute per IP. Nothing here is latency-critical - a screen
    // paints from the source first and fills metadata in as it arrives.
    private val queue = MetadataRequestQueue(minIntervalMillis = 700)

    private suspend fun graphql(query: String, variables: JsonObject): AniListData? = queue.run(
        request = {
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
        parse = { it.decode<AniListResponse>()?.data },
    )

    suspend fun fetchById(anilistId: Int): ExternalMetadata? = graphql(
        "query (\$id: Int) { Media(id: \$id, type: ANIME) { $ANILIST_MEDIA_FIELDS } }",
        buildJsonObject { put("id", anilistId) },
    )?.media?.toExternalMetadata()

    /** Looks a title up by its MAL id - what lets a match established elsewhere be reused here
     * without searching by name again. */
    suspend fun fetchByMalId(malId: Int): ExternalMetadata? = graphql(
        "query (\$idMal: Int) { Media(idMal: \$idMal, type: ANIME) { $ANILIST_MEDIA_FIELDS } }",
        buildJsonObject { put("idMal", malId) },
    )?.media?.toExternalMetadata()

    suspend fun search(name: String): List<ScoredEntry>? {
        val data = graphql(
            "query (\$search: String) { Page(perPage: 10) { media(search: \$search, type: ANIME) { $ANILIST_MEDIA_FIELDS } } }",
            buildJsonObject { put("search", name) },
        ) ?: return null
        return data.page?.media.orEmpty().map { ScoredEntry(it.toMatchCandidate(), it.toExternalMetadata()) }
    }

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
    }
}

/** Jikan, MAL's unofficial read-only API. No key and no account, which is why it is here: the
 * official MAL API needs a registered client id even to read. */
class MalClient(private val client: HttpClient) {
    // Jikan publishes two limits, 3 requests a second and 60 a minute; the minute one binds.
    private val queue = MetadataRequestQueue(minIntervalMillis = 1_100)

    private suspend inline fun <reified T> get(path: String): T? = queue.run(
        request = { client.get(BASE_URL + path) { header(HttpHeaders.Accept, "application/json") } },
        parse = { it.decode<T>() },
    )

    suspend fun fetchById(malId: Int): ExternalMetadata? =
        get<JikanSingleResponse>("/anime/$malId")?.data?.toExternalMetadata()

    suspend fun search(name: String): List<ScoredEntry>? {
        // `sfw` keeps adult entries out of the candidate pool, which matters because a name that
        // matches a mainstream show also matches its parody often enough to pick the wrong one.
        val body = get<JikanListResponse>("/anime?q=${name.urlEncoded()}&limit=10&sfw=true") ?: return null
        return body.data.orEmpty()
            .filter { it.approved != false }
            .map { ScoredEntry(it.toMatchCandidate(), it.toExternalMetadata()) }
    }

    private companion object {
        const val BASE_URL = "https://api.jikan.moe/v4"
    }
}

/** Kitsu's public JSON:API. No key, no account. */
class KitsuClient(private val client: HttpClient) {
    // Kitsu publishes no hard rate limit, so this is a courtesy pace rather than a documented one.
    private val queue = MetadataRequestQueue(minIntervalMillis = 400)

    private suspend inline fun <reified T> get(path: String): T? = queue.run(
        // JSON:API's own media type, which is what Kitsu's documentation asks for.
        request = { client.get(BASE_URL + path) { header(HttpHeaders.Accept, "application/vnd.api+json") } },
        parse = { it.decode<T>() },
    )

    suspend fun fetchById(kitsuId: Int): ExternalMetadata? {
        val body = get<KitsuSingleResponse>("/anime/$kitsuId?include=$KITSU_INCLUDE") ?: return null
        return body.data?.toExternalMetadata(body.included.orEmpty())
    }

    /** Kitsu's web URLs name a title by slug, so a pasted link resolves through this rather than by
     * id. */
    suspend fun fetchBySlug(slug: String): ExternalMetadata? {
        val body = get<KitsuListResponse>("/anime?filter[slug]=${slug.urlEncoded()}&include=$KITSU_INCLUDE") ?: return null
        return body.data.orEmpty().firstOrNull()?.toExternalMetadata(body.included.orEmpty())
    }

    /** Kitsu indexes the other providers' ids as first-class records, so a title already matched
     * elsewhere can be bound here exactly, with no search and no guessing. */
    suspend fun fetchByMalId(malId: Int): ExternalMetadata? {
        val body = get<KitsuMappingsResponse>("/mappings?filter[externalSite]=myanimelist/anime&filter[externalId]=$malId")
            ?: return null
        val kitsuId = body.data.orEmpty().firstNotNullOfOrNull { it.relationships?.item?.data?.id?.toIntOrNull() }
            ?: return null
        return fetchById(kitsuId)
    }

    suspend fun search(name: String): List<ScoredEntry>? {
        val body = get<KitsuListResponse>("/anime?filter[text]=${name.urlEncoded()}&page[limit]=10&include=$KITSU_INCLUDE")
            ?: return null
        val included = body.included.orEmpty()
        return body.data.orEmpty().map { ScoredEntry(it.toMatchCandidate(), it.toExternalMetadata(included)) }
    }

    private companion object {
        const val BASE_URL = "https://kitsu.io/api/edge"
    }
}

/** One search result: what the matcher compares, and what the merge would use if it is chosen. */
data class ScoredEntry(val candidate: MatchCandidate, val media: ExternalMetadata)

private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")
