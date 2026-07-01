package org.akkirrai.hibiki.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserListWatchStat
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.account.YummyAccountSessionState
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader
import org.akkirrai.hibiki.core.design.component.AppFloatingIconButton
import org.akkirrai.hibiki.core.design.component.YummySignInForm

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
    var page by remember { mutableStateOf(AccountPage.Profile) }
    val notificationCount = 0
    val loginErrorMessage = stringResource(R.string.yummy_account_login_error)
    val submitCredentials: (String, String) -> Unit = { login, secret ->
        busy = true
        coroutineScope.launch {
            state = runCatching {
                val profile = repository.signIn(login = login, secret = secret)
                loadSignedInState(repository, profile)
            }.fold(
                onSuccess = {
                    apiKeyAvailable = !repository.getApplicationToken().isNullOrBlank()
                    it
                },
                onFailure = { throwable ->
                    YummyAccountScreenState.Error(
                        throwable.message ?: loginErrorMessage,
                    )
                },
            )
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        state = when (val result = repository.validateSession()) {
            YummyAccountSessionState.LoggedOut -> YummyAccountScreenState.SignedOut
            is YummyAccountSessionState.LoggedIn -> loadSignedInState(repository, result.profile)
            is YummyAccountSessionState.Invalid -> YummyAccountScreenState.SignedOut
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.navigationBars,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val current = state) {
                YummyAccountScreenState.Checking -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckingContent()
                }

                YummyAccountScreenState.SignedOut -> SignedOutScreen(
                    busy = busy,
                    errorMessage = null,
                    paddingValues = PaddingValues(top = 78.dp),
                    onSubmit = submitCredentials,
                )

                is YummyAccountScreenState.Error -> SignedOutScreen(
                    busy = busy,
                    errorMessage = current.message,
                    paddingValues = PaddingValues(top = 78.dp),
                    onSubmit = submitCredentials,
                )

                is YummyAccountScreenState.SignedIn -> SignedInScreen(
                    page = page,
                    profile = current.profile,
                    libraryItems = current.libraryItems,
                    listWatchStats = current.listWatchStats,
                    busy = busy,
                    apiKeyEnabled = apiKeyEnabled,
                    apiKeyAvailable = apiKeyAvailable,
                    paddingValues = PaddingValues(top = 78.dp),
                    onApiKeyEnabledChange = { enabled ->
                        apiKeyEnabled = enabled
                        repository.setApplicationTokenEnabled(enabled)
                    },
                    onApiKeyHelpClick = { apiKeyHelpVisible = true },
                    onExit = {
                        busy = true
                        coroutineScope.launch {
                            repository.signOut()
                            page = AccountPage.Profile
                            state = YummyAccountScreenState.SignedOut
                            busy = false
                        }
                    },
                )
            }

            AppFloatingHeader(
                title = when (page) {
                    AccountPage.Profile -> stringResource(R.string.yummy_account_title)
                    AccountPage.Settings -> stringResource(R.string.yummy_account_settings_title)
                },
                onBackClick = {
                    if (page == AccountPage.Settings) {
                        page = AccountPage.Profile
                    } else {
                        onBackClick()
                    }
                },
                actions = if (state is YummyAccountScreenState.SignedIn && page == AccountPage.Profile) {
                    {
                        NotificationActionIcon(
                            notificationCount = notificationCount,
                            onClick = {},
                        )
                        AppFloatingIconButton(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.yummy_account_settings_title),
                            onClick = { page = AccountPage.Settings },
                        )
                    }
                } else {
                    null
                },
            )
        }
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

@Composable
private fun NotificationActionIcon(
    notificationCount: Int,
    onClick: () -> Unit,
) {
    Box {
        AppFloatingIconButton(
            imageVector = Icons.Outlined.NotificationsNone,
            contentDescription = stringResource(R.string.yummy_account_notifications_cd),
            onClick = onClick,
        )
        if (notificationCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(AccountWarmAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = notificationCount.coerceAtMost(9).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private suspend fun loadSignedInState(
    repository: YummyAccountRepository,
    profile: YummyProfile,
): YummyAccountScreenState.SignedIn {
    val (detailedProfile, libraryItems, listWatchStats) = coroutineScope {
        val detailedProfile = async {
            runCatching { repository.getUserProfile(profile.id) }
                .getOrDefault(profile)
        }
        val libraryItems = async {
            runCatching { repository.getUserLists(profile.id) }
                .getOrDefault(emptyList())
        }
        val listWatchStats = async {
            runCatching { repository.getUserStatsLists(profile.id) }
                .getOrDefault(emptyList())
        }

        Triple(detailedProfile.await(), libraryItems.await(), listWatchStats.await())
    }
    return YummyAccountScreenState.SignedIn(detailedProfile, libraryItems, listWatchStats)
}

@Composable
private fun SignedOutScreen(
    busy: Boolean,
    errorMessage: String?,
    paddingValues: PaddingValues,
    onSubmit: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.yummy_account_auth_subtitle),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.yummy_account_sign_in_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        YummySignInForm(
            busy = busy,
            errorMessage = errorMessage,
            onSubmit = onSubmit,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SignedInScreen(
    page: AccountPage,
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
    listWatchStats: List<YummyUserListWatchStat>,
    busy: Boolean,
    apiKeyEnabled: Boolean,
    apiKeyAvailable: Boolean,
    paddingValues: PaddingValues,
    onApiKeyEnabledChange: (Boolean) -> Unit,
    onApiKeyHelpClick: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot = remember(context.resources, profile, libraryItems, listWatchStats) {
        buildProfileSnapshot(context.resources, profile, libraryItems, listWatchStats)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (page) {
            AccountPage.Profile -> {
                ProfileCard(
                    profile = profile,
                    busy = busy,
                    onExit = onExit,
                )
                AnalyticsCard(snapshot = snapshot)
                RecentLibraryCard(items = snapshot.recentLibraryItems)
                Spacer(modifier = Modifier.height(24.dp))
            }
            AccountPage.Settings -> {
                AccountSettingsScreenContent(
                    busy = busy,
                    enabled = apiKeyEnabled,
                    available = apiKeyAvailable,
                    onEnabledChange = onApiKeyEnabledChange,
                    onHelpClick = onApiKeyHelpClick,
                    onExit = onExit,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CheckingContent() {
    Row(
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

private enum class AccountPage {
    Profile,
    Settings,
}

private sealed interface YummyAccountScreenState {
    data object Checking : YummyAccountScreenState
    data object SignedOut : YummyAccountScreenState
    data class SignedIn(
        val profile: YummyProfile,
        val libraryItems: List<YummyUserAnimeListItem>,
        val listWatchStats: List<YummyUserListWatchStat>,
    ) : YummyAccountScreenState

    data class Error(val message: String) : YummyAccountScreenState
}
