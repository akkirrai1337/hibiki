package org.akkirrai.hibiki.shared.settings

import androidx.compose.runtime.Composable

@Composable
fun AppSettingsSupportSection(
    sectionTitle: String,
    exportLogsTitle: String,
    onExportLogs: () -> Unit,
    installExtensionTitle: String,
    onInstallExtensionClick: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 2) { index, shape ->
            when (index) {
                0 -> AppSettingsIconActionItem(
                    icon = SettingsExportLogsIcon,
                    title = exportLogsTitle,
                    shape = shape,
                    onClick = onExportLogs,
                )
                else -> AppSettingsIconActionItem(
                    icon = SettingsInstallExtensionIcon,
                    title = installExtensionTitle,
                    shape = shape,
                    showChevron = true,
                    onClick = onInstallExtensionClick,
                )
            }
        }
    }
}
