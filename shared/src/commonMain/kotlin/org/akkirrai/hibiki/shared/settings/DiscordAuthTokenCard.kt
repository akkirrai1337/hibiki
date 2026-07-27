package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AppDiscordAuthTokenCard(
    icon: ImageVector,
    manualToken: String,
    onManualTokenChange: (String) -> Unit,
    manualTokenLabel: String,
    invalidTokenLabel: String,
    manualTokenFailed: Boolean,
    isChecking: Boolean,
    isSignedIn: Boolean,
    disconnectLabel: String,
    browserSignInLabel: String,
    onDisconnect: () -> Unit,
    onBrowserSignIn: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = manualToken,
                onValueChange = onManualTokenChange,
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
                shape = RoundedCornerShape(16.dp),
            )
            if (isSignedIn) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isChecking,
                ) {
                    Text(disconnectLabel)
                }
            } else {
                Button(
                    onClick = onBrowserSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isChecking,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = browserSignInLabel,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
