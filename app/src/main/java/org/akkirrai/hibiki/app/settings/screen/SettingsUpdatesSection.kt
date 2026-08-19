package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable

@Composable
fun AppSettingsUpdatesSection(
    sectionTitle: String,
    checkForUpdatesTitle: String,
    onCheckForUpdates: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, _ ->
            AppSettingsIconActionItem(
                icon = SettingsUpdatesIcon,
                title = checkForUpdatesTitle,
                shape = CircleShape,
                onClick = onCheckForUpdates,
            )
        }
    }
}
