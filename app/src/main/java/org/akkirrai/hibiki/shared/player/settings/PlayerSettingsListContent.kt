package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.navigation.AppPlayerSettingsDestination as PlayerSettingsDestination

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
