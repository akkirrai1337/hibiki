package org.akkirrai.hibiki.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppMotion {
    const val ScreenTransitionDurationMillis = 200
}

/** Default non-platform-specific palettes used when a host has no dynamic colors. */
val HibikiDarkColorScheme: ColorScheme = darkColorScheme()
val HibikiLightColorScheme: ColorScheme = lightColorScheme()

/** Platform-neutral Material typography used by every Hibiki UI host. */
val HibikiTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)
