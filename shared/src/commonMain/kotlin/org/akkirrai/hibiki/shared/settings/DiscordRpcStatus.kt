package org.akkirrai.hibiki.shared.settings

fun DiscordRpcConnectionStatus.isBusy(): Boolean = this == DiscordRpcConnectionStatus.Checking ||
    this == DiscordRpcConnectionStatus.Connecting
