package org.akkirrai.animeresolver.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.animeresolver.network.toReadableHttpErrorDetails

class YummyAccountApi(
    private val client: HttpClient,
    private val applicationToken: String? = null,
    private val accessTokenProvider: (() -> String?)? = null,
    private val baseUrl: String = "https://api.yani.tv",
    private val debugLogger: ((String) -> Unit)? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun login(
        login: String,
        password: String,
        recaptchaResponse: String? = null,
    ): YummyLoginResult {
        val response = client.post("$baseUrl/profile/login") {
            addHeaders(accessToken = null)
            contentType(ContentType.Application.Json)
            setBody(
                YummyLoginRequest(
                    login = login,
                    password = password,
                    needJson = true,
                    recaptchaResponse = recaptchaResponse,
                )
            )
        }
        val rawBody = response.bodyAsText()
        debugLogger?.invoke(
            buildString {
                append("Yummy login response: HTTP ")
                append(response.status.value)
                response.contentType()?.toString()?.takeIf(String::isNotBlank)?.let { type ->
                    append(", contentType=")
                    append(type)
                }
                val setCookieValues = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                if (setCookieValues.isNotEmpty()) {
                    append(", setCookieCount=")
                    append(setCookieValues.size)
                    append(", setCookieNames=")
                    append(setCookieValues.mapNotNull(::extractCookieName).joinToString("|"))
                }
                append(", ")
                append(rawBody.toLoginDebugSummary())
            }
        )

        if (!response.status.isSuccess()) {
            val message = if (response.status.value == 429) {
                "$SOURCE временно ограничил частоту запросов (HTTP 429)"
            } else {
                "$SOURCE вернул HTTP ${response.status.value}"
            }
            val normalizedDetails = rawBody.toReadableHttpErrorDetails()
            throw SourceException(
                message = if (normalizedDetails.isBlank()) message else "$message: $normalizedDetails",
                statusCode = response.status.value,
            )
        }

        val envelope = try {
            json.decodeFromString<YummyAccountEnvelope<JsonElement>>(rawBody)
        } catch (error: SerializationException) {
            throw SourceException(
                message = "$SOURCE вернул неожиданный ответ при входе: ${rawBody.toReadableHttpErrorDetails()}",
                cause = error,
            )
        }
        val cookieToken = response.headers.getAll(HttpHeaders.SetCookie)
            .orEmpty()
            .extractCookieToken("yummy_token")
        return envelope.response.toLoginResult(cookieToken = cookieToken)
    }

    suspend fun getProfile(accessToken: String? = null): YummyProfile {
        val response = client.get("$baseUrl/profile") {
            addHeaders(resolveAccessToken(accessToken))
        }
        return response.bodyOrThrow<YummyAccountEnvelope<YummyProfileResponse>>(SOURCE)
            .response
            .toModel()
    }

    suspend fun refreshToken(accessToken: String? = null): String {
        val response = client.get("$baseUrl/profile/token") {
            addHeaders(resolveAccessToken(accessToken))
        }
        return response.bodyOrThrow<YummyAccountEnvelope<YummyTokenResponse>>(SOURCE)
            .response
            .token
    }

    suspend fun logout(accessToken: String? = null) {
        client.post("$baseUrl/profile/logout") {
            addHeaders(resolveAccessToken(accessToken))
        }.bodyOrThrow<YummyAccountEnvelope<Unit?>>(SOURCE)
    }

    suspend fun getAnimeListState(
        animeId: Long,
        accessToken: String? = null,
    ): YummyAnimeListState {
        val response = client.get("$baseUrl/anime/$animeId/list") {
            addHeaders(resolveAccessToken(accessToken))
        }
        return response.bodyOrThrow<YummyAccountEnvelope<YummyAnimeListStateResponse>>(SOURCE)
            .response
            .toModel()
    }

    suspend fun addAnimeToList(
        animeId: Long,
        list: YummyUserList,
        accessToken: String? = null,
    ) {
        client.put("$baseUrl/anime/$animeId/list") {
            addHeaders(resolveAccessToken(accessToken))
            contentType(ContentType.Application.Json)
            setBody(YummyAnimeListUpdateRequest(list = list.value))
        }.bodyOrThrow<YummyAccountEnvelope<Unit?>>(SOURCE)
    }

    suspend fun removeAnimeFromList(
        animeId: Long,
        accessToken: String? = null,
    ) {
        client.delete("$baseUrl/anime/$animeId/list") {
            addHeaders(resolveAccessToken(accessToken))
        }.bodyOrThrow<YummyAccountEnvelope<Unit?>>(SOURCE)
    }

    suspend fun addAnimeToFavorites(
        animeId: Long,
        accessToken: String? = null,
    ) {
        client.put("$baseUrl/anime/$animeId/list/fav") {
            addHeaders(resolveAccessToken(accessToken))
        }.bodyOrThrow<YummyAccountEnvelope<Unit?>>(SOURCE)
    }

    suspend fun removeAnimeFromFavorites(
        animeId: Long,
        accessToken: String? = null,
    ) {
        client.delete("$baseUrl/anime/$animeId/list/fav") {
            addHeaders(resolveAccessToken(accessToken))
        }.bodyOrThrow<YummyAccountEnvelope<Unit?>>(SOURCE)
    }

    suspend fun getUserLists(
        userId: Long,
        accessToken: String? = null,
    ): List<YummyUserAnimeListItem> {
        val response = client.get("$baseUrl/users/$userId/lists") {
            addHeaders(resolveAccessToken(accessToken))
        }
        return response.bodyOrThrow<YummyAccountEnvelope<List<YummyUserAnimeListItemResponse>>>(SOURCE)
            .response
            .map(YummyUserAnimeListItemResponse::toModel)
    }

    suspend fun setUserOnline(accessToken: String? = null) {
        client.post("$baseUrl/profile/online") {
            addHeaders(resolveAccessToken(accessToken))
        }.bodyOrThrow<YummyAccountEnvelope<Unit?>>(SOURCE)
    }

    private fun resolveAccessToken(explicitToken: String?): String? {
        return explicitToken?.takeIf(String::isNotBlank)
            ?: accessTokenProvider?.invoke()?.takeIf(String::isNotBlank)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addHeaders(accessToken: String? = null) {
        header("Lang", "ru")
        applicationToken?.takeIf(String::isNotBlank)?.let {
            header("X-Application", it)
        }
        accessToken?.takeIf(String::isNotBlank)?.let {
            header(HttpHeaders.Authorization, "Yummy $it")
        }
    }

    private companion object {
        const val SOURCE = "YummyAnime"
    }
}

@Serializable
private data class YummyAccountEnvelope<T>(
    val response: T,
)

@Serializable
private data class YummyLoginRequest(
    val login: String,
    val password: String,
    @SerialName("need_json") val needJson: Boolean = true,
    @SerialName("recaptcha_response") val recaptchaResponse: String? = null,
)

@Serializable
private data class YummyTokenResponse(
    val token: String,
)

@Serializable
private data class YummyProfileResponse(
    val id: Long,
    val nickname: String? = null,
    val about: String? = null,
    val banned: Boolean = false,
    val ids: YummyProfileIdsResponse? = null,
    val avatars: YummyProfileAvatarsResponse? = null,
    val bdate: Long? = null,
    @SerialName("last_online") val lastOnline: Long? = null,
    val sex: Int? = null,
    val roles: List<String> = emptyList(),
    @SerialName("register_date") val registerDate: Long? = null,
    val texts: YummyProfileTextsResponse? = null,
    val banner: YummyProfileBannerResponse? = null,
    val watches: YummyProfileWatchesResponse? = null,
    @SerialName("days_online") val daysOnline: Int? = null,
    val oldNicknames: List<YummyOldNicknameResponse> = emptyList(),
) {
    fun toModel(): YummyProfile {
        return YummyProfile(
            id = id,
            nickname = nickname.orEmpty(),
            about = about,
            banned = banned,
            ids = ids?.toModel(),
            avatars = avatars?.toModel() ?: YummyProfileAvatars(),
            avatarUrl = avatars?.full ?: avatars?.big ?: avatars?.small,
            birthDate = bdate,
            lastOnline = lastOnline,
            sex = sex?.let(YummyProfileSex::fromValue),
            roles = roles,
            registerDate = registerDate,
            texts = texts?.toModel(),
            banner = banner?.toModel(),
            watches = watches?.toModel(),
            daysOnline = daysOnline,
            oldNicknames = oldNicknames.map(YummyOldNicknameResponse::toModel),
        )
    }
}

@Serializable
private data class YummyProfileIdsResponse(
    val shikimori: YummyProfileShikimoriResponse? = null,
    val vk: Long? = null,
    @SerialName("tg_nickname") val telegramNickname: String? = null,
) {
    fun toModel(): YummyProfileIds {
        return YummyProfileIds(
            shikimoriId = shikimori?.id,
            shikimoriNickname = shikimori?.nickname,
            vkId = vk,
            telegramNickname = telegramNickname,
        )
    }
}

@Serializable
private data class YummyProfileShikimoriResponse(
    val id: Long? = null,
    val nickname: String? = null,
)

@Serializable
private data class YummyProfileAvatarsResponse(
    val small: String? = null,
    val big: String? = null,
    val full: String? = null,
){
    fun toModel(): YummyProfileAvatars {
        return YummyProfileAvatars(
            small = small,
            big = big,
            full = full,
        )
    }
}

@Serializable
private data class YummyProfileTextsResponse(
    val color: Int? = null,
    val left: String? = null,
    val right: String? = null,
) {
    fun toModel(): YummyProfileTexts {
        return YummyProfileTexts(
            color = color,
            left = left,
            right = right,
        )
    }
}

@Serializable
private data class YummyProfileBannerResponse(
    val cropped: String? = null,
    val full: String? = null,
) {
    fun toModel(): YummyProfileBanner {
        return YummyProfileBanner(
            cropped = cropped,
            full = full,
        )
    }
}

@Serializable
private data class YummyProfileWatchesResponse(
    val sum: List<YummyProfileWatchSumResponse> = emptyList(),
    val history: List<YummyProfileWatchHistoryResponse> = emptyList(),
) {
    fun toModel(): YummyProfileWatches {
        return YummyProfileWatches(
            sum = sum.map(YummyProfileWatchSumResponse::toModel),
            history = history.map(YummyProfileWatchHistoryResponse::toModel),
        )
    }
}

@Serializable
private data class YummyProfileWatchSumResponse(
    val shortname: String? = null,
    val name: String? = null,
    @SerialName("spent_time") val spentTime: Long? = null,
    val value: Int? = null,
    val alias: String? = null,
) {
    fun toModel(): YummyProfileWatchSum {
        return YummyProfileWatchSum(
            shortName = shortname,
            name = name,
            spentTime = spentTime,
            value = value,
            alias = alias,
        )
    }
}

@Serializable
private data class YummyProfileWatchHistoryResponse(
    @SerialName("when") val watchedAt: Long? = null,
    @SerialName("ep_count") val episodeCount: Int? = null,
    val duration: Long? = null,
) {
    fun toModel(): YummyProfileWatchHistoryItem {
        return YummyProfileWatchHistoryItem(
            date = watchedAt,
            episodeCount = episodeCount,
            duration = duration,
        )
    }
}

@Serializable
private data class YummyOldNicknameResponse(
    val nickname: String? = null,
    val date: Long? = null,
) {
    fun toModel(): YummyOldNickname {
        return YummyOldNickname(
            nickname = nickname.orEmpty(),
            date = date,
        )
    }
}

@Serializable
private data class YummyAnimeListStateResponse(
    val list: Int? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
) {
    fun toModel(): YummyAnimeListState {
        return YummyAnimeListState(
            list = list?.let(YummyUserList::fromValue),
            isFavorite = isFavorite,
        )
    }
}

@Serializable
private data class YummyAnimeListUpdateRequest(
    val list: Int,
)

@Serializable
private data class YummyUserAnimeListItemResponse(
    @SerialName("anime_id") val animeId: Long,
    val title: String? = null,
    val list: Int? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val date: Long? = null,
) {
    fun toModel(): YummyUserAnimeListItem {
        return YummyUserAnimeListItem(
            animeId = animeId,
            title = title.orEmpty(),
            list = list?.let(YummyUserList::fromValue),
            isFavorite = isFavorite,
            addedAt = date,
        )
    }
}

data class YummyLoginResult(
    val success: Boolean,
    val accessToken: String,
    val errorMessage: String? = null,
    val requiresCaptcha: Boolean = false,
)

private fun JsonElement.toLoginResult(cookieToken: String? = null): YummyLoginResult {
    if (this is JsonPrimitive) {
        val token = contentOrNull.orEmpty().ifBlank { cookieToken.orEmpty() }
        return YummyLoginResult(
            success = token.isNotBlank(),
            accessToken = token,
        )
    }

    val objectValue = jsonObject
    val token = objectValue.string("token")
        ?: objectValue.string("access_token")
        ?: objectValue.string("accessToken")
        ?: objectValue.string("auth_token")
        ?: objectValue.jsonObject("data")?.let { nested ->
            nested.string("token")
                ?: nested.string("access_token")
                ?: nested.string("accessToken")
                ?: nested.string("auth_token")
        }
        ?: cookieToken
        .orEmpty()
    val success = objectValue.boolean("success") ?: token.isNotBlank()
    val errorMessage = listOfNotNull(
        objectValue.string("message"),
        objectValue.string("error"),
        objectValue["errors"].extractReadableMessage(),
        objectValue.jsonObject("data")?.get("errors").extractReadableMessage(),
    ).firstOrNull { it.isNotBlank() }
    val requiresCaptcha = listOf(
        objectValue.boolean("need_captcha"),
        objectValue.boolean("need_recaptcha"),
        objectValue.boolean("recaptcha_required"),
        objectValue.jsonObject("data")?.boolean("need_captcha"),
        objectValue.jsonObject("data")?.boolean("need_recaptcha"),
        objectValue.jsonObject("data")?.boolean("recaptcha_required"),
    ).any { it == true }

    return YummyLoginResult(
        success = success,
        accessToken = token,
        errorMessage = errorMessage,
        requiresCaptcha = requiresCaptcha,
    )
}

private fun String.toLoginDebugSummary(): String {
    return runCatching {
        val root = Json.parseToJsonElement(this)
        val envelope = Json.decodeFromJsonElement<YummyAccountEnvelope<JsonElement>>(root)
        val payload = envelope.response
        val result = payload.toLoginResult()
        val payloadObject = payload as? JsonObject
        val tokenField = payloadObject?.detectTokenFieldName()
        buildString {
            append("payloadType=")
            append(payload::class.simpleName ?: "unknown")
            append(", success=")
            append(result.success)
            append(", tokenPresent=")
            append(result.accessToken.isNotBlank())
            tokenField?.let {
                append(", tokenField=")
                append(it)
            }
            payloadObject?.let {
                append(", keys=")
                append(it.keys.joinToString("|"))
                append(", payloadPreview=")
                append(it.toSanitizedPreview())
            }
            append(", captchaRequired=")
            append(result.requiresCaptcha)
            result.errorMessage?.takeIf(String::isNotBlank)?.let {
                append(", error=\"")
                append(it.take(180).replace('\n', ' '))
                append('"')
            }
        }
    }.getOrElse {
        "bodyPreview=\"${trim().replace(Regex("\\s+"), " ").take(220)}\""
    }
}

private fun JsonObject.detectTokenFieldName(): String? {
    return when {
        string("token") != null -> "token"
        string("access_token") != null -> "access_token"
        string("accessToken") != null -> "accessToken"
        string("auth_token") != null -> "auth_token"
        jsonObject("data")?.string("token") != null -> "data.token"
        jsonObject("data")?.string("access_token") != null -> "data.access_token"
        jsonObject("data")?.string("accessToken") != null -> "data.accessToken"
        jsonObject("data")?.string("auth_token") != null -> "data.auth_token"
        else -> null
    }
}

private fun JsonObject.toSanitizedPreview(): String {
    return entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "$key=${value.toSanitizedPreviewValue()}"
    }.take(320)
}

