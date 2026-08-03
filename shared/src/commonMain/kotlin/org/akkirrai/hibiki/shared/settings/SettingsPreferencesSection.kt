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
    notificationsAvailable: Boolean = true,
    onNotificationsClick: () -> Unit,
    externalSourcesTitle: String,
    externalSourcesSubtitle: String,
    externalSourcesCount: Int,
    onExternalSourcesClick: () -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = if (notificationsAvailable) 3 else 2) { index, shape ->
            when {
                index == 0 -> AppSettingsIconVerticalItem(
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
                notificationsAvailable && index == 1 -> AppSettingsIconActionItem(
                    icon = SettingsNotificationsIcon,
                    title = notificationsTitle,
                    subtitle = notificationsSubtitle,
                    shape = shape,
                    showChevron = true,
                    onClick = onNotificationsClick,
                )
                else -> AppSettingsIconActionItem(
                    icon = SettingsExternalSourcesIcon,
                    title = externalSourcesTitle,
                    subtitle = externalSourcesSubtitle.replace("%s", externalSourcesCount.toString()),
                    shape = shape,
                    showChevron = true,
                    onClick = onExternalSourcesClick,
                )
            }
        }
    }
}
