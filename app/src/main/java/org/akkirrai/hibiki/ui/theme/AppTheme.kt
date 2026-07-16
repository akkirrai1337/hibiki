package org.akkirrai.hibiki.ui.theme

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.animateColorScheme
import org.akkirrai.hibiki.app.settings.ThemeMode

private val DarkColorScheme = darkColorScheme()

private val LightColorScheme = lightColorScheme()

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

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (amoled) {
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

    MaterialTheme(
        colorScheme = animateColorScheme(
            colorScheme = colorScheme,
            animationSpec = { tween(durationMillis = 500) },
        ),
        typography = Typography,
        content = content
    )
}
