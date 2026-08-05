package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable

@Composable
fun AppSettingsPlayerSection(
    sectionTitle: String,
    autoSkipTitle: String,
    autoSkipEnabled: Boolean,
    onAutoSkipChange: (Boolean) -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, _ ->
            AppSettingsIconSwitchItem(
                icon = SettingsAutoSkipIcon,
                title = autoSkipTitle,
                checked = autoSkipEnabled,
                shape = CircleShape,
                onCheckedChange = onAutoSkipChange,
            )
        }
    }
}
