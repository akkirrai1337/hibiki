package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.design.component.AppBackButton
import org.akkirrai.hibiki.shared.model.PlaybackSettingsOptions
import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

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
