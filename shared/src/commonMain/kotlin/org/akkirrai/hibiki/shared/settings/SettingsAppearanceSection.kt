package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

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
    switchShape: Shape = CircleShape,
) {
    AppSettingsSection(title = sectionTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(SettingsItemGap)) {
            AppSettingsItems(count = 2) { index, shape ->
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
                }
            }
            AppSettingsIconSwitchItem(
                icon = SettingsAmoledIcon,
                title = amoledTitle,
                checked = useAmoledTheme,
                shape = switchShape,
                onCheckedChange = onAmoledChange,
            )
        }
    }
}