private fun JsonElement.toSanitizedPreviewValue(): String {
    return when (this) {
        is JsonPrimitive -> {
            val raw = contentOrNull.orEmpty()
            if (raw.isBlank()) "\"\""
            else if (looksSensitive(raw)) "\"<redacted:${raw.length}>\""
            else "\"${raw.take(48).replace('\n', ' ')}\""
        }
        is JsonObject -> keys.joinToString(prefix = "{", postfix = "}") { it }
        is JsonArray -> "[size=$size]"
    }
}

private fun looksSensitive(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.length >= 24 && normalized.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
        return true
    }
    return normalized.contains("eyJ")
}

private fun extractCookieName(headerValue: String): String? {
    return headerValue.substringBefore(';')
        .substringBefore('=')
        .trim()
        .takeIf(String::isNotBlank)
}

private fun List<String>.extractCookieToken(cookieName: String): String? {
    return asReversed()
        .asSequence()
        .mapNotNull { header ->
            val name = extractCookieName(header) ?: return@mapNotNull null
            if (name != cookieName) return@mapNotNull null
            header.substringBefore(';')
                .substringAfter('=', "")
                .trim()
                .takeIf(String::isNotBlank)
        }
        .firstOrNull()
}

private fun JsonObject.string(key: String): String? {
    return (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
}

private fun JsonObject.boolean(key: String): Boolean? {
    return (get(key) as? JsonPrimitive)?.booleanOrNull
}

private fun JsonObject.jsonObject(key: String): JsonObject? {
    return get(key) as? JsonObject
}

private fun JsonElement?.extractReadableMessage(): String? {
    return when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull?.trim()?.takeIf(String::isNotBlank)
        is JsonArray -> asSequence()
            .mapNotNull { it.extractReadableMessage() }
            .firstOrNull()
        is JsonObject -> values.asSequence()
            .mapNotNull { it.extractReadableMessage() }
            .firstOrNull()
    }
}

