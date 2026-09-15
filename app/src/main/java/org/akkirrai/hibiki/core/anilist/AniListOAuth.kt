package org.akkirrai.hibiki.core.anilist

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

/**
 * AniList's mobile flow is the OAuth implicit grant: the app receives a bearer token in the
 * redirect fragment and deliberately never contains a client secret.
 */
object AniListOAuth {
    const val redirectUri = "hibiki://anilist-auth"

    fun createAuthorizationRequest(clientId: String): AniListAuthorizationRequest? {
        if (clientId.isBlank()) return null
        val stateBytes = ByteArray(24).also(SecureRandom()::nextBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        val url = buildString {
            append("https://anilist.co/api/v2/oauth/authorize?")
            append("client_id=").append(clientId.encodeQueryComponent())
            append("&redirect_uri=").append(redirectUri.encodeQueryComponent())
            append("&response_type=token")
            append("&state=").append(state.encodeQueryComponent())
        }
        return AniListAuthorizationRequest(url = url, state = state)
    }

    fun parseRedirect(url: String): AniListOAuthCallback? = runCatching {
        val uri = URI(url)
        val parameters = parseParameters(uri.rawFragment ?: uri.rawQuery.orEmpty())
        when {
            parameters["error"].isNullOrBlank().not() -> AniListOAuthCallback.Error(
                code = parameters.getValue("error"),
                description = parameters["error_description"],
                state = parameters["state"],
            )

            parameters["access_token"].isNullOrBlank().not() -> AniListOAuthCallback.Success(
                accessToken = parameters.getValue("access_token"),
                expiresInSeconds = parameters["expires_in"]?.toLongOrNull(),
                state = parameters["state"],
            )

            else -> null
        }
    }.getOrNull()

    private fun parseParameters(raw: String): Map<String, String> = raw
        .split('&')
        .mapNotNull { component ->
            val separator = component.indexOf('=')
            if (separator < 0) return@mapNotNull null
            component.substring(0, separator).decodeQueryComponent() to
                component.substring(separator + 1).decodeQueryComponent()
        }
        .toMap()

    private fun String.encodeQueryComponent(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")

    private fun String.decodeQueryComponent(): String = URLDecoder.decode(this, StandardCharsets.UTF_8)
}

data class AniListAuthorizationRequest(
    val url: String,
    val state: String,
)

sealed interface AniListOAuthCallback {
    val state: String?

    data class Success(
        val accessToken: String,
        val expiresInSeconds: Long?,
        override val state: String?,
    ) : AniListOAuthCallback

    data class Error(
        val code: String,
        val description: String?,
        override val state: String?,
    ) : AniListOAuthCallback
}
