package org.akkirrai.hibiki.core.discord

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory

data class DiscordAccount(
    val username: String,
    val displayName: String,
)

class DiscordAuthenticationException : IllegalStateException()

class DiscordRepository(
    private val client: HttpClient = AndroidHttpClientFactory.create(),
) {
    suspend fun getAccount(token: String): DiscordAccount {
        val response = client.get("https://discord.com/api/v10/users/@me") {
            header(HttpHeaders.Authorization, token)
        }
        if (!response.status.isSuccess()) throw DiscordAuthenticationException()
        val user = response.body<DiscordUser>()
        return DiscordAccount(
            username = user.username,
            displayName = user.globalName?.takeIf(String::isNotBlank) ?: user.username,
        )
    }

    suspend fun getMediaProxyUrl(
        applicationId: String,
        token: String,
        url: String,
    ): String? {
        if (url.startsWith(MEDIA_PROXY_SCHEME)) return url
        val response = client.post("https://discord.com/api/v10/applications/$applicationId/external-assets") {
            header(HttpHeaders.Authorization, token)
            contentType(ContentType.Application.Json)
            setBody(JsonObject(mapOf("urls" to JsonArray(listOf(JsonPrimitive(url))))))
        }
        if (!response.status.isSuccess()) {
            if (response.status.value == 401 || response.status.value == 403) {
                throw DiscordAuthenticationException()
            }
            return null
        }
        return response.body<JsonArray>()
            .firstOrNull()
            ?.jsonObject
            ?.get("external_asset_path")
            ?.jsonPrimitive
            ?.content
            ?.let(MEDIA_PROXY_SCHEME::plus)
    }

    fun close() = client.close()

    private companion object {
        const val MEDIA_PROXY_SCHEME = "mp:"
    }
}

@Serializable
private data class DiscordUser(
    val username: String,
    @SerialName("global_name") val globalName: String? = null,
)
