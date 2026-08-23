package org.akkirrai.hibiki.app.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("LocalAppPreferences not provided")
}

val LocalAppPreferencesState = staticCompositionLocalOf {
    AppPreferencesState()
}

val LocalAppLanguage = staticCompositionLocalOf {
    LanguageMode.SYSTEM
}

val LocalThemeMode = staticCompositionLocalOf {
    ThemeMode.SYSTEM
}

@Composable
fun HibikiSettingsProvider(
    appPreferences: AppPreferences,
    content: @Composable () -> Unit
) {
    val preferences by appPreferences.state.collectAsState()

    LocalizedAppContext(languageMode = preferences.languageMode) {
        CompositionLocalProvider(
            LocalAppPreferences provides appPreferences,
            LocalAppPreferencesState provides preferences,
            LocalAppLanguage provides preferences.languageMode,
            LocalThemeMode provides preferences.themeMode,
            content = content
        )
    }
}

@Composable
fun LocalizedAppContext(
    languageMode: LanguageMode,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    // createConfigurationContext() rebuilds a Resources/AssetManager snapshot and isn't
    // guaranteed cheap; building it inline in `remember` would block this composition's
    // frame right before the full-tree recompose it triggers below. Build it off the main
    // thread instead and swap in once ready, so the UI stays responsive meanwhile.
    var localizedContext by remember(baseContext) {
        mutableStateOf(baseContext.withLanguage(languageMode))
    }
    LaunchedEffect(baseContext, languageMode) {
        localizedContext = withContext(Dispatchers.Default) { baseContext.withLanguage(languageMode) }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content
    )
}

fun Context.withAppPreferencesLanguage(): Context {
    val languageMode = AppPreferences.readState(applicationContext).languageMode
    return withLanguage(languageMode)
}

fun Context.withLanguage(languageMode: LanguageMode): Context {
    val languageTag = languageMode.tag ?: return this
    val locale = Locale.forLanguageTag(languageTag)

    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLocales(LocaleList(locale))

    return createConfigurationContext(configuration)
}

fun resolveAppLanguageTag(languageMode: LanguageMode, systemLanguage: String): String = when (languageMode) {
    LanguageMode.ENGLISH -> "en"
    LanguageMode.RUSSIAN -> "ru"
    LanguageMode.SYSTEM -> if (systemLanguage.lowercase().startsWith("ru")) "ru" else "en"
}

fun isEnglishAppLanguage(languageMode: LanguageMode, systemLanguage: String): Boolean =
    resolveAppLanguageTag(languageMode, systemLanguage) == "en"
