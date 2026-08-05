package org.akkirrai.hibiki.shared.app.shell.runtime

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.shared.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.settings.ThemeMode

@Composable
internal fun hibikiAppColorScheme(
    themeMode: ThemeMode,
    useAmoledTheme: Boolean,
): ColorScheme {
    val effectiveDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val baseColorScheme = if (effectiveDarkTheme) HibikiDarkColorScheme else HibikiLightColorScheme
    return if (useAmoledTheme && effectiveDarkTheme) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        baseColorScheme
    }
}
