package org.akkirrai.hibiki.app.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

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
    val localizedContext = remember(baseContext, languageMode) {
        baseContext.withLanguage(languageMode)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
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
