package org.akkirrai.hibiki.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserListWatchStat
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppFloatingBackButton
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader
import org.akkirrai.hibiki.core.design.component.AppFloatingIconButton
import org.akkirrai.hibiki.core.design.component.YummySignInForm
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository

@Composable
fun YummyAccountScreen(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YummyAccountViewModel = viewModel(factory = YummyAccountViewModel.Factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.screenState
    val notificationCount = 0

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        when (state) {
            YummyAccountScreenState.Checking -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CheckingContent()
            }

            YummyAccountScreenState.SignedOut -> SignedOutScreen(
                busy = uiState.busy,
                errorMessage = null,
                paddingValues = PaddingValues(0.dp),
                onSubmit = viewModel::submitCredentials,
                onInputChanged = viewModel::clearSignInError,
            )

            is YummyAccountScreenState.Error -> SignedOutScreen(
                busy = uiState.busy,
                errorMessage = state.message,
                paddingValues = PaddingValues(0.dp),
                onSubmit = viewModel::submitCredentials,
                onInputChanged = viewModel::clearSignInError,
            )

            is YummyAccountScreenState.SignedIn -> SignedInProfileScreen(
                profile = state.profile,
                libraryItems = state.libraryItems,
                listWatchStats = state.listWatchStats,
                busy = uiState.busy,
                paddingValues = PaddingValues(top = AccountHeaderContentTopPadding),
                onExit = viewModel::signOut,
            )
        }

        when (state) {
            is YummyAccountScreenState.SignedIn -> {
                AppFloatingHeader(
                    title = stringResource(R.string.yummy_account_title),
                    onBackClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(),
                    actions = {
                        NotificationActionIcon(
                            notificationCount = notificationCount,
                            onClick = {},
                        )
                        AppFloatingIconButton(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.yummy_account_settings_title),
                            onClick = onSettingsClick,
                        )
                    },
                )
            }
            else -> {
                AppFloatingBackButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = UiDimens.ScreenPadding, top = 14.dp)
                        .statusBarsPadding(),
                )
            }
        }
    }
}

@Composable
fun YummyAccountSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YummyAccountViewModel = viewModel(factory = YummyAccountViewModel.Factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.screenState
    var apiKeyHelpVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        when (state) {
            YummyAccountScreenState.Checking -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CheckingContent()
            }

            YummyAccountScreenState.SignedOut -> SignedOutScreen(
                busy = uiState.busy,
                errorMessage = null,
                paddingValues = PaddingValues(top = AccountSettingsHeaderContentTopPadding),
                onSubmit = viewModel::submitCredentials,
                onInputChanged = viewModel::clearSignInError,
            )

            is YummyAccountScreenState.Error -> SignedOutScreen(
                busy = uiState.busy,
                errorMessage = state.message,
                paddingValues = PaddingValues(top = AccountSettingsHeaderContentTopPadding),
                onSubmit = viewModel::submitCredentials,
                onInputChanged = viewModel::clearSignInError,
            )

            is YummyAccountScreenState.SignedIn -> SignedInSettingsScreen(
                busy = uiState.busy,
                apiKeyEnabled = uiState.apiKeyEnabled,
                apiKeyAvailable = uiState.apiKeyAvailable,
                paddingValues = PaddingValues(top = AccountSettingsHeaderContentTopPadding),
                onApiKeyEnabledChange = viewModel::setApplicationTokenEnabled,
                onApiKeyHelpClick = { apiKeyHelpVisible = true },
                onExit = viewModel::signOut,
            )
        }

        AppFloatingHeader(
            title = stringResource(R.string.yummy_account_settings_title),
            onBackClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
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

@Composable
private fun SignedOutScreen(
    busy: Boolean,
    errorMessage: String?,
    paddingValues: PaddingValues,
    onSubmit: (String, String) -> Unit,
    onInputChanged: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp)
            .wrapContentHeight(align = Alignment.CenterVertically)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        YummySignInForm(
            busy = busy,
            errorMessage = errorMessage,
            onSubmit = onSubmit,
            onInputChanged = onInputChanged,
        )
    }
}

@Composable
private fun SignedInProfileScreen(
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
    listWatchStats: List<YummyUserListWatchStat>,
    busy: Boolean,
    paddingValues: PaddingValues,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val offlineTitleMetadataRepository = remember(context) { OfflineTitleMetadataRepository(context) }
    val cachedLibraryMetadata = remember(libraryItems, offlineTitleMetadataRepository) {
        libraryItems.mapNotNull { item ->
            offlineTitleMetadataRepository.get(item.animeId.toString())
        }
    }
    val snapshot = remember(context.resources, profile, libraryItems, listWatchStats, cachedLibraryMetadata) {
        buildProfileSnapshot(
            resources = context.resources,
            profile = profile,
            libraryItems = libraryItems,
            listWatchStats = listWatchStats,
            cachedLibraryMetadata = cachedLibraryMetadata,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileCard(
            profile = profile,
            busy = busy,
            onExit = onExit,
        )
        AnalyticsCard(snapshot = snapshot)
        RecentLibraryCard(items = snapshot.recentLibraryItems)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SignedInSettingsScreen(
    busy: Boolean,
    apiKeyEnabled: Boolean,
    apiKeyAvailable: Boolean,
    paddingValues: PaddingValues,
    onApiKeyEnabledChange: (Boolean) -> Unit,
    onApiKeyHelpClick: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

private val AccountHeaderContentTopPadding = 72.dp
private val AccountSettingsHeaderContentTopPadding = 72.dp
