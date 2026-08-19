package org.akkirrai.hibiki.app.settings

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
    showExternalSources: Boolean = false,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = (if (notificationsAvailable) 2 else 1) + if (showExternalSources) 1 else 0) { index, shape ->
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
                showExternalSources -> AppSettingsIconActionItem(
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
