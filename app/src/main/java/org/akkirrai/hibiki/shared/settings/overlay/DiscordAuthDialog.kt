package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun AppDiscordAuthDialog(
    initialToken: String,
    isSignedIn: Boolean,
    statusText: String,
    isChecking: Boolean,
    iconContent: @Composable (Modifier) -> Unit,
    title: String,
    manualTokenLabel: String,
    invalidTokenLabel: String,
    disconnectLabel: String,
    browserSignInLabel: String,
    cancelLabel: String,
    applyLabel: String,
    onBrowserSignIn: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onAuthenticate: suspend (String) -> Result<*>,
) {
    val scope = rememberCoroutineScope()
    var manualToken by remember(initialToken) { mutableStateOf(initialToken) }
    var manualTokenFailed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
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
                verticalArrangement = Arrangement.spacedBy(SettingsDiscordDialogContentGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SettingsDiscordHeaderGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(SettingsDiscordHeaderIconSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        iconContent(Modifier.size(SettingsDiscordHeaderIconContentSize))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SettingsDiscordHeaderTextGap),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(SettingsDiscordTokenCardCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(
                        modifier = Modifier.padding(SettingsDiscordTokenCardPadding),
                        verticalArrangement = Arrangement.spacedBy(SettingsDiscordTokenCardContentGap),
                    ) {
                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = {
                                manualToken = it
                                manualTokenFailed = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(manualTokenLabel) },
                            supportingText = if (manualTokenFailed) {
                                { Text(invalidTokenLabel) }
                            } else {
                                null
                            },
                            isError = manualTokenFailed,
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isChecking,
                            shape = RoundedCornerShape(SettingsDiscordTokenFieldCornerRadius),
                        )
                        if (isSignedIn) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SettingsDiscordActionHeight),
                                enabled = !isChecking,
                            ) {
                                Text(disconnectLabel)
                            }
                        } else {
                            Button(
                                onClick = onBrowserSignIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SettingsDiscordActionHeight),
                                enabled = !isChecking,
                            ) {
                                iconContent(Modifier.size(SettingsDiscordBrowserIconSize))
                                Text(
                                    text = browserSignInLabel,
                                    modifier = Modifier.padding(start = SettingsDiscordBrowserLabelStartPadding),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SettingsDiscordActionsGap),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(SettingsDiscordActionHeight),
                        enabled = !isChecking,
                    ) {
                        Text(cancelLabel)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                onAuthenticate(manualToken)
                                    .onSuccess { onDismiss() }
                                    .onFailure { manualTokenFailed = true }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(SettingsDiscordActionHeight),
                        enabled = manualToken.isNotBlank() && !isChecking,
                    ) {
                        Text(applyLabel)
                    }
                }
            }
        }
    }
}