data class YummyProfile(
    val id: Long,
    val nickname: String,
    val about: String?,
    val banned: Boolean,
    val ids: YummyProfileIds? = null,
    val avatars: YummyProfileAvatars = YummyProfileAvatars(),
    val avatarUrl: String?,
    val birthDate: Long? = null,
    val lastOnline: Long?,
    val sex: YummyProfileSex? = null,
    val roles: List<String> = emptyList(),
    val registerDate: Long?,
    val texts: YummyProfileTexts? = null,
    val banner: YummyProfileBanner? = null,
    val watches: YummyProfileWatches? = null,
    val daysOnline: Int? = null,
    val oldNicknames: List<YummyOldNickname> = emptyList(),
)

data class YummyProfileIds(
    val shikimoriId: Long? = null,
    val shikimoriNickname: String? = null,
    val vkId: Long? = null,
    val telegramNickname: String? = null,
)

data class YummyProfileAvatars(
    val small: String? = null,
    val big: String? = null,
    val full: String? = null,
)

enum class YummyProfileSex(val value: Int) {
    Unknown(0),
    Male(1),
    Female(2);

    companion object {
        fun fromValue(value: Int): YummyProfileSex? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

data class YummyProfileTexts(
    val color: Int? = null,
    val left: String? = null,
    val right: String? = null,
)

data class YummyProfileBanner(
    val cropped: String? = null,
    val full: String? = null,
)

data class YummyProfileWatches(
    val sum: List<YummyProfileWatchSum> = emptyList(),
    val history: List<YummyProfileWatchHistoryItem> = emptyList(),
)

data class YummyProfileWatchSum(
    val shortName: String? = null,
    val name: String? = null,
    val spentTime: Long? = null,
    val value: Int? = null,
    val alias: String? = null,
)

data class YummyProfileWatchHistoryItem(
    val date: Long? = null,
    val episodeCount: Int? = null,
    val duration: Long? = null,
)

data class YummyOldNickname(
    val nickname: String,
    val date: Long? = null,
)

data class YummyAnimeListState(
    val list: YummyUserList?,
    val isFavorite: Boolean,
)

data class YummyUserAnimeListItem(
    val animeId: Long,
    val title: String,
    val list: YummyUserList?,
    val isFavorite: Boolean,
    val addedAt: Long?,
)

enum class YummyUserList(val value: Int) {
    Watching(0),
    Planned(1),
    Completed(2),
    Dropped(3),
    OnHold(5);

    companion object {
        fun fromValue(value: Int): YummyUserList? {
            return entries.firstOrNull { it.value == value }
        }
    }
}
