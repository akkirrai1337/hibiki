package org.akkirrai.hibiki.shared.player

data class PlayerSettingsEntry(
    val id: String,
    val title: String,
    val value: String,
    val onClick: () -> Unit,
)
