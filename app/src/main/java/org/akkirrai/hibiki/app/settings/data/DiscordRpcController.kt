package org.akkirrai.hibiki.app.settings

import kotlinx.coroutines.flow.StateFlow

enum class DiscordRpcConnectionStatus {
    Disabled,
    SignedOut,
    Checking,
    Connecting,
    Connected,
    Error,
}

fun DiscordRpcConnectionStatus.isBusy(): Boolean = this == DiscordRpcConnectionStatus.Checking ||
    this == DiscordRpcConnectionStatus.Connecting

fun resolveDiscordRpcStatusLabel(
    status: DiscordRpcConnectionStatus,
    disabledLabel: String,
    signedOutLabel: String,
    checkingLabel: String,
    connectingLabel: String,
    connectedLabel: String,
    errorLabel: String,
): String = when (status) {
    DiscordRpcConnectionStatus.Disabled -> disabledLabel
    DiscordRpcConnectionStatus.SignedOut -> signedOutLabel
    DiscordRpcConnectionStatus.Checking -> checkingLabel
    DiscordRpcConnectionStatus.Connecting -> connectingLabel
    DiscordRpcConnectionStatus.Connected -> connectedLabel
    DiscordRpcConnectionStatus.Error -> errorLabel
}

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
