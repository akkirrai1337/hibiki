package org.akkirrai.hibiki.core.account

import android.content.Context

class DiscordTokenStore(
    context: Context,
) {
    private val secureStore = AndroidKeystoreStringStore(
        context = context.applicationContext,
        prefsName = PREFS_NAME,
        keyAlias = KEY_ALIAS,
    )

    fun getToken(): String? = secureStore.get(KEY_TOKEN)

    fun saveToken(token: String) {
        val normalized = token.trim()
        require(normalized.isNotBlank()) { "Discord token is blank" }
        secureStore.save(KEY_TOKEN, normalized)
    }

    fun clearToken() {
        secureStore.clear(KEY_TOKEN)
    }

    private companion object {
        const val PREFS_NAME = "hibiki_discord_token"
        const val KEY_ALIAS = "hibiki_discord_token"
        const val KEY_TOKEN = "token"
    }
}
