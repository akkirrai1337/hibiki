package org.akkirrai.hibiki

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.navigation.HibikiApp
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.HibikiSettingsProvider
import org.akkirrai.hibiki.app.settings.LocalThemeMode
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
    private val appPreferences by lazy(LazyThreadSafetyMode.NONE) {
        AppPreferences(this)
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

        enableEdgeToEdge()

        setContent {
            HibikiSettingsProvider(appPreferences = appPreferences) {
                HibikiTheme(
                    themeMode = LocalThemeMode.current
                ) {
                    HibikiApp()
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
        appPreferences.close()
        accountRepository.close()
        super.onDestroy()
    }

    private companion object {
        private const val KEY_NOTIFICATIONS_ASKED = "notifications_permission_asked"
    }
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
