package org.akkirrai.hibiki

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.navigation.HibikiApp
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LanguageMode
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.ui.theme.HibikiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val accountRepository by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.hibikiDependencies().accountRepository()
    }
    private var profileWarmupJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _: Boolean -> }

    private fun requestNotificationPermissionOnFirstLaunch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getPreferences(Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Hibiki)
        super.onCreate(savedInstanceState)
        AppLogger.install(applicationContext)

        val appPreferences = AppPreferences(this)

        enableEdgeToEdge()

        setContent {
            val preferences by appPreferences.state.collectAsState()

            LocalizedAppContext(languageMode = preferences.languageMode) {
                HibikiTheme(
                    themeMode = preferences.themeMode
                ) {
                    HibikiApp(
                        appPreferences = appPreferences,
                    )
                }
            }
        }

        requestNotificationPermissionOnFirstLaunch()
    }

    override fun onStart() {
        super.onStart()
        profileWarmupJob?.cancel()
        profileWarmupJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                accountRepository.warmProfileCacheIfLoggedIn()
            }
        }
    }

    override fun onDestroy() {
        profileWarmupJob?.cancel()
        accountRepository.close()
        super.onDestroy()
    }

    private companion object {
        private const val KEY_NOTIFICATIONS_ASKED = "notifications_permission_asked"
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
