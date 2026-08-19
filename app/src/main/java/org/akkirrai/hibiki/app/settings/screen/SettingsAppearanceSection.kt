package org.akkirrai.hibiki.app.settings

import androidx.compose.runtime.Composable

@Composable
fun AppSettingsAppearanceSection(
    sectionTitle: String,
    themeTitle: String,
    themeOptions: List<ThemeMode>,
    selectedTheme: ThemeMode,
    themeLabel: @Composable (ThemeMode) -> String,
    onThemeSelected: (ThemeMode) -> Unit,
    systemColorSchemeTitle: String,
    useSystemColorScheme: Boolean,
    onSystemColorSchemeChange: (Boolean) -> Unit,
    amoledTitle: String,
    useAmoledTheme: Boolean,
    onAmoledChange: (Boolean) -> Unit,
) {
    AppSettingsSection(title = sectionTitle) {
        AppSettingsItems(count = 3) { index, shape ->
            when (index) {
                0 -> AppSettingsIconVerticalItem(
                    icon = SettingsThemeIcon,
                    title = themeTitle,
                    shape = shape,
                ) {
                    AppSettingsSegmentedControl(
                        options = themeOptions,
                        selectedOption = selectedTheme,
                        label = themeLabel,
                        onSelect = onThemeSelected,
                    )
                }
                1 -> AppSettingsIconSwitchItem(
                    icon = SettingsSystemColorSchemeIcon,
                    title = systemColorSchemeTitle,
                    checked = useSystemColorScheme,
                    shape = shape,
                    onCheckedChange = onSystemColorSchemeChange,
                )
                2 -> AppSettingsIconSwitchItem(
                    icon = SettingsAmoledIcon,
                    title = amoledTitle,
                    checked = useAmoledTheme,
                    shape = shape,
                    onCheckedChange = onAmoledChange,
                )
            }
        }
    }
}
