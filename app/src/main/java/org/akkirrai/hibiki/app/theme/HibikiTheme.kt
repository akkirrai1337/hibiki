package org.akkirrai.hibiki.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.design.HibikiLightColorScheme
import org.akkirrai.hibiki.design.HibikiTypography

@Composable
fun HibikiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val baseColorScheme = remember(dynamicColor, darkTheme, context) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> HibikiDarkColorScheme
            else -> HibikiLightColorScheme
        }
    }
    val colorScheme = remember(baseColorScheme, amoled, darkTheme) {
        if (amoled) {
            val amoledColor = if (darkTheme) Color.Black else Color.White
            val onAmoledColor = if (darkTheme) Color.White else Color.Black
            baseColorScheme.copy(
                background = amoledColor,
                onBackground = onAmoledColor,
                surface = amoledColor,
                onSurface = onAmoledColor,
            )
        } else {
            baseColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HibikiTypography,
        content = content
    )
}
