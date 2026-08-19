package org.akkirrai.hibiki.app.settings

import kotlinx.coroutines.flow.StateFlow

/** Platform-neutral Discord RPC/auth boundary used by the shared Settings UI. */
interface DiscordRpcController {
    val state: StateFlow<DiscordRpcUiState>

    fun isEnabled(): Boolean

    fun hasToken(): Boolean

    fun tokenForEditing(): String?

    suspend fun authenticate(token: String): Result<Unit>

    fun refreshAuthentication(enableOnSuccess: Boolean)

    fun signOut()

    fun setEnabled(enabled: Boolean)
}

data class DiscordRpcUiState(
    val status: DiscordRpcConnectionStatus = DiscordRpcConnectionStatus.Disabled,
    val accountName: String? = null,
)
