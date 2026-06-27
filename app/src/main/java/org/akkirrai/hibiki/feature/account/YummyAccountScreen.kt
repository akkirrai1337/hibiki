package org.akkirrai.hibiki.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.account.YummyAccountSessionState
import org.akkirrai.hibiki.core.design.UiDimens

@Composable
fun YummyAccountScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember(context) { YummyAccountRepository(context) }
    var state by remember { mutableStateOf<YummyAccountScreenState>(YummyAccountScreenState.Checking) }
    var busy by remember { mutableStateOf(false) }
    var apiKeyEnabled by remember { mutableStateOf(repository.isApplicationTokenEnabled()) }
    var apiKeyAvailable by remember { mutableStateOf(!repository.getApplicationToken().isNullOrBlank()) }
    var apiKeyHelpVisible by remember { mutableStateOf(false) }
    val loginErrorMessage = stringResource(R.string.yummy_account_login_error)

    LaunchedEffect(Unit) {
        state = when (val result = repository.validateSession()) {
            YummyAccountSessionState.LoggedOut -> YummyAccountScreenState.SignedOut
            is YummyAccountSessionState.LoggedIn -> YummyAccountScreenState.SignedIn(result.profile)
            is YummyAccountSessionState.Invalid -> YummyAccountScreenState.SignedOut
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UiDimens.ScreenPadding, vertical = UiDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Hibiki",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.yummy_account_auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                YummyAccountScreenState.Checking -> CheckingContent(
                    modifier = Modifier.widthIn(max = 360.dp),
                )
                YummyAccountScreenState.SignedOut -> AccountForm(
                    busy = busy,
                    errorMessage = null,
                    modifier = Modifier.widthIn(max = 360.dp),
                    onSubmit = { login, secret ->
                        busy = true
                        coroutineScope.launch {
                            state = runCatching {
                                repository.signIn(login = login, secret = secret)
                            }.fold(
                                onSuccess = { profile ->
                                    apiKeyAvailable = !repository.getApplicationToken().isNullOrBlank()
                                    YummyAccountScreenState.SignedIn(profile)
                                },
                                onFailure = { throwable ->
                                    YummyAccountScreenState.Error(
                                        throwable.message ?: loginErrorMessage,
                                    )
                                },
                            )
                            busy = false
                        }
                    },
                )
                is YummyAccountScreenState.Error -> AccountForm(
                    busy = busy,
                    errorMessage = current.message,
                    modifier = Modifier.widthIn(max = 360.dp),
                    onSubmit = { login, secret ->
                        busy = true
                        coroutineScope.launch {
                            state = runCatching {
                                repository.signIn(login = login, secret = secret)
                            }.fold(
                                onSuccess = { profile ->
                                    apiKeyAvailable = !repository.getApplicationToken().isNullOrBlank()
                                    YummyAccountScreenState.SignedIn(profile)
                                },
                                onFailure = { throwable ->
                                    YummyAccountScreenState.Error(
                                        throwable.message ?: loginErrorMessage,
                                    )
                                },
                            )
                            busy = false
                        }
                    },
                )
                is YummyAccountScreenState.SignedIn -> SignedInContent(
                    profile = current.profile,
                    busy = busy,
                    modifier = Modifier.widthIn(max = 360.dp),
                    onExit = {
                        busy = true
                        coroutineScope.launch {
                            repository.signOut()
                            state = YummyAccountScreenState.SignedOut
                            busy = false
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(0.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AdvancedApiKeyCard(
                enabled = apiKeyEnabled,
                available = apiKeyAvailable,
                modifier = Modifier.widthIn(max = 360.dp),
                onEnabledChange = { enabled ->
                    apiKeyEnabled = enabled
                    repository.setApplicationTokenEnabled(enabled)
                },
                onHelpClick = { apiKeyHelpVisible = !apiKeyHelpVisible },
            )
        }

        if (apiKeyHelpVisible) {
            AlertDialog(
                onDismissRequest = { apiKeyHelpVisible = false },
                confirmButton = {
                    TextButton(onClick = { apiKeyHelpVisible = false }) {
                        Text(text = stringResource(R.string.action_ok))
                    }
                },
                title = {
                    Text(text = stringResource(R.string.yummy_account_api_key_title))
                },
                text = {
                    Text(
                        text = stringResource(R.string.yummy_account_api_key_help_text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@Composable
private fun CheckingContent(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.yummy_account_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountForm(
    busy: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onSubmit: (String, String) -> Unit,
) {
    var loginValue by remember { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val loginRequiredMessage = stringResource(R.string.yummy_account_login_required)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            localError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = loginValue,
                onValueChange = {
                    loginValue = it
                    localError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.yummy_account_login_label),
                        fontSize = 17.sp,
                    )
                },
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.56f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.42f),
                    focusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                    disabledIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                ),
            )
            OutlinedTextField(
                value = secretValue,
                onValueChange = {
                    secretValue = it
                    localError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.yummy_account_password_label),
                        fontSize = 17.sp,
                    )
                },
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.56f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.42f),
                    focusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                    disabledIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                ),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Spacer(modifier = Modifier.size(5.dp))
                Text(
                    text = stringResource(R.string.yummy_account_password_note),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun SignedInContent(
    profile: YummyProfile,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = profile.nickname.ifBlank { stringResource(R.string.yummy_account_profile_fallback) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.yummy_account_connected_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(
                onClick = onExit,
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
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
    }
}

@Composable
private fun AdvancedApiKeyCard(
    enabled: Boolean,
    available: Boolean,
    modifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit,
    onHelpClick: () -> Unit,
) {
    val statusHint = if (available) {
        stringResource(R.string.yummy_account_api_key_enabled_hint)
    } else {
        stringResource(R.string.yummy_account_api_key_disabled_hint)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.16f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.yummy_account_api_key_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = onHelpClick, modifier = Modifier.size(22.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = stringResource(R.string.yummy_account_api_key_help_cd),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = true,
                    modifier = Modifier.scale(0.84f),
                )
            }
        }
    }
}

private sealed interface YummyAccountScreenState {
    data object Checking : YummyAccountScreenState
    data object SignedOut : YummyAccountScreenState
    data class SignedIn(val profile: YummyProfile) : YummyAccountScreenState
    data class Error(val message: String) : YummyAccountScreenState
}
