package org.akkirrai.hibiki.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.app.settings.DiscordRpcController

/** Activity-scoped callbacks and availability flags for Settings/Profile that Compose can't reach on its own. */
class AppShellCallbacks(
    val resumeFrameContent: (@Composable (String, Modifier) -> Unit)? = null,
    val discordRpcController: DiscordRpcController? = null,
    val onRequestOnboardingNotificationPermission: () -> Unit = {},
    val onConfigureNotifications: () -> Unit = {},
    val notificationsAvailable: Boolean = true,
    val onCheckForUpdates: () -> Unit = {},
    val onExportLogs: () -> Unit = {},
    val onProfileAvatarEdit: (((String) -> Unit) -> Unit) = {},
    val profileAvatarEditAvailable: Boolean = false,
    val onDiscordBrowserSignIn: (((String) -> Unit) -> Unit) = {},
    // Fired once on cold start, after Home's first real data (not just the loading state) is
    // ready to display -- lets a platform host hold its own launch splash screen up until
    // then instead of revealing an empty Home that fills in a moment later.
    val onFirstContentReady: () -> Unit = {},
)
