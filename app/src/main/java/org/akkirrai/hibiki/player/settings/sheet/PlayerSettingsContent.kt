package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import org.akkirrai.hibiki.design.component.navigation.AppBackButton
import org.akkirrai.hibiki.app.navigation.AppPlayerSettingsDestination as PlayerSettingsDestination
import org.akkirrai.hibiki.player.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

@Composable
fun AppPlayerSettingsContent(
    destination: PlayerSettingsDestination,
    selectedSpeed: Float,
    selectedSourceId: String,
    selectedPlayerName: String?,
    selectedQualityLabel: String?,
    availableQualityLabels: List<String>,
    autoSkipSegments: Boolean,
    autoPlayNextEpisode: Boolean,
    options: PlaybackSettingsOptions,
    onNavigate: (PlayerSettingsDestination) -> Unit,
    onBack: () -> Unit,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectVoiceover: (WatchSource) -> Unit,
    onSelectPlayer: (String?) -> Unit,
    onSelectQuality: (String?) -> Unit,
    onAutoSkipSegmentsChange: (Boolean) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
) {
    val choices = buildPlayerSettingsChoices(
        selectedSpeed = selectedSpeed,
        selectedSourceId = selectedSourceId,
        selectedPlayerName = selectedPlayerName,
        selectedQualityLabel = selectedQualityLabel,
        availableQualityLabels = availableQualityLabels,
        options = options,
        onSelectSpeed = onSelectSpeed,
        onSelectVoiceover = onSelectVoiceover,
        onSelectPlayer = onSelectPlayer,
        onSelectQuality = onSelectQuality,
    )
    val rootEntries = buildPlayerSettingsRootEntries(
        speedValues = choices.speed,
        voiceoverValues = choices.voiceover,
        playerValues = choices.player,
        qualityValues = choices.quality,
        autoSkipSegments = autoSkipSegments,
        autoPlayNextEpisode = autoPlayNextEpisode,
        onNavigate = onNavigate,
        onAutoSkipSegmentsChange = onAutoSkipSegmentsChange,
        onAutoPlayNextEpisodeChange = onAutoPlayNextEpisodeChange,
        voiceoverTitle = appText(AppTextKey.PlayerSettingsVoiceover),
        qualityTitle = appText(AppTextKey.PlayerSettingsQuality),
        speedTitle = appText(AppTextKey.PlayerSettingsSpeed),
        autoSkipTitle = appText(AppTextKey.PlayerSettingsAutoSkip),
        autoSkipValue = appText(playerToggleValueLocalizationKey(autoSkipSegments).toPlayerSettingsTextKey()),
        autoPlayTitle = appText(AppTextKey.PlayerSettingsAutoPlayNext),
        autoPlayValue = appText(playerToggleValueLocalizationKey(autoPlayNextEpisode).toPlayerSettingsTextKey()),
        playerTitle = appText(AppTextKey.PlayerSettingsPlayer),
    )

    backHandler(destination != PlayerSettingsDestination.Root, onBack)

    AppPlayerSettingsSheet(
        destination = destination,
        title = { target -> appText(target.textKey) },
        onBack = onBack,
        backContent = {
            AppBackButton(onClick = onBack, contentDescription = appText(AppTextKey.Back))
        },
        content = { target ->
            appPlayerSettingsItems(
                destination = target,
                rootEntries = rootEntries,
                speedValues = choices.speed,
                voiceoverValues = choices.voiceover,
                playerValues = choices.player,
                qualityValues = choices.quality,
                entryContent = { entry ->
                    AppPlayerSettingsEntry(
                        title = entry.title,
                        value = entry.value,
                        onClick = entry.onClick,
                    )
                },
                choiceContent = { value ->
                    AppPlayerSettingsChoice(
                        label = value.label,
                        description = value.description,
                        selected = value.selected,
                        onClick = value.onClick,
                    )
                },
            )
        },
    )
}

private val PlayerSettingsDestination.textKey: AppTextKey
    get() = when (localizationKey()) {
        "watch_player_settings_root" -> AppTextKey.PlayerSettingsRoot
        "watch_player_settings_speed" -> AppTextKey.PlayerSettingsSpeed
        "watch_player_settings_voiceover" -> AppTextKey.PlayerSettingsVoiceover
        "watch_player_settings_player" -> AppTextKey.PlayerSettingsPlayer
        "watch_player_settings_quality" -> AppTextKey.PlayerSettingsQuality
        else -> error("Unknown player settings localization key")
    }

private fun String.toPlayerSettingsTextKey(): AppTextKey = when (this) {
    "watch_player_settings_on" -> AppTextKey.PlayerSettingsOn
    "watch_player_settings_off" -> AppTextKey.PlayerSettingsOff
    else -> error("Unknown player settings value localization key")
}

fun LazyListScope.appPlayerSettingsItems(
    destination: PlayerSettingsDestination,
    rootEntries: List<PlayerSettingsEntry>,
    speedValues: List<PlayerSettingsValue>,
    voiceoverValues: List<PlayerSettingsValue>,
    playerValues: List<PlayerSettingsValue>,
    qualityValues: List<PlayerSettingsValue>,
    entryContent: @Composable (PlayerSettingsEntry) -> Unit,
    choiceContent: @Composable (PlayerSettingsValue) -> Unit,
) {
    when (destination) {
        PlayerSettingsDestination.Root -> items(rootEntries, key = PlayerSettingsEntry::id) { entry ->
            entryContent(entry)
        }
        PlayerSettingsDestination.Speed -> appPlayerSettingsChoices(speedValues, choiceContent)
        PlayerSettingsDestination.Voiceover -> appPlayerSettingsChoices(voiceoverValues, choiceContent)
        PlayerSettingsDestination.Player -> appPlayerSettingsChoices(playerValues, choiceContent)
        PlayerSettingsDestination.Quality -> appPlayerSettingsChoices(qualityValues, choiceContent)
    }
}

private fun LazyListScope.appPlayerSettingsChoices(
    values: List<PlayerSettingsValue>,
    choiceContent: @Composable (PlayerSettingsValue) -> Unit,
) {
    items(values, key = PlayerSettingsValue::id) { value ->
        choiceContent(value)
    }
}

fun PlayerSettingsDestination.localizationKey(): String = when (this) {
    PlayerSettingsDestination.Root -> "watch_player_settings_root"
    PlayerSettingsDestination.Speed -> "watch_player_settings_speed"
    PlayerSettingsDestination.Voiceover -> "watch_player_settings_voiceover"
    PlayerSettingsDestination.Player -> "watch_player_settings_player"
    PlayerSettingsDestination.Quality -> "watch_player_settings_quality"
}
