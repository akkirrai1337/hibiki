package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AppDiscordAuthDialogSurface(
    onDismissRequest: () -> Unit,
    headerContent: @Composable () -> Unit,
    tokenContent: @Composable () -> Unit,
    actionsContent: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDiscordDialogHorizontalPadding),
            shape = RoundedCornerShape(SettingsDiscordDialogCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = SettingsDiscordDialogElevation,
        ) {
            Column(
                modifier = Modifier.padding(SettingsDiscordDialogPadding),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SettingsDiscordDialogContentGap),
            ) {
                headerContent()
                tokenContent()
                actionsContent()
            }
        }
    }
}
