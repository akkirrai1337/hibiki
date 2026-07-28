package org.akkirrai.hibiki.shared.settings

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
