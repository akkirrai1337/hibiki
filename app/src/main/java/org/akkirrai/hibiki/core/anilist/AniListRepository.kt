package org.akkirrai.hibiki.core.anilist

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory

/**
 * Non-UI foundation for a future AniList library sync. Nothing instantiates this repository yet;
 * adding the account screen later only needs to launch [beginAuthorization] and pass its redirect
 * to [completeAuthorization].
 */
class AniListRepository(
    context: Context,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val tokenStore = AniListTokenStore(context)

    val isConfigured: Boolean get() = BuildConfig.ANILIST_CLIENT_ID.isNotBlank()

    fun beginAuthorization(): AniListAuthorizationRequest? {
        val request = AniListOAuth.createAuthorizationRequest(BuildConfig.ANILIST_CLIENT_ID) ?: return null
        val current = tokenStore.read() ?: AniListStoredSession()
        tokenStore.save(current.copy(pendingState = request.state))
        return request
    }

    fun completeAuthorization(redirectUrl: String): AniListAuthorizationResult {
        val callback = AniListOAuth.parseRedirect(redirectUrl) ?: return AniListAuthorizationResult.InvalidRedirect
        val current = tokenStore.read()
        if (current?.pendingState.isNullOrBlank() || current?.pendingState != callback.state) {
            return AniListAuthorizationResult.StateMismatch
        }

        return when (callback) {
            is AniListOAuthCallback.Error -> {
                tokenStore.save(current.copy(pendingState = null))
                AniListAuthorizationResult.OAuthError(callback.code, callback.description)
            }

            is AniListOAuthCallback.Success -> {
                val expiresAt = callback.expiresInSeconds
                    ?.coerceAtLeast(0)
                    ?.let { seconds -> nowMillis() + seconds * 1_000 }
                tokenStore.save(
                    current.copy(
                        accessToken = callback.accessToken,
                        expiresAtMillis = expiresAt,
                        pendingState = null,
                    ),
                )
                AniListAuthorizationResult.Connected
            }
        }
    }

    fun currentAccessToken(): String? {
        val session = tokenStore.read() ?: return null
        if (session.expiresAtMillis != null && session.expiresAtMillis <= nowMillis()) {
            tokenStore.clear()
            return null
        }
        return session.accessToken
    }

    fun disconnect() = tokenStore.clear()

    suspend fun getViewer(): AniListViewer = execute<AniListViewerData, AniListViewer>(
        query = "query { Viewer { id name avatar { large } } }",
    ) { it.viewer.toModel() }

    suspend fun getLibraryPage(
        userId: Int,
        page: Int = 1,
        perPage: Int = 50,
    ): AniListLibraryPage = execute<AniListLibraryData, AniListLibraryPage>(
        query = LIBRARY_QUERY,
        variables = JsonObject(
            mapOf(
                "userId" to JsonPrimitive(userId),
                "page" to JsonPrimitive(page),
                "perPage" to JsonPrimitive(perPage.coerceIn(1, 50)),
            ),
        ),
    ) { data ->
        AniListLibraryPage(
            entries = data.page.mediaList.map(AniListMediaListItem::toModel),
            hasNextPage = data.page.pageInfo.hasNextPage,
        )
    }

    suspend fun updateProgress(
        mediaId: Int,
        progress: Int,
        status: AniListMediaListStatus? = null,
    ): AniListLibraryEntry = execute<AniListSaveProgressData, AniListLibraryEntry>(
        query = SAVE_PROGRESS_MUTATION,
        variables = buildMap {
            put("mediaId", JsonPrimitive(mediaId))
            put("progress", JsonPrimitive(progress.coerceAtLeast(0)))
            status?.let { put("status", JsonPrimitive(it.name)) }
        }.let(::JsonObject),
    ) { it.saveMediaListEntry.toModel() }

    fun close() = client.close()

    private suspend inline fun <reified T, R> execute(
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
        transform: (T) -> R,
    ): R {
        val token = currentAccessToken() ?: throw AniListAuthenticationException()
        val response = client.post(GRAPHQL_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(AniListGraphQlRequest(query, variables))
        }
        if (!response.status.isSuccess()) throw AniListApiException("AniList returned HTTP ${response.status.value}")
        val result = response.body<AniListGraphQlResponse<T>>()
        result.errors?.firstOrNull()?.let { error -> throw AniListApiException(error.message) }
        return transform(result.data ?: throw AniListApiException("AniList returned no data"))
    }

    private companion object {
        const val GRAPHQL_URL = "https://graphql.anilist.co"
        const val LIBRARY_QUERY = """
            query Library(${'$'}userId: Int!, ${'$'}page: Int!, ${'$'}perPage: Int!) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage }
                mediaList(userId: ${'$'}userId, type: ANIME, sort: UPDATED_TIME_DESC) {
                  mediaId status progress updatedAt
                  media { id episodes title { romaji english native } coverImage { large } }
                }
              }
            }
        """
        const val SAVE_PROGRESS_MUTATION = """
            mutation SaveProgress(${'$'}mediaId: Int!, ${'$'}progress: Int!, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                mediaId status progress updatedAt
              }
            }
        """
    }
}

