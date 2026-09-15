package org.akkirrai.hibiki.core.anilist

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.akkirrai.hibiki.core.account.AndroidKeystoreStringStore

internal class AniListTokenStore(context: Context) {
    private val encryptedStore = AndroidKeystoreStringStore(
        context = context,
        prefsName = "anilist_auth",
        keyAlias = "hibiki_anilist_auth_key",
    )

    fun read(): AniListStoredSession? = encryptedStore.get(SESSION_KEY)
        ?.let { encoded -> runCatching { json.decodeFromString<AniListStoredSession>(encoded) }.getOrNull() }

    fun save(session: AniListStoredSession) {
        encryptedStore.save(SESSION_KEY, json.encodeToString(session))
    }

    fun clear() = encryptedStore.clear(SESSION_KEY)

    private companion object {
        const val SESSION_KEY = "session"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class AniListStoredSession(
    val accessToken: String? = null,
    val expiresAtMillis: Long? = null,
    val pendingState: String? = null,
)
