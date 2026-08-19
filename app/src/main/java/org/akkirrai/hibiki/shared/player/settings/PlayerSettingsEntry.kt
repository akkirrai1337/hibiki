package org.akkirrai.hibiki.shared.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.navigation.AppPlayerSettingsDestination as PlayerSettingsDestination

data class PlayerSettingsEntry(
    val id: String,
    val title: String,
    val value: String,
    val onClick: () -> Unit,
)

fun buildPlayerSettingsRootEntries(
    speedValues: List<PlayerSettingsValue>,
    voiceoverValues: List<PlayerSettingsValue>,
    playerValues: List<PlayerSettingsValue>,
    qualityValues: List<PlayerSettingsValue>,
    voiceoverTitle: String,
    qualityTitle: String,
    speedTitle: String,
    autoSkipTitle: String,
    autoSkipValue: String,
    autoPlayTitle: String,
    autoPlayValue: String,
    playerTitle: String,
    onNavigate: (PlayerSettingsDestination) -> Unit,
    onAutoSkipSegmentsChange: (Boolean) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
    autoSkipSegments: Boolean,
    autoPlayNextEpisode: Boolean,
): List<PlayerSettingsEntry> = buildList {
    if (voiceoverValues.size > 1) add(
        PlayerSettingsEntry(
            id = PlayerSettingsDestination.Voiceover.name,
            title = voiceoverTitle,
            value = voiceoverValues.firstSelectedLabelOrDefault(),
            onClick = { onNavigate(PlayerSettingsDestination.Voiceover) },
        )
    )
    if (qualityValues.size > 1) add(
        PlayerSettingsEntry(
            id = PlayerSettingsDestination.Quality.name,
            title = qualityTitle,
            value = qualityValues.firstSelectedLabelOrDefault(),
            onClick = { onNavigate(PlayerSettingsDestination.Quality) },
        )
    )
    add(
        PlayerSettingsEntry(
            id = PlayerSettingsDestination.Speed.name,
            title = speedTitle,
            value = speedValues.firstSelectedLabelOrDefault(defaultLabel = "1x"),
            onClick = { onNavigate(PlayerSettingsDestination.Speed) },
        )
    )
    add(
        PlayerSettingsEntry(
            id = "auto_skip",
            title = autoSkipTitle,
            value = autoSkipValue,
            onClick = { onAutoSkipSegmentsChange(!autoSkipSegments) },
        )
    )
    add(
        PlayerSettingsEntry(
            id = "auto_play_next",
            title = autoPlayTitle,
            value = autoPlayValue,
            onClick = { onAutoPlayNextEpisodeChange(!autoPlayNextEpisode) },
        )
    )
    if (playerValues.isNotEmpty()) add(
        PlayerSettingsEntry(
            id = PlayerSettingsDestination.Player.name,
            title = playerTitle,
            value = playerValues.firstSelectedLabelOrDefault(),
            onClick = { onNavigate(PlayerSettingsDestination.Player) },
        )
    )
}

@Composable
fun AppPlayerSettingsEntry(
    title: String,
    value: String,
    trailingIcon: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
    onClick: () -> Unit,
) {
    PlayerSettingsEntryRow(
        title = title,
        value = value,
        onClick = onClick,
        trailingContent = {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.52f),
            )
        },
    )
}