sealed interface AniListAuthorizationResult {
    data object Connected : AniListAuthorizationResult
    data object InvalidRedirect : AniListAuthorizationResult
    data object StateMismatch : AniListAuthorizationResult
    data class OAuthError(val code: String, val description: String?) : AniListAuthorizationResult
}

class AniListAuthenticationException : IllegalStateException("AniList is not connected")
class AniListApiException(message: String) : IllegalStateException(message)

data class AniListViewer(val id: Int, val name: String, val avatarUrl: String?)
data class AniListLibraryPage(val entries: List<AniListLibraryEntry>, val hasNextPage: Boolean)
data class AniListLibraryEntry(
    val mediaId: Int,
    val progress: Int,
    val status: AniListMediaListStatus?,
    val updatedAtSeconds: Long?,
    val title: AniListMediaTitle? = null,
    val coverUrl: String? = null,
    val episodes: Int? = null,
)
data class AniListMediaTitle(val romaji: String?, val english: String?, val native: String?)
enum class AniListMediaListStatus { CURRENT, PLANNING, COMPLETED, DROPPED, PAUSED, REPEATING }

@Serializable private data class AniListGraphQlRequest(val query: String, val variables: JsonObject)
@Serializable private data class AniListGraphQlResponse<T>(val data: T? = null, val errors: List<AniListGraphQlError>? = null)
@Serializable private data class AniListGraphQlError(val message: String)
@Serializable private data class AniListViewerData(val viewer: AniListViewerDto)
@Serializable private data class AniListViewerDto(val id: Int, val name: String, val avatar: AniListAvatarDto? = null)
@Serializable private data class AniListAvatarDto(val large: String? = null)
@Serializable private data class AniListLibraryData(val page: AniListPageDto)
@Serializable private data class AniListPageDto(val pageInfo: AniListPageInfoDto, val mediaList: List<AniListMediaListItem>)
@Serializable private data class AniListPageInfoDto(val hasNextPage: Boolean)
@Serializable private data class AniListMediaListItem(
    val mediaId: Int,
    val status: AniListMediaListStatus? = null,
    val progress: Int = 0,
    val updatedAt: Long? = null,
    val media: AniListMediaDto? = null,
)
@Serializable private data class AniListMediaDto(
    val id: Int,
    val episodes: Int? = null,
    val title: AniListMediaTitleDto? = null,
    val coverImage: AniListCoverImageDto? = null,
)
@Serializable private data class AniListMediaTitleDto(val romaji: String? = null, val english: String? = null, val native: String? = null)
@Serializable private data class AniListCoverImageDto(val large: String? = null)
@Serializable private data class AniListSaveProgressData(val saveMediaListEntry: AniListMediaListItem)

private fun AniListViewerDto.toModel() = AniListViewer(id, name, avatar?.large)
private fun AniListMediaListItem.toModel() = AniListLibraryEntry(
    mediaId = mediaId,
    progress = progress,
    status = status,
    updatedAtSeconds = updatedAt,
    title = media?.title?.let { AniListMediaTitle(it.romaji, it.english, it.native) },
    coverUrl = media?.coverImage?.large,
    episodes = media?.episodes,
)
