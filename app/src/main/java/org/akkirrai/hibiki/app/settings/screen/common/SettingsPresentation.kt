package org.akkirrai.hibiki.app.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Update
import androidx.compose.ui.graphics.vector.ImageVector

val SettingsNotificationsIcon: ImageVector = Icons.Outlined.Notifications
val SettingsExternalSourcesIcon: ImageVector = Icons.Outlined.CloudDownload
val SettingsAutoSkipIcon: ImageVector = Icons.Outlined.SkipNext
val SettingsUpdatesIcon: ImageVector = Icons.Outlined.Update
val SettingsExportLogsIcon: ImageVector = Icons.Outlined.Share
val SettingsThemeIcon: ImageVector = Icons.Outlined.DarkMode
val SettingsSystemColorSchemeIcon: ImageVector = Icons.Outlined.Palette
val SettingsAmoledIcon: ImageVector = Icons.Outlined.Contrast
val SettingsLanguageIcon: ImageVector = Icons.Outlined.Language

val themeModeOptions: List<ThemeMode> = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)
val languageModeOptions: List<LanguageMode> = listOf(LanguageMode.RUSSIAN, LanguageMode.ENGLISH, LanguageMode.SYSTEM)

fun resolveLanguageModeLabel(
    mode: LanguageMode,
    systemLabel: String,
    russianLabel: String,
    englishLabel: String,
): String = when (mode) {
    LanguageMode.SYSTEM -> systemLabel
    LanguageMode.RUSSIAN -> russianLabel
    LanguageMode.ENGLISH -> englishLabel
}

fun resolveThemeModeLabel(
    mode: ThemeMode,
    systemLabel: String,
    lightLabel: String,
    darkLabel: String,
): String = when (mode) {
    ThemeMode.SYSTEM -> systemLabel
    ThemeMode.LIGHT -> lightLabel
    ThemeMode.DARK -> darkLabel
}

fun resolveNotificationPermissionLabel(
    state: NotificationPermissionState,
    notAskedLabel: String,
    grantedLabel: String,
    deniedLabel: String,
): String = when (state) {
    NotificationPermissionState.NOT_ASKED -> notAskedLabel
    NotificationPermissionState.GRANTED -> grantedLabel
    NotificationPermissionState.DENIED -> deniedLabel
}
