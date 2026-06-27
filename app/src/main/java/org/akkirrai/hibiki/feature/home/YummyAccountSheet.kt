package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YummyAccountSheet(
    state: YummyAccountUiState,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onExit: () -> Unit,
) {
    var loginValue by remember { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val loginRequiredMessage = stringResource(R.string.yummy_account_login_required)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiDimens.ScreenPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            Text(
                text = "YummyAnime",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            when (state) {
                YummyAccountUiState.Checking -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.yummy_account_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is YummyAccountUiState.SignedIn -> {
                    YummySignedInContent(
                        profile = state.profile,
                        busy = busy,
                        onExit = onExit,
                    )
                }
                YummyAccountUiState.SignedOut,
                is YummyAccountUiState.Error -> {
                    if (state is YummyAccountUiState.Error) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    localError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = loginValue,
                        onValueChange = {
                            loginValue = it
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.yummy_account_login_label)) },
                        enabled = !busy,
                    )
                    OutlinedTextField(
                        value = secretValue,
                        onValueChange = {
                            secretValue = it
                            localError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.yummy_account_password_label)) },
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = {
                            val normalizedLogin = loginValue.trim()
                            if (normalizedLogin.isBlank() || secretValue.isBlank()) {
                                localError = loginRequiredMessage
                            } else {
                                onSubmit(normalizedLogin, secretValue)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(stringResource(R.string.action_sign_in))
                    }
                    Text(
                        text = stringResource(R.string.yummy_account_password_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun YummySignedInContent(
    profile: YummyProfile,
    busy: Boolean,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        text = profile.nickname.ifBlank { stringResource(R.string.yummy_account_profile_fallback) },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = stringResource(R.string.yummy_account_profile_id, profile.id),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.yummy_account_connected_message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = onExit,
        enabled = !busy,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(stringResource(R.string.action_sign_out))
    }
}

sealed interface YummyAccountUiState {
    data object Checking : YummyAccountUiState
    data object SignedOut : YummyAccountUiState
    data class SignedIn(val profile: YummyProfile) : YummyAccountUiState
    data class Error(val message: String) : YummyAccountUiState
}
