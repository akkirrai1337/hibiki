package org.akkirrai.hibiki.shared.settings

import androidx.compose.runtime.Composable

@Composable
fun AppSettingsPreferencesSection(
    sectionTitle: String,
    languageTitle: String,
    languageOptions: List<LanguageMode>,
    selectedLanguage: LanguageMode,
    languageLabel: @Composable (LanguageMode) -> String,
    onLanguageSelected: (LanguageMode) -> Unit,
    notificationsTitle: String,
    notificationsSubtitle: String,
    onNotificationsClick: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 2) { index, shape ->
            when (index) {
                0 -> AppSettingsIconVerticalItem(
                    icon = SettingsLanguageIcon,
                    title = languageTitle,
                    shape = shape,
                ) {
                    AppSettingsSegmentedControl(
                        options = languageOptions,
                        selectedOption = selectedLanguage,
                        label = languageLabel,
                        onSelect = onLanguageSelected,
                    )
                }
                1 -> AppSettingsIconActionItem(
                    icon = SettingsNotificationsIcon,
                    title = notificationsTitle,
                    subtitle = notificationsSubtitle,
                    shape = shape,
                    showChevron = true,
                    onClick = onNotificationsClick,
                )
            }
        }
    }
}
