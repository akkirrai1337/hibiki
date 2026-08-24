package org.akkirrai.hibiki.core.anilist

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.akkirrai.hibiki.core.network.HttpClientFactory

private const val ANILIST_ENDPOINT = "https://graphql.anilist.co"

/** Thin transport for AniList's GraphQL API. Never throws: any failure surfaces as null/empty. */
internal class AniListGraphQlClient(
    private val client: HttpClient = HttpClientFactory.create(),
) {
    suspend fun search(query: String, page: Int = 1, perPage: Int = 10): List<AniListSearchMedia> {
        val variables = buildJsonObject {
            put("search", query)
            put("page", page)
            put("perPage", perPage)
        }
        val data = execute<AniListSearchData>(ANILIST_SEARCH_QUERY, variables) ?: return emptyList()
        return data.Page?.media ?: emptyList()
    }

    suspend fun getById(id: Int): AniListMediaDetail? {
        val variables = buildJsonObject { put("id", id) }
        return execute<AniListMediaData>(ANILIST_DETAILS_QUERY, variables)?.Media
    }

    private suspend inline fun <reified T> execute(query: String, variables: JsonObject): T? {
        return runCatching {
            val response = client.post(ANILIST_ENDPOINT) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", query)
                        put("variables", variables)
                    },
                )
            }
            if (!response.status.isSuccess()) return null
            response.body<AniListGraphQlResponse<T>>().data
        }.getOrNull()
    }

    fun close() = client.close()
}
