package org.akkirrai.hibiki.shared.app.shell.settings

import org.akkirrai.hibiki.shared.settings.DiscordRpcController

internal class HibikiDiscordSettingsActions(
    val onClick: () -> Unit,
    val onChange: (Boolean) -> Unit,
)

internal fun createHibikiDiscordSettingsActions(
    controller: DiscordRpcController?,
    openAuthDialog: () -> Unit,
): HibikiDiscordSettingsActions = HibikiDiscordSettingsActions(
    onClick = { if (controller != null) openAuthDialog() },
    onChange = { enabled ->
        controller?.let {
            if (enabled && !it.hasToken()) openAuthDialog()
            else it.setEnabled(enabled)
        }
    },
)
