package org.akkirrai.hibiki.shared.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppSettingsExperimentalSection(
    sectionTitle: String,
    discordIcon: ImageVector,
    discordTitle: String,
    discordEnabled: Boolean,
    onDiscordClick: () -> Unit,
    onDiscordChange: (Boolean) -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, shape ->
            AppSettingsIconToggleItem(
                icon = discordIcon,
                title = discordTitle,
                checked = discordEnabled,
                shape = shape,
                onClick = onDiscordClick,
                onCheckedChange = onDiscordChange,
            )
        }
    }
}
