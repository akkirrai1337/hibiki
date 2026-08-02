package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.WatchSource

data class PlayerSettingsChoices(
    val speed: List<PlayerSettingsValue>,
    val voiceover: List<PlayerSettingsValue>,
    val player: List<PlayerSettingsValue>,
    val quality: List<PlayerSettingsValue>,
)

fun buildPlayerSettingsChoices(
    selectedSpeed: Float,
    selectedSourceId: String,
    selectedPlayerName: String?,
    selectedQualityLabel: String?,
    availableQualityLabels: List<String>,
    options: PlaybackSettingsOptions,
    onSelectSpeed: (Float) -> Unit,
    onSelectVoiceover: (WatchSource) -> Unit,
    onSelectPlayer: (String?) -> Unit,
    onSelectQuality: (String?) -> Unit,
): PlayerSettingsChoices = PlayerSettingsChoices(
    speed = playbackSpeedOptions.map { speed ->
        PlayerSettingsValue(
            id = speed.toString(),
            label = formatPlaybackSpeed(speed),
            selected = selectedSpeed == speed,
            onClick = { onSelectSpeed(speed) },
        )
    },
    voiceover = options.voiceovers.map { source ->
        PlayerSettingsValue(
            id = source.sourceId,
            label = source.title.ifBlank { source.sourceId },
            description = source.qualityLabel,
            selected = selectedSourceId == source.sourceId,
            onClick = { onSelectVoiceover(source) },
        )
    },
    player = uniquePlayerNames(options.links).map { name ->
        PlayerSettingsValue(
            id = name,
            label = name,
            selected = selectedPlayerName == name ||
                (selectedPlayerName == null && options.links.firstOrNull()?.playerName == name),
            onClick = { onSelectPlayer(name) },
        )
    },
    quality = sortQualityLabels(options.links.mapNotNull { it.qualityLabel } + availableQualityLabels)
        .map { quality ->
            PlayerSettingsValue(
                id = quality,
                label = quality,
                selected = selectedQualityLabel == quality,
                onClick = { onSelectQuality(quality) },
            )
        },
)
