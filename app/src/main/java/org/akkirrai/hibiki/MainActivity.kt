package org.akkirrai.hibiki

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import org.akkirrai.hibiki.app.navigation.HibikiApp
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.ui.theme.HibikiTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val accountRepository by lazy(LazyThreadSafetyMode.NONE) {
        YummyAccountRepository(applicationContext)
    }
    private var profileWarmupJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Hibiki)
        super.onCreate(savedInstanceState)

        val appPreferences = AppPreferences(this)

        enableEdgeToEdge()

        setContent {
            val preferences by appPreferences.state.collectAsState()

            LocalizedAppContext(languageMode = preferences.languageMode) {
                HibikiTheme(
                    themeMode = preferences.themeMode
                ) {
                    HibikiApp(
                        appPreferences = appPreferences
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        profileWarmupJob?.cancel()
        profileWarmupJob = lifecycleScope.launch {
            accountRepository.warmProfileCacheIfLoggedIn()
        }
    }

    override fun onDestroy() {
        profileWarmupJob?.cancel()
        accountRepository.close()
        super.onDestroy()
    }
}

@Composable
private fun LocalizedAppContext(
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

private fun Context.withLanguage(languageMode: LanguageMode): Context {
    val languageTag = languageMode.tag ?: return this
    val locale = Locale.forLanguageTag(languageTag)

    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLocales(LocaleList(locale))

    return createConfigurationContext(configuration)
}

@Preview
@Composable
fun GreetingPreview() {
    HibikiTheme(
        themeMode = ThemeMode.SYSTEM
    ) {
        HibikiApp()
    }
}
