package org.akkirrai.hibiki.app.settings

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
