package org.akkirrai.hibiki.core.anilist

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A title in someone's AniList library, with the names and facts needed to find it on a source. */
data class AniListSyncEntry(
    val mediaId: Int,
    val status: AniListMediaListStatus?,
    val progress: Int,
    val updatedAtSeconds: Long?,
    val names: List<String>,
    val year: Int?,
    val format: String?,
    val episodes: Int?,
)

class AniListPrivateListException : IllegalStateException("The AniList profile or its list is private")
class AniListUserNotFoundException : IllegalStateException("AniList user not found")

/**
 * Reads a user's list and favourites, and (with a token) writes entries. Reading needs no sign-in for a
 * public profile, which keeps a one-way pull free of OAuth entirely.
 */
class AniListPublicLibrary(
    private val client: HttpClient,
    /** A signed-in user's token, which also opens a private list; without it only public lists are read. */
    private val accessToken: String? = null,
) {

    suspend fun library(userName: String): List<AniListSyncEntry> {
        val data = execute<CollectionData>(
            COLLECTION_QUERY,
            JsonObject(mapOf("userName" to JsonPrimitive(userName))),
        )
        // A title on a custom list appears once per list it is on; the first (status list) wins.
        return data.collection.lists.orEmpty()
            .sortedBy { it.isCustomList == true }
            .flatMap { it.entries.orEmpty() }
            .distinctBy { it.mediaId }
            .map { item ->
                val media = item.media
                AniListSyncEntry(
                    mediaId = item.mediaId,
                    status = item.status,
                    progress = item.progress,
                    updatedAtSeconds = item.updatedAt,
                    names = media?.names().orEmpty(),
                    year = media?.year(),
                    format = media?.format,
                    episodes = media?.episodes,
                )
            }
    }

    suspend fun favourites(userName: String): List<AniListSyncEntry> {
        val result = mutableListOf<AniListSyncEntry>()
        var page = 1
        while (page <= MAX_FAVOURITE_PAGES) {
            val data = execute<FavouritesData>(
                FAVOURITES_QUERY,
                JsonObject(mapOf("userName" to JsonPrimitive(userName), "page" to JsonPrimitive(page))),
            )
            val anime = data.user?.favourites?.anime ?: break
            anime.nodes.orEmpty().forEach { media ->
                result += AniListSyncEntry(
                    mediaId = media.id,
                    status = null,
                    progress = 0,
                    updatedAtSeconds = null,
                    names = media.names(),
                    year = media.year(),
                    format = media.format,
                    episodes = media.episodes,
                )
            }
            if (anime.pageInfo?.hasNextPage != true) break
            page++
        }
        return result
    }

    suspend fun search(name: String): List<AniListSyncEntry> {
        val data = execute<SearchData>(
            SEARCH_QUERY,
            JsonObject(mapOf("q" to JsonPrimitive(name))),
        )
        return data.page.media.orEmpty().map { media ->
            AniListSyncEntry(
                mediaId = media.id,
                status = null,
                progress = 0,
                updatedAtSeconds = null,
                names = media.names(),
                year = media.year(),
                format = media.format,
                episodes = media.episodes,
            )
        }
    }

    /** Creates or updates list entries: each is a title, the status to set and the progress to set (either may be null). */
    suspend fun saveEntries(entries: List<Triple<Int, AniListMediaListStatus?, Int?>>) {
        if (entries.isEmpty()) return
        // Ids, enum names and integers only, so nothing here needs escaping.
        val body = entries.mapIndexed { index, (mediaId, status, progress) ->
            val args = buildList {
                add("mediaId: $mediaId")
                status?.let { add("status: ${it.name}") }
                progress?.let { add("progress: $it") }
            }.joinToString(", ")
            "e$index: SaveMediaListEntry($args) { id }"
        }.joinToString("\n")
        execute<JsonObject>("mutation {\n$body\n}", JsonObject(emptyMap()))
    }

    /** Toggles favourites on: the caller passes only titles that are not favourites yet. */
    suspend fun toggleFavourites(mediaIds: List<Int>) {
        if (mediaIds.isEmpty()) return
        val body = mediaIds.mapIndexed { index, id ->
            "f$index: ToggleFavourite(animeId: $id) { anime { nodes { id } } }"
        }.joinToString("\n")
        execute<JsonObject>("mutation {\n$body\n}", JsonObject(emptyMap()))
    }

    private suspend inline fun <reified T> execute(query: String, variables: JsonObject): T {
        val response = client.post(GRAPHQL_URL) {
            accessToken?.let { header(io.ktor.http.HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(query, variables))
        }
        // AniList answers a private or missing user with an error status and a message body.
        val text = runCatching { response.body<GraphQlResponse<T>>() }.getOrNull()
        text?.errors?.firstOrNull()?.let { error ->
            when {
                error.message.contains("private", ignoreCase = true) -> throw AniListPrivateListException()
                error.status == 404 || error.message.contains("not found", ignoreCase = true) ->
                    throw AniListUserNotFoundException()
                else -> throw AniListApiException(error.message)
            }
        }
        if (!response.status.isSuccess()) throw AniListApiException("AniList returned HTTP ${response.status.value}")
        return text?.data ?: throw AniListApiException("AniList returned no data")
    }

    private companion object {
        const val GRAPHQL_URL = "https://graphql.anilist.co"
        const val MAX_FAVOURITE_PAGES = 20
        const val MEDIA_FIELDS = "id episodes format seasonYear startDate { year } synonyms " +
            "title { romaji english native }"
        const val COLLECTION_QUERY = """
            query Library(${'$'}userName: String) {
              MediaListCollection(userName: ${'$'}userName, type: ANIME) {
                lists {
                  isCustomList
                  entries { mediaId status progress updatedAt media { $MEDIA_FIELDS } }
                }
              }
            }
        """
        const val SEARCH_QUERY = """
            query Search(${'$'}q: String) {
              Page(perPage: 6) {
                media(search: ${'$'}q, type: ANIME) { $MEDIA_FIELDS }
              }
            }
        """
        const val FAVOURITES_QUERY = """
            query Favourites(${'$'}userName: String, ${'$'}page: Int) {
              User(name: ${'$'}userName) {
                favourites {
                  anime(page: ${'$'}page, perPage: 25) {
                    pageInfo { hasNextPage }
                    nodes { $MEDIA_FIELDS }
                  }
                }
              }
            }
        """
    }
}

@Serializable private data class GraphQlRequest(val query: String, val variables: JsonObject)
@Serializable private data class GraphQlResponse<T>(val data: T? = null, val errors: List<GraphQlError>? = null)
@Serializable private data class GraphQlError(val message: String = "", val status: Int? = null)

@Serializable private data class CollectionData(
    @kotlinx.serialization.SerialName("MediaListCollection") val collection: CollectionDto,
)
@Serializable private data class CollectionDto(val lists: List<ListDto>? = null)
@Serializable private data class ListDto(val isCustomList: Boolean? = null, val entries: List<EntryDto>? = null)
@Serializable private data class EntryDto(
    val mediaId: Int,
    val status: AniListMediaListStatus? = null,
    val progress: Int = 0,
    val updatedAt: Long? = null,
    val media: MediaDto? = null,
)
@Serializable private data class SearchData(
    @kotlinx.serialization.SerialName("Page") val page: SearchPageDto,
)
@Serializable private data class SearchPageDto(val media: List<MediaDto>? = null)
@Serializable private data class FavouritesData(
    @kotlinx.serialization.SerialName("User") val user: UserDto? = null,
)
@Serializable private data class UserDto(val favourites: FavouritesDto? = null)
@Serializable private data class FavouritesDto(val anime: FavouriteAnimeDto? = null)
@Serializable private data class FavouriteAnimeDto(val pageInfo: PageInfoDto? = null, val nodes: List<MediaDto>? = null)
@Serializable private data class PageInfoDto(val hasNextPage: Boolean? = null)
@Serializable private data class MediaDto(
    val id: Int,
    val episodes: Int? = null,
    val format: String? = null,
    val seasonYear: Int? = null,
    val startDate: DateDto? = null,
    val synonyms: List<String>? = null,
    val title: TitleDto? = null,
) {
    fun year(): Int? = seasonYear ?: startDate?.year

    fun names(): List<String> = buildList {
        title?.let { addAll(listOfNotNull(it.english, it.romaji, it.native)) }
        addAll(synonyms.orEmpty())
    }.filter(String::isNotBlank).distinct()
}
@Serializable private data class DateDto(val year: Int? = null)
@Serializable private data class TitleDto(val romaji: String? = null, val english: String? = null, val native: String? = null)
