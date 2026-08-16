package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable

@Composable
fun AppSettingsSupportSection(
    sectionTitle: String,
    exportLogsTitle: String,
    onExportLogs: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 1) { _, _ ->
            AppSettingsIconActionItem(
                icon = SettingsExportLogsIcon,
                title = exportLogsTitle,
                shape = CircleShape,
                onClick = onExportLogs,
            )
        }
    }
}
