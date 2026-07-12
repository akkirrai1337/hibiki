package org.akkirrai.hibiki

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.DownloadManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.navigation.HibikiApp
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.HibikiSettingsProvider
import org.akkirrai.hibiki.app.settings.LocalThemeMode
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.core.update.AppUpdate
import org.akkirrai.hibiki.core.update.AppUpdateRepository
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.download.OfflineMediaCache
import org.akkirrai.hibiki.feature.update.AppUpdateDialog
import org.akkirrai.hibiki.ui.theme.HibikiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val accountRepository by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.hibikiDependencies().accountRepository()
    }
    private val appPreferences by lazy(LazyThreadSafetyMode.NONE) {
        AppPreferences(this)
    }
    private var profileWarmupJob: Job? = null
    private val updateRepository by lazy(LazyThreadSafetyMode.NONE) {
        @Suppress("DEPRECATION")
        AppUpdateRepository(packageManager.getPackageInfo(packageName, 0).versionName.orEmpty())
    }
    private val downloadManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(DOWNLOAD_SERVICE) as DownloadManager
    }
    private var availableUpdate by mutableStateOf<AppUpdate?>(null)
    private var updateDownloadProgress by mutableStateOf<Float?>(null)
    private var updateDownloadId: Long = NO_DOWNLOAD_ID
    private var updateDownloadJob: Job? = null
    private val updateDownloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, NO_DOWNLOAD_ID) != updateDownloadId) return
            installDownloadedUpdate()
        }
    }

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

        lifecycleScope.launch(Dispatchers.IO) {
            OfflineMediaCache.migrateLegacyStreamingCacheIfSafe(applicationContext)
        }

        enableEdgeToEdge()

        setContent {
            HibikiSettingsProvider(appPreferences = appPreferences) {
                HibikiTheme(
                    themeMode = LocalThemeMode.current
                ) {
                    HibikiApp()
                    availableUpdate?.let { update ->
                        AppUpdateDialog(
                            update = update,
                            downloadProgress = updateDownloadProgress,
                            onUpdate = { downloadUpdate(update) },
                            onLater = { availableUpdate = null },
                        )
                    }
                }
            }
        }

        requestNotificationPermissionOnFirstLaunch()
        ContextCompat.registerReceiver(
            this,
            updateDownloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        checkForAppUpdate()
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
        updateDownloadJob?.cancel()
        unregisterReceiver(updateDownloadReceiver)
        updateRepository.close()
        appPreferences.close()
        accountRepository.close()
        super.onDestroy()
    }

    private fun checkForAppUpdate() {
        lifecycleScope.launch {
            availableUpdate = withContext(Dispatchers.IO) {
                runCatching { updateRepository.findAvailableUpdate() }.getOrNull()
            }
        }
    }

    private fun downloadUpdate(update: AppUpdate) {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("${getString(R.string.app_name)} ${update.version}")
            .setDescription(getString(R.string.update_downloading, 0))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, update.apkFileName)
        updateDownloadId = downloadManager.enqueue(request)
        updateDownloadProgress = 0f
        observeUpdateDownload(updateDownloadId)
    }

    private fun observeUpdateDownload(downloadId: Long) {
        updateDownloadJob?.cancel()
        updateDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            while (true) {
                val isFinished = downloadManager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) return@use true
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    withContext(Dispatchers.Main) {
                        updateDownloadProgress = if (total > 0) downloaded.toFloat() / total else 0f
                    }
                    status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED
                }
                if (isFinished) return@launch
                delay(250)
            }
        }
    }

    private fun installDownloadedUpdate() {
        val updateUri = downloadManager.getUriForDownloadedFile(updateDownloadId)
        if (updateUri == null) {
            updateDownloadProgress = null
            Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_SHORT).show()
            return
        }
        updateDownloadProgress = null
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(updateUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private companion object {
        private const val KEY_NOTIFICATIONS_ASKED = "notifications_permission_asked"
        private const val NO_DOWNLOAD_ID = -1L
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
