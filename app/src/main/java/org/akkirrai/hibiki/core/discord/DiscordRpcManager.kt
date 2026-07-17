package org.akkirrai.hibiki.core.discord

import android.content.Context
import android.os.SystemClock
import com.my.kizzyrpc.KizzyRPC
import com.my.kizzyrpc.entities.presence.Activity
import com.my.kizzyrpc.entities.presence.Assets
import com.my.kizzyrpc.entities.presence.Metadata
import com.my.kizzyrpc.entities.presence.Timestamps
import com.my.kizzyrpc.logger.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import org.akkirrai.hibiki.core.account.DiscordTokenStore
import org.akkirrai.hibiki.core.log.AppLogger

enum class DiscordRpcConnectionStatus {
    Disabled,
    SignedOut,
    Checking,
    Connecting,
    Connected,
    Error,
}

data class DiscordRpcState(
    val status: DiscordRpcConnectionStatus = DiscordRpcConnectionStatus.Disabled,
    val account: DiscordAccount? = null,
)

data class DiscordPlaybackPresence(
    val titleId: String,
    val animeTitle: String,
    val voiceover: String,
    val episodeNumber: Double?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val coverUrl: String? = null,
)

class DiscordRpcManager private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)
    private val tokenStore = DiscordTokenStore(appContext)
    private val repository = DiscordRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val publishRequests = Channel<Unit>(capacity = Channel.CONFLATED)
    private val publishImmediately = AtomicBoolean(false)
    private val mediaProxyCache = ConcurrentHashMap<String, String>()
    private val _state = MutableStateFlow(initialState())

    val state: StateFlow<DiscordRpcState> = _state.asStateFlow()

    @Volatile
    private var desiredPresence: DesiredPresence? = null

    @Volatile
    private var isAppForeground = true

    @Volatile
    private var pictureInPictureActive = false

    @Volatile
    private var backgroundAudioActive = false

    private val backgroundPlaybackActive: Boolean
        get() = pictureInPictureActive || backgroundAudioActive

    private var rpc: KizzyRPC? = null
    private var lastPublishAtMs = 0L
    private var lastPublishedFingerprint: PublishFingerprint? = null
    private var backgroundDisconnectJob: Job? = null
    private var reconnectJob: Job? = null

    init {
        scope.launch {
            preferences.state.collect { state ->
                if (!state.discordRpcEnabled) {
                    closeRpc(keepDesiredPresence = true)
                    _state.value = DiscordRpcState(
                        status = if (tokenStore.getToken() == null) {
                            DiscordRpcConnectionStatus.SignedOut
                        } else {
                            DiscordRpcConnectionStatus.Disabled
                        },
                        account = _state.value.account,
                    )
                } else if (tokenStore.getToken() == null) {
                    closeRpc(keepDesiredPresence = true)
                    _state.value = DiscordRpcState(DiscordRpcConnectionStatus.SignedOut)
                } else if (desiredPresence is DesiredPresence.Playback &&
                    (desiredPresence as DesiredPresence.Playback).value.titleId in state.discordRpcExcludedTitleIds
                ) {
                    closeRpc(keepDesiredPresence = true)
                } else {
                    requestPublish()
                }
            }
        }
        scope.launch {
            for (ignored in publishRequests) {
                publishLatestPresence()
            }
        }
        if (tokenStore.getToken() != null) {
            refreshAuthentication(enableOnSuccess = false)
        }
    }

    fun hasToken(): Boolean = tokenStore.getToken() != null

    suspend fun authenticate(token: String): Result<DiscordAccount> = runCatching {
        _state.value = DiscordRpcState(DiscordRpcConnectionStatus.Checking)
        val account = repository.getAccount(token.trim())
        tokenStore.saveToken(token)
        preferences.setDiscordRpcEnabled(true)
        ensureDesiredPresence()
        _state.value = DiscordRpcState(DiscordRpcConnectionStatus.Connecting, account)
        closeRpc(keepDesiredPresence = true)
        requestPublish()
        account
    }.onFailure {
        _state.value = DiscordRpcState(DiscordRpcConnectionStatus.Error)
    }

    fun refreshAuthentication(enableOnSuccess: Boolean) {
        val token = tokenStore.getToken() ?: run {
            _state.value = DiscordRpcState(DiscordRpcConnectionStatus.SignedOut)
            return
        }
        scope.launch {
            _state.value = DiscordRpcState(DiscordRpcConnectionStatus.Checking)
            runCatching { repository.getAccount(token) }
                .onSuccess { account ->
                    if (enableOnSuccess) preferences.setDiscordRpcEnabled(true)
                    ensureDesiredPresence()
                    _state.value = DiscordRpcState(
                        status = if (preferences.state.value.discordRpcEnabled) {
                            DiscordRpcConnectionStatus.Connecting
                        } else {
                            DiscordRpcConnectionStatus.Disabled
                        },
                        account = account,
                    )
                    requestPublish()
                }
                .onFailure { throwable ->
                    if (throwable is DiscordAuthenticationException) {
                        tokenStore.clearToken()
                        preferences.setDiscordRpcEnabled(false)
                        _state.value = DiscordRpcState(DiscordRpcConnectionStatus.SignedOut)
                    } else {
                        _state.value = DiscordRpcState(DiscordRpcConnectionStatus.Error)
                    }
                }
        }
    }

    fun signOut() {
        tokenStore.clearToken()
        preferences.setDiscordRpcEnabled(false)
        closeRpc(keepDesiredPresence = true)
        _state.value = DiscordRpcState(DiscordRpcConnectionStatus.SignedOut)
    }

    fun showGeneralStatus(route: String?) {
        val localizedContext = appContext.withAppPreferencesLanguage()
        val details = when (route?.substringBefore('/')) {
            "catalog", "search", "trending", "recent_updates", "details" ->
                localizedContext.getString(R.string.discord_rpc_browsing_catalog)
            "library" -> localizedContext.getString(R.string.discord_rpc_browsing_library)
            "profile" -> localizedContext.getString(R.string.discord_rpc_browsing_profile)
            "settings" -> localizedContext.getString(R.string.discord_rpc_browsing_settings)
            "watch_sources", "episodes" -> localizedContext.getString(R.string.discord_rpc_selecting_episode)
            "player" -> return
            else -> localizedContext.getString(R.string.discord_rpc_browsing_home)
        }
        desiredPresence = DesiredPresence.General(details)
        requestPublish()
    }

    fun showPlayback(value: DiscordPlaybackPresence) {
        val playbackStateChanged = (desiredPresence as? DesiredPresence.Playback)
            ?.value
            ?.isPlaying != value.isPlaying
        desiredPresence = DesiredPresence.Playback(value)
        if (value.titleId in preferences.state.value.discordRpcExcludedTitleIds) {
            closeRpc(keepDesiredPresence = true)
        } else {
            requestPublish(immediately = playbackStateChanged)
        }
    }

    fun onAppForegrounded() {
        isAppForeground = true
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null
        requestPublish()
    }

    fun onAppBackgrounded() {
        isAppForeground = false
        scheduleBackgroundDisconnectIfNeeded()
    }

    fun setPictureInPictureActive(active: Boolean) {
        pictureInPictureActive = active
        updateBackgroundPlaybackState()
    }

    fun setBackgroundAudioActive(active: Boolean) {
        backgroundAudioActive = active
        updateBackgroundPlaybackState()
    }

    private fun updateBackgroundPlaybackState() {
        if (backgroundPlaybackActive) {
            backgroundDisconnectJob?.cancel()
            backgroundDisconnectJob = null
        } else if (!isAppForeground) {
            scheduleBackgroundDisconnectIfNeeded()
        }
    }

    private fun scheduleBackgroundDisconnectIfNeeded() {
        if (backgroundPlaybackActive) return
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = scope.launch {
            delay(BACKGROUND_DISCONNECT_DELAY_MS)
            if (!isAppForeground && !backgroundPlaybackActive) {
                closeRpc(keepDesiredPresence = true)
            }
        }
    }

    private fun requestPublish(immediately: Boolean = false) {
        if (immediately) publishImmediately.set(true)
        publishRequests.trySend(Unit)
    }

    private suspend fun publishLatestPresence() {
        ensureDesiredPresence()
        val initialPresence = desiredPresence ?: return
        if (!canPublish(initialPresence)) return
        if (publishFingerprint(initialPresence) == lastPublishedFingerprint) {
            publishImmediately.set(false)
            return
        }

        while (!publishImmediately.getAndSet(false)) {
            val debounceMs = lastPublishAtMs + MIN_PUBLISH_INTERVAL_MS - SystemClock.elapsedRealtime()
            if (debounceMs <= 0) break
            delay(minOf(debounceMs, IMMEDIATE_PUBLISH_POLL_MS))
        }
        val rateLimitMs = lastPublishAtMs + ABSOLUTE_MIN_PUBLISH_INTERVAL_MS -
            SystemClock.elapsedRealtime()
        if (rateLimitMs > 0) delay(rateLimitMs)

        val presence = desiredPresence ?: return
        val token = tokenStore.getToken() ?: return
        if (!canPublish(presence)) return
        val fingerprint = publishFingerprint(presence)
        if (fingerprint == lastPublishedFingerprint) return
        _state.value = _state.value.copy(status = DiscordRpcConnectionStatus.Connecting)
        val activity = buildActivity(presence, token)
        if (!canPublish(presence) || tokenStore.getToken() != token) return
        val client = rpc ?: KizzyRPC(token, DiscordGatewayLogger).also { rpc = it }
        AppLogger.d(DISCORD_LOG_TAG, "Discord RPC Gateway update started")
        updateRpcWithTimeout(client, activity).onSuccess {
            lastPublishAtMs = SystemClock.elapsedRealtime()
            lastPublishedFingerprint = fingerprint
            reconnectJob?.cancel()
            reconnectJob = null
            AppLogger.d(DISCORD_LOG_TAG, "Discord RPC Gateway update completed")
            if (canPublish(presence)) {
                _state.value = _state.value.copy(status = DiscordRpcConnectionStatus.Connected)
            } else {
                closeRpc(keepDesiredPresence = true)
            }
        }.onFailure { throwable ->
            AppLogger.e(DISCORD_LOG_TAG, "Discord RPC update failed (${throwable.javaClass.simpleName})")
            closeRpc(keepDesiredPresence = true)
            if (preferences.state.value.discordRpcEnabled) {
                _state.value = _state.value.copy(status = DiscordRpcConnectionStatus.Error)
                scheduleReconnect()
            }
        }
    }

    private fun ensureDesiredPresence() {
        if (desiredPresence != null) return
        val localizedContext = appContext.withAppPreferencesLanguage()
        desiredPresence = DesiredPresence.General(
            localizedContext.getString(R.string.discord_rpc_browsing_home),
        )
    }

    private fun publishFingerprint(presence: DesiredPresence): PublishFingerprint =
        PublishFingerprint(
            presence = presence,
            language = preferences.state.value.languageMode.name,
        )

    private suspend fun updateRpcWithTimeout(
        client: KizzyRPC,
        activity: Activity,
    ): Result<Unit> {
        val update = scope.async(Dispatchers.IO) {
            runCatching {
                client.updateRPC(
                    activity = activity,
                    status = STATUS_ONLINE,
                    since = activity.timestamps?.start ?: System.currentTimeMillis(),
                )
            }
        }
        val result = withTimeoutOrNull(RPC_CONNECT_TIMEOUT_MS) { update.await() }
        if (result != null) return result

        update.cancel()
        return Result.failure(DiscordRpcTimeoutException())
    }

    private fun canPublish(presence: DesiredPresence): Boolean {
        val state = preferences.state.value
        if (!state.discordRpcEnabled || tokenStore.getToken() == null) return false
        if (!isAppForeground && !backgroundPlaybackActive) return false
        return presence !is DesiredPresence.Playback ||
            presence.value.titleId !in state.discordRpcExcludedTitleIds
    }

    private suspend fun buildActivity(
        presence: DesiredPresence,
        token: String,
    ): Activity {
        val localizedContext = appContext.withAppPreferencesLanguage()
        val appName = localizedContext.getString(R.string.app_name)
        val appIcon = mediaProxyUrl(token, HIBIKI_ICON_URL)
        val githubButton = localizedContext.getString(R.string.discord_rpc_open_github)
            .takeIf { it.isValidDiscordButtonLabel() }
        val githubButtons = githubButton?.let(::listOf)
        val githubMetadata = githubButton?.let { Metadata(listOf(HIBIKI_GITHUB_URL)) }
        val activity = when (presence) {
            is DesiredPresence.General -> Activity(
                applicationId = DISCORD_APPLICATION_ID,
                name = appName,
                details = presence.details.discordText() ?: appName,
                state = null,
                type = ACTIVITY_TYPE_WATCHING,
                assets = appIcon?.let {
                    Assets(
                        largeImage = it,
                        largeText = appName,
                        smallImage = null,
                        smallText = null,
                    )
                },
                buttons = githubButtons,
                metadata = githubMetadata,
            )
            is DesiredPresence.Playback -> {
                val value = presence.value
                val timestamps = if (value.isPlaying) {
                    val startTime = System.currentTimeMillis() - value.positionMs.coerceAtLeast(0L)
                    val endTime = value.durationMs
                        .takeIf { it > value.positionMs }
                        ?.let { startTime + it }
                    Timestamps(start = startTime, end = endTime)
                } else {
                    null
                }
                val progress = formatProgress(value.positionMs, value.durationMs)
                val episode = value.episodeNumber?.let(::formatEpisodeNumber)
                val largeImage = value.coverUrl?.let { mediaProxyUrl(token, it) } ?: appIcon
                Activity(
                    applicationId = DISCORD_APPLICATION_ID,
                    name = appName,
                    details = value.animeTitle.discordText() ?: appName,
                    state = value.voiceover.discordText(),
                    type = ACTIVITY_TYPE_WATCHING,
                    timestamps = timestamps,
                    assets = largeImage?.let {
                        Assets(
                            largeImage = it,
                            largeText = listOfNotNull(
                                episode?.let { number ->
                                    localizedContext.getString(R.string.discord_rpc_episode, number)
                                },
                                progress,
                            ).joinToString(SEPARATOR).discordText(),
                            smallImage = appIcon,
                            smallText = appName,
                        )
                    },
                    buttons = githubButtons,
                    metadata = githubMetadata,
                )
            }
        }
        AppLogger.d(
            DISCORD_LOG_TAG,
            "Discord RPC activity built: type=${activity.type}, " +
                "applicationId=${activity.applicationId != null}, assets=${activity.assets != null}, " +
                "buttons=${activity.buttons?.isNotEmpty() == true}",
        )
        return activity
    }

    private suspend fun mediaProxyUrl(token: String, url: String): String? {
        mediaProxyCache[url]?.let { return it }
        return withTimeoutOrNull(MEDIA_PROXY_TIMEOUT_MS) {
            runCatching {
                repository.getMediaProxyUrl(DISCORD_APPLICATION_ID, token, url)
            }.getOrNull()
        }?.also { mediaProxyCache[url] = it }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (preferences.state.value.discordRpcEnabled &&
                (isAppForeground || backgroundPlaybackActive)
            ) {
                requestPublish()
            }
        }
    }

    @Synchronized
    private fun closeRpc(keepDesiredPresence: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = null
        runCatching { rpc?.closeRPC() }
        rpc = null
        lastPublishAtMs = 0L
        lastPublishedFingerprint = null
        if (!keepDesiredPresence) desiredPresence = null
    }

    private fun initialState(): DiscordRpcState {
        val hasToken = tokenStore.getToken() != null
        return DiscordRpcState(
            status = when {
                !hasToken -> DiscordRpcConnectionStatus.SignedOut
                !preferences.state.value.discordRpcEnabled -> DiscordRpcConnectionStatus.Disabled
                else -> DiscordRpcConnectionStatus.Checking
            },
        )
    }

    private sealed interface DesiredPresence {
        data class General(val details: String) : DesiredPresence
        data class Playback(val value: DiscordPlaybackPresence) : DesiredPresence
    }

    private data class PublishFingerprint(
        val presence: DesiredPresence,
        val language: String,
    )

    companion object {
        @Volatile
        private var instance: DiscordRpcManager? = null

        fun get(context: Context): DiscordRpcManager = instance ?: synchronized(this) {
            instance ?: DiscordRpcManager(context).also { instance = it }
        }

        private const val DISCORD_LOG_TAG = "HibikiDiscordRpc"
        private const val DISCORD_APPLICATION_ID = "1527613923338096764"
        private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"
        private const val HIBIKI_ICON_URL =
            "https://raw.githubusercontent.com/akkirrai1337/hibiki/refs/heads/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp"
        private const val STATUS_ONLINE = "online"
        private const val ACTIVITY_TYPE_WATCHING = 3
        private const val MIN_PUBLISH_INTERVAL_MS = 16_000L
        private const val ABSOLUTE_MIN_PUBLISH_INTERVAL_MS = 2_000L
        private const val IMMEDIATE_PUBLISH_POLL_MS = 100L
        private const val RPC_CONNECT_TIMEOUT_MS = 20_000L
        private const val MEDIA_PROXY_TIMEOUT_MS = 5_000L
        private const val RECONNECT_DELAY_MS = 15_000L
        private const val BACKGROUND_DISCONNECT_DELAY_MS = 30_000L
        private const val SEPARATOR = " • "
        private const val MAX_DISCORD_TEXT_LENGTH = 128
        private const val MAX_DISCORD_BUTTON_BYTES = 32

        private fun formatProgress(positionMs: Long, durationMs: Long): String? {
            if (durationMs <= 0L) return null
            return "${formatTime(positionMs)}/${formatTime(durationMs)}"
        }

        private fun formatTime(timeMs: Long): String {
            val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

        private fun formatEpisodeNumber(number: Double): String =
            if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

        private fun String.discordText(): String? = trim()
            .take(MAX_DISCORD_TEXT_LENGTH)
            .takeIf { it.length >= 2 }

        private fun String.isValidDiscordButtonLabel(): Boolean =
            isNotBlank() && toByteArray(Charsets.UTF_8).size <= MAX_DISCORD_BUTTON_BYTES
    }
}

private class DiscordRpcTimeoutException : Exception("Discord RPC connection timed out")

private object DiscordGatewayLogger : Logger {
    override fun clear() = Unit

    override fun i(tag: String, event: String) {
        AppLogger.i("HibikiDiscordRpc", "KizzyRPC [$tag] $event")
    }

    override fun e(tag: String, event: String) {
        AppLogger.e("HibikiDiscordRpc", "KizzyRPC [$tag] $event")
    }

    override fun d(tag: String, event: String) {
        AppLogger.d("HibikiDiscordRpc", "KizzyRPC [$tag] $event")
    }

    override fun w(tag: String, event: String) {
        AppLogger.w("HibikiDiscordRpc", "KizzyRPC [$tag] $event")
    }
}
