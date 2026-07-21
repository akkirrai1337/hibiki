package org.akkirrai.hibiki

import android.content.res.Configuration
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.app.DownloadManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.akkirrai.hibiki.app.navigation.HibikiApp
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.HibikiSettingsProvider
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.LocalThemeMode
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import org.akkirrai.hibiki.core.update.AppUpdate
import org.akkirrai.hibiki.core.update.AppUpdateRepository
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.download.OfflineMediaCache
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.feature.update.AppUpdateDialog
import org.akkirrai.hibiki.feature.onboarding.FirstLaunchOnboarding
import org.akkirrai.hibiki.ui.theme.HibikiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val discordRpcManager by lazy(LazyThreadSafetyMode.NONE) {
        DiscordRpcManager.get(applicationContext)
    }
    private val appPreferences by lazy(LazyThreadSafetyMode.NONE) {
        AppPreferences(this)
    }
    private val updateRepository by lazy(LazyThreadSafetyMode.NONE) {
        @Suppress("DEPRECATION")
        AppUpdateRepository(packageManager.getPackageInfo(packageName, 0).versionName.orEmpty())
    }
    private val downloadManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(DOWNLOAD_SERVICE) as DownloadManager
    }
    private val updatePreferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
    }
    private var availableUpdate by mutableStateOf<AppUpdate?>(null)
    private var updateDownloadProgress by mutableStateOf<Float?>(null)
    private var updateDownloadId: Long = NO_DOWNLOAD_ID
    private var updateDownloadJob: Job? = null
    private var isStartingInstaller = false
    private val updateDownloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, NO_DOWNLOAD_ID) != updateDownloadId) return
            availableUpdate?.let(::installDownloadedUpdate)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        appPreferences.setNotificationPermissionState(
            if (granted) NotificationPermissionState.GRANTED else NotificationPermissionState.DENIED,
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appPreferences.setNotificationPermissionState(NotificationPermissionState.GRANTED)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            appPreferences.setNotificationPermissionState(NotificationPermissionState.GRANTED)
            return
        }
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun configureNotifications() {
        val state = AppPreferences.readState(this).notificationPermissionState
        if (state == NotificationPermissionState.NOT_ASKED) {
            requestNotificationPermission()
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun synchronizeNotificationPermissionState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            appPreferences.setNotificationPermissionState(NotificationPermissionState.GRANTED)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val storedState = AppPreferences.readState(this).notificationPermissionState
        when {
            granted -> appPreferences.setNotificationPermissionState(NotificationPermissionState.GRANTED)
            storedState == NotificationPermissionState.GRANTED -> {
                appPreferences.setNotificationPermissionState(NotificationPermissionState.DENIED)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Hibiki)
        super.onCreate(savedInstanceState)
        AppLogger.install(applicationContext)
        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            cleanupInstalledUpdate()
            updateDownloadId = updatePreferences.getLong(KEY_PENDING_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            OfflineMediaCache.migrateLegacyStreamingCacheIfSafe(applicationContext)
        }

        enableEdgeToEdge()
        synchronizeNotificationPermissionState()

        setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides this@MainActivity) {
                HibikiSettingsProvider(appPreferences = appPreferences) {
                    val preferences = LocalAppPreferencesState.current
                    HibikiTheme(
                        themeMode = preferences.themeMode,
                        dynamicColor = preferences.useSystemColorScheme,
                        amoled = preferences.useAmoledTheme,
                    ) {
                        if (preferences.onboardingCompleted) {
                            HibikiApp(
                                onCheckForUpdates = { checkForAppUpdate(showNoUpdateMessage = true) },
                                onConfigureNotifications = ::configureNotifications,
                                onRestartOnboarding = appPreferences::restartOnboarding,
                            )
                        } else {
                            FirstLaunchOnboarding(
                                initialSource = preferences.animeSource
                                    .takeIf { preferences.hasExplicitAnimeSource },
                                notificationPermissionState = preferences.notificationPermissionState,
                                onRequestNotificationPermission = ::requestNotificationPermission,
                                onComplete = appPreferences::completeOnboarding,
                            )
                        }
                        if (preferences.onboardingCompleted && BuildConfig.GITHUB_UPDATES_ENABLED) {
                            availableUpdate?.let { update ->
                                AppUpdateDialog(
                                    update = update,
                                    downloadProgress = updateDownloadProgress,
                                    onUpdate = { downloadUpdate(update) },
                                    onLater = { dismissUpdate(update.version) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            ContextCompat.registerReceiver(
                this,
                updateDownloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            checkForAppUpdate()
        }
    }

    override fun onResume() {
        super.onResume()
        synchronizeNotificationPermissionState()
        requestHighestRefreshRate()
    }

    override fun onStart() {
        super.onStart()
        discordRpcManager.onAppForegrounded()
    }

    override fun onStop() {
        discordRpcManager.setPictureInPictureActive(isInPictureInPictureMode)
        discordRpcManager.onAppBackgrounded()
        super.onStop()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        discordRpcManager.setPictureInPictureActive(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        discordRpcManager.setPictureInPictureActive(false)
        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            updateDownloadJob?.cancel()
            unregisterReceiver(updateDownloadReceiver)
            updateRepository.close()
        }
        appPreferences.close()
        super.onDestroy()
    }

    private fun checkForAppUpdate(showNoUpdateMessage: Boolean = false) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { updateRepository.findAvailableUpdate() }
            }
            result.onSuccess { update ->
                availableUpdate = update
                    ?.takeUnless {
                        !showNoUpdateMessage &&
                            it.version == updatePreferences.getString(KEY_LAST_SHOWN_UPDATE_VERSION, null)
                    }
                    ?.let { candidate ->
                        if (getCompletedDownloadUri(candidate.version) != null) candidate.copy(isDownloaded = true) else candidate
                    }
                availableUpdate?.let { shownUpdate ->
                    updatePreferences.edit()
                        .putString(KEY_LAST_SHOWN_UPDATE_VERSION, shownUpdate.version)
                        .apply()
                }
                if (showNoUpdateMessage && update == null) {
                    Toast.makeText(this@MainActivity, updateString(R.string.update_not_available), Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                if (showNoUpdateMessage) {
                    Toast.makeText(this@MainActivity, updateString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun downloadUpdate(update: AppUpdate) {
        if (update.isDownloaded) {
            installDownloadedUpdate(update)
            return
        }
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("${getString(R.string.app_name)} ${update.version}")
            .setDescription(updateString(R.string.update_downloading, 0))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, update.apkFileName)
        updateDownloadId = downloadManager.enqueue(request)
        updatePreferences.edit()
            .putLong(KEY_PENDING_DOWNLOAD_ID, updateDownloadId)
            .putString(KEY_PENDING_UPDATE_VERSION, update.version)
            .apply()
        updateDownloadProgress = 0f
        observeUpdateDownload(updateDownloadId)
    }

    private fun observeUpdateDownload(downloadId: Long) {
        updateDownloadJob?.cancel()
        updateDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            while (true) {
                val status = downloadManager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) return@use DOWNLOAD_STATUS_MISSING
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    withContext(Dispatchers.Main) {
                        updateDownloadProgress = if (total > 0) downloaded.toFloat() / total else 0f
                    }
                    status
                }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        withContext(Dispatchers.Main) {
                            updateDownloadProgress = null
                            availableUpdate?.let(::installDownloadedUpdate)
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED, DOWNLOAD_STATUS_MISSING -> {
                        withContext(Dispatchers.Main) {
                            clearPendingUpdate()
                            updateDownloadProgress = null
                            Toast.makeText(this@MainActivity, updateString(R.string.update_download_failed), Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }
                delay(250)
            }
        }
    }

    private fun installDownloadedUpdate(update: AppUpdate) {
        if (isStartingInstaller) return
        val updateUri = getCompletedDownloadUri(update.version)
        if (updateUri == null || !isTrustedUpdateApk(update, updateUri)) {
            clearPendingUpdate()
            updateDownloadProgress = null
            Toast.makeText(this, updateString(R.string.update_download_failed), Toast.LENGTH_SHORT).show()
            return
        }
        isStartingInstaller = true
        updateDownloadProgress = null
        availableUpdate = update.copy(isDownloaded = true)
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(updateUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun dismissUpdate(version: String) {
        updatePreferences.edit().putString(KEY_LAST_SHOWN_UPDATE_VERSION, version).apply()
        availableUpdate = null
    }

    private fun getCompletedDownloadUri(version: String): Uri? {
        if (updatePreferences.getString(KEY_PENDING_UPDATE_VERSION, null) != version) return null
        val downloadId = updatePreferences.getLong(KEY_PENDING_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (downloadId == NO_DOWNLOAD_ID) return null
        val isSuccessful = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
        return if (isSuccessful) downloadManager.getUriForDownloadedFile(downloadId) else null
    }

    private fun isTrustedUpdateApk(update: AppUpdate, uri: Uri): Boolean = runCatching {
        val apkFile = File(cacheDir, "update-validation.apk")
        contentResolver.openInputStream(uri)?.use { input ->
            apkFile.outputStream().use(input::copyTo)
        } ?: return false
        val archiveInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, packageInfoFlags()) ?: return false
        val archiveCertificates = signingCertificates(archiveInfo)
        archiveCertificates.isNotEmpty() &&
            archiveInfo.packageName == packageName &&
            archiveInfo.versionName == update.version &&
            archiveCertificates == signingCertificates(installedPackageInfo())
    }.getOrDefault(false)

    private fun installedPackageInfo(): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(packageInfoFlags().toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, packageInfoFlags())
    }

    private fun packageInfoFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    private fun signingCertificates(packageInfo: PackageInfo): Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
    } else {
        @Suppress("DEPRECATION")
        packageInfo.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
    }

    private fun cleanupInstalledUpdate() {
        val pendingVersion = updatePreferences.getString(KEY_PENDING_UPDATE_VERSION, null)
        if (pendingVersion == currentVersionName()) {
            val downloadId = updatePreferences.getLong(KEY_PENDING_DOWNLOAD_ID, NO_DOWNLOAD_ID)
            if (downloadId != NO_DOWNLOAD_ID) downloadManager.remove(downloadId)
            clearPendingUpdate()
        }
    }

    private fun clearPendingUpdate() {
        updatePreferences.edit()
            .remove(KEY_PENDING_DOWNLOAD_ID)
            .remove(KEY_PENDING_UPDATE_VERSION)
            .apply()
        updateDownloadId = NO_DOWNLOAD_ID
        isStartingInstaller = false
    }

    private fun currentVersionName(): String {
        @Suppress("DEPRECATION")
        return packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }

    private fun updateString(@StringRes stringRes: Int, vararg formatArgs: Any): String {
        return applicationContext.withAppPreferencesLanguage().getString(stringRes, *formatArgs)
    }

    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        val activityDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        }
        val currentMode = activityDisplay.mode
        val preferredMode = activityDisplay.supportedModes
            .asSequence()
            .filter { mode ->
                mode.physicalWidth == currentMode.physicalWidth &&
                    mode.physicalHeight == currentMode.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = preferredMode.modeId
            preferredRefreshRate = preferredMode.refreshRate
        }
    }

    private companion object {
        private const val UPDATE_PREFERENCES = "app_update"
        private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
        private const val KEY_PENDING_UPDATE_VERSION = "pending_update_version"
        private const val KEY_LAST_SHOWN_UPDATE_VERSION = "last_shown_update_version"
        private const val NO_DOWNLOAD_ID = -1L
        private const val DOWNLOAD_STATUS_MISSING = -1
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
