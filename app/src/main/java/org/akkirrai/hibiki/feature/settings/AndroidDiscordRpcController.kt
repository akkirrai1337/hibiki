package org.akkirrai.hibiki.feature.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.discord.DiscordRpcManager
import org.akkirrai.hibiki.shared.settings.DiscordRpcConnectionStatus
import org.akkirrai.hibiki.shared.settings.DiscordRpcController
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState

/** Android adapter preserving the existing Discord RPC manager and auth lifecycle. */
internal class AndroidDiscordRpcController(
    context: Context,
) : DiscordRpcController {
    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)
    private val manager = DiscordRpcManager.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val state: StateFlow<DiscordRpcUiState> = manager.state
        .map { discordState ->
            DiscordRpcUiState(
                status = discordState.status,
                accountName = discordState.account?.displayName,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, DiscordRpcUiState(DiscordRpcConnectionStatus.Disabled))

    override fun isEnabled(): Boolean = preferences.state.value.discordRpcEnabled

    override fun hasToken(): Boolean = manager.hasToken()

    override fun tokenForEditing(): String? = manager.tokenForEditing()

    override suspend fun authenticate(token: String): Result<Unit> =
        manager.authenticate(token).map { Unit }

    override fun refreshAuthentication(enableOnSuccess: Boolean) {
        manager.refreshAuthentication(enableOnSuccess)
    }

    override fun signOut() {
        manager.signOut()
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (manager.hasToken()) {
                manager.refreshAuthentication(enableOnSuccess = true)
            }
        } else {
            preferences.setDiscordRpcEnabled(false)
        }
    }
}
