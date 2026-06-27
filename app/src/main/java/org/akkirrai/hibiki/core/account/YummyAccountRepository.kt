package org.akkirrai.hibiki.core.account

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.animeresolver.metadata.YummyAccountApi
import org.akkirrai.animeresolver.metadata.YummyAnimeListState
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserList
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory

class YummyAccountRepository(
    context: Context,
    private val tokenStore: YummyAccountTokenStore = AndroidKeystoreYummyAccountTokenStore(context),
    private val applicationTokenStore: YummyApplicationTokenStore = AndroidKeystoreYummyApplicationTokenStore(context),
    private val client: HttpClient = AndroidHttpClientFactory.create(),
    applicationTokenProvider: (() -> String?)? = null,
) {
    private val stringContext = context
    private val api = YummyAccountApi(
        client = client,
        applicationToken = applicationTokenProvider?.invoke() ?: applicationTokenStore.getEffectiveApplicationToken(),
        accessTokenProvider = tokenStore::getAccessToken,
        debugLogger = { message -> AppLogger.d(LOG_TAG, message) },
    )

    fun isLoggedIn(): Boolean = tokenStore.hasAccessToken()

    fun getStoredToken(): String? = tokenStore.getAccessToken()

    fun isApplicationTokenEnabled(): Boolean = applicationTokenStore.isApplicationTokenEnabled()

    fun getApplicationToken(): String? = applicationTokenStore.getApplicationToken()

    fun saveApplicationToken(token: String) {
        applicationTokenStore.saveApplicationToken(token)
    }

    fun setApplicationTokenEnabled(enabled: Boolean) {
        applicationTokenStore.setApplicationTokenEnabled(enabled)
    }

    fun clearApplicationToken() {
        applicationTokenStore.clearApplicationToken()
    }

    suspend fun signIn(
        login: String,
        secret: String,
        recaptchaResponse: String? = null,
    ): YummyProfile {
        val result = api.login(
            login = login,
            password = secret,
            recaptchaResponse = recaptchaResponse,
        )
        if (!result.success || result.accessToken.isBlank()) {
            val message = when {
                result.requiresCaptcha -> stringContext.getString(R.string.yummy_account_error_captcha_required)
                !result.errorMessage.isNullOrBlank() -> result.errorMessage
                else -> stringContext.getString(R.string.yummy_account_error_missing_access_token)
            }
            throw IllegalStateException(message)
        }
        tokenStore.saveAccessToken(result.accessToken)
        saveDiscoveredApplicationToken()
        return api.getProfile()
    }

    private fun saveDiscoveredApplicationToken() {
        if (!applicationTokenStore.getApplicationToken().isNullOrBlank()) {
            return
        }

        applicationTokenStore.saveApplicationToken(DEFAULT_APPLICATION_TOKEN)
    }

    suspend fun getProfile(): YummyProfile {
        return api.getProfile()
    }

    suspend fun refreshSession(): String {
        val refreshedToken = api.refreshToken()
        tokenStore.saveAccessToken(refreshedToken)
        return refreshedToken
    }

    suspend fun signOut() {
        runCatching { api.logout() }
        tokenStore.clearAccessToken()
    }

    fun clearLocalSession() {
        tokenStore.clearAccessToken()
    }

    suspend fun validateSession(): YummyAccountSessionState {
        if (!tokenStore.hasAccessToken()) {
            return YummyAccountSessionState.LoggedOut
        }

        return runCatching { api.getProfile() }
            .fold(
                onSuccess = { profile -> YummyAccountSessionState.LoggedIn(profile) },
                onFailure = { firstError ->
                    val refreshed = runCatching { refreshSession() }.getOrNull()
                    if (refreshed.isNullOrBlank()) {
                        YummyAccountSessionState.Invalid(firstError)
                    } else {
                        runCatching { api.getProfile() }
                            .fold(
                                onSuccess = { profile -> YummyAccountSessionState.LoggedIn(profile) },
                                onFailure = { secondError -> YummyAccountSessionState.Invalid(secondError) },
                            )
                    }
                },
            )
    }

    suspend fun getAnimeListState(animeId: Long): YummyAnimeListState {
        return api.getAnimeListState(animeId = animeId)
    }

    suspend fun setAnimeListStatus(animeId: Long, list: YummyUserList) {
        api.addAnimeToList(animeId = animeId, list = list)
    }

    suspend fun removeAnimeListStatus(animeId: Long) {
        api.removeAnimeFromList(animeId = animeId)
    }

    suspend fun addAnimeToFavorites(animeId: Long) {
        api.addAnimeToFavorites(animeId = animeId)
    }

    suspend fun removeAnimeFromFavorites(animeId: Long) {
        api.removeAnimeFromFavorites(animeId = animeId)
    }

    suspend fun getUserLists(userId: Long): List<YummyUserAnimeListItem> {
        return api.getUserLists(userId = userId)
    }

    suspend fun setOnline() {
        api.setUserOnline()
    }

    fun close() {
        client.close()
    }

    private companion object {
        const val LOG_TAG = "YummyAccount"
        // Yummy web client sends this X-Application value with API requests.
        const val DEFAULT_APPLICATION_TOKEN = "wawegr8j13it4rdw"
    }
}

sealed interface YummyAccountSessionState {
    data object LoggedOut : YummyAccountSessionState
    data class LoggedIn(val profile: YummyProfile) : YummyAccountSessionState
    data class Invalid(val cause: Throwable) : YummyAccountSessionState
}
