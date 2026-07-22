package org.akkirrai.hibiki.shared.player

private fun String?.orEmptyValue(): String = this.orEmpty()

fun PlayerUiState.settingsOptionsKey(): String = buildString {
    append(currentSourceId)
    append(':')
    append(currentEpisodeId)
    append(':')
    append(selectedPlayerName.orEmptyValue())
    append(':')
    append(selectedQualityLabel.orEmptyValue())
}
