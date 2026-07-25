package org.akkirrai.hibiki.shared.player

data class PlayerSettingsValue(
    val id: String,
    val label: String,
    val description: String? = null,
    val selected: Boolean,
    val onClick: () -> Unit,
)

fun List<PlayerSettingsValue>.firstSelectedLabelOrDefault(defaultLabel: String = first().label): String =
    firstOrNull { it.selected }?.label ?: defaultLabel
