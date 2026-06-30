package org.akkirrai.hibiki.feature.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyProfileSex
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserList
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.account.YummyAccountSessionState
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.PosterPlaceholder

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
        topBar = {
            AccountTopBar(
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
                action = if (state is YummyAccountScreenState.SignedIn && page == AccountPage.Profile) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TopBarActionIcon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = "Уведомления",
                                badgeCount = notificationCount,
                                onClick = {},
                            )
                            TopBarActionIcon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.yummy_account_settings_title),
                                onClick = { page = AccountPage.Settings },
                            )
                        }
                    }
                } else {
                    null
                },
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            YummyAccountScreenState.Checking -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CheckingContent()
            }

            YummyAccountScreenState.SignedOut -> SignedOutScreen(
                busy = busy,
                errorMessage = null,
                paddingValues = innerPadding,
                onSubmit = { login, secret ->
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
                },
            )

            is YummyAccountScreenState.Error -> SignedOutScreen(
                busy = busy,
                errorMessage = current.message,
                paddingValues = innerPadding,
                onSubmit = { login, secret ->
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
                },
            )

            is YummyAccountScreenState.SignedIn -> SignedInScreen(
                page = page,
                profile = current.profile,
                libraryItems = current.libraryItems,
                busy = busy,
                apiKeyEnabled = apiKeyEnabled,
                apiKeyAvailable = apiKeyAvailable,
                paddingValues = innerPadding,
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
private fun TopBarActionIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    badgeCount: Int = 0,
    onClick: () -> Unit,
) {
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.38f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(WARM_ACCENT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeCount.coerceAtMost(9).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AccountTopBar(
    title: String,
    onBackClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = UiDimens.ScreenPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (action != null) {
                action()
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }
        }
    }
}

private suspend fun loadSignedInState(
    repository: YummyAccountRepository,
    profile: YummyProfile,
): YummyAccountScreenState.SignedIn {
    val libraryItems = runCatching { repository.getUserLists(profile.id) }
        .getOrDefault(emptyList())
    return YummyAccountScreenState.SignedIn(profile, libraryItems)
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
        AccountForm(
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
    busy: Boolean,
    apiKeyEnabled: Boolean,
    apiKeyAvailable: Boolean,
    paddingValues: PaddingValues,
    onApiKeyEnabledChange: (Boolean) -> Unit,
    onApiKeyHelpClick: () -> Unit,
    onExit: () -> Unit,
) {
    val snapshot = remember(profile, libraryItems) {
        buildProfileSnapshot(profile, libraryItems)
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

@Composable
private fun AccountForm(
    busy: Boolean,
    errorMessage: String?,
    onSubmit: (String, String) -> Unit,
) {
    var loginValue by remember { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val loginRequiredMessage = stringResource(R.string.yummy_account_login_required)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(text = stringResource(R.string.yummy_account_login_label))
                },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = textFieldColors(),
            )
            OutlinedTextField(
                value = secretValue,
                onValueChange = {
                    secretValue = it
                    localError = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(text = stringResource(R.string.yummy_account_password_label))
                },
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                colors = textFieldColors(),
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
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.yummy_account_password_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: YummyProfile,
    busy: Boolean,
    onExit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                avatarUrl = profile.avatarUrl,
                nickname = profile.nickname,
                modifier = Modifier.size(60.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = profile.nickname.ifBlank { stringResource(R.string.yummy_account_profile_fallback) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProfileMetaLine(
                    registeredAt = profile.registerDate?.let(::formatEpochDateCompact) ?: "—",
                    sex = profile.sex.toLabel(),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f))
                    .clickable(enabled = !busy, onClick = onExit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Logout,
                    contentDescription = stringResource(R.string.action_sign_out),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun AnalyticsCard(
    snapshot: YummyProfileSnapshot,
) {
    val hasActivity = snapshot.activeDaysCount > 0
    val statusSegments = remember(snapshot.distributionSegments, snapshot.favoriteCount) {
        snapshot.distributionSegments + DistributionSegment(
            label = "Любимое",
            count = snapshot.favoriteCount,
            color = FAVORITE_ACCENT,
        )
    }
    val durationSegments = remember(snapshot.durationSegments, snapshot.favoriteHoursLabel) {
        snapshot.durationSegments + DurationSegment(
            label = "Любимое",
            hoursLabel = snapshot.favoriteHoursLabel,
            value = 0L,
            color = FAVORITE_ACCENT,
        )
    }
    val hasDurationData = durationSegments.any { it.value > 0L }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Активность",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.025f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    ActivityHeatmap(
                        days = snapshot.activityDays,
                        rows = 7,
                        columns = 20,
                        cellSize = 8,
                        gap = 3,
                        muted = !hasActivity,
                    )
                    if (!hasActivity) {
                        Text(
                            text = "Нет активности",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactMetricChip(
                    modifier = Modifier.weight(1f),
                    value = snapshot.streakDays.toString(),
                    label = "дней подряд",
                )
                CompactMetricChip(
                    modifier = Modifier.weight(1f),
                    value = snapshot.activeDaysCount.toString(),
                    label = "активных дней",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Время по спискам",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DistributionDonut(
                        segments = durationSegments,
                        centerLabel = snapshot.watchTimeLabel,
                        modifier = Modifier.size(78.dp),
                        muted = !hasDurationData,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        durationSegments.forEach { segment ->
                            DurationLegendRow(segment = segment, muted = !hasDurationData)
                        }
                    }
                }
            }
            Text(
                text = "Списки",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusTilesGrid(segments = statusSegments)
        }
    }
}

@Composable
private fun CompactMetricChip(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.09f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WARM_ACCENT,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusTilesGrid(
    segments: List<DistributionSegment>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { segment ->
                    StatusTile(
                        modifier = Modifier.weight(1f),
                        segment = segment,
                    )
                }
                repeat(2 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusTile(
    segment: DistributionSegment,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.13f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(segment.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segment.color),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = segment.count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProfileMetaLine(
    registeredAt: String,
    sex: String,
) {
    Text(
        text = "$registeredAt • $sex",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Avatar(
    avatarUrl: String?,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val resolvedUrl = normalizeYummyAssetUrl(avatarUrl)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        if (resolvedUrl == null) {
            DefaultAvatarPlaceholder(nickname)
        } else {
            SubcomposeAsyncImage(
                model = resolvedUrl,
                contentDescription = nickname,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                },
                error = {
                    PosterPlaceholder(modifier = Modifier.fillMaxSize()) {
                        DefaultAvatarPlaceholder(nickname)
                    }
                },
            )
        }
    }
}

@Composable
private fun DefaultAvatarPlaceholder(
    nickname: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8F8F8), Color(0xFFD6D6D6)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = nickname,
            modifier = Modifier.size(40.dp),
            tint = Color(0xFF6D6D6D),
        )
    }
}

@Composable
private fun DistributionDonut(
    segments: List<DurationSegment>,
    centerLabel: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val trackColor = if (muted) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            var startAngle = -90f
            val safeTotal = segments.sumOf(DurationSegment::value).coerceAtLeast(1L)
            segments.filter { it.value > 0L }.forEach { segment ->
                val sweep = segment.value / safeTotal.toFloat() * 360f
                drawArc(
                    color = if (muted) segment.color.copy(alpha = 0.4f) else segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = "всего",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun DurationLegendRow(
    segment: DurationSegment,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(segment.color),
        )
        Text(
            text = segment.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = segment.hoursLabel,
            modifier = Modifier.width(30.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
            } else {
                WARM_ACCENT
            },
        )
    }
}

@Composable
private fun ActivityHeatmap(
    days: List<ActivityDay>,
    rows: Int = 7,
    columns: Int = 20,
    cellSize: Int = 14,
    gap: Int = 2,
    muted: Boolean = false,
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f)
    val paddedDays = buildList<ActivityDay?> {
        addAll(days.takeLast(rows * columns))
        repeat((rows * columns) - size) { add(null) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        repeat(rows) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                repeat(columns) { columnIndex ->
                    val itemIndex = columnIndex * rows + rowIndex
                    val day = paddedDays.getOrNull(itemIndex)
                    Box(
                        modifier = Modifier
                            .size(cellSize.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    day == null || day.intensity <= 0 -> emptyColor
                                    else -> day.color(base = WARM_ACCENT, muted = muted)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentLibraryCard(
    items: List<RecentLibraryItem>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = "Последние добавления",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    EmptyState(text = "Нет недавних добавлений")
                }
            } else {
                items.forEach { item ->
                    RecentLibraryRow(item)
                }
            }
        }
    }
}

@Composable
private fun RecentLibraryRow(
    item: RecentLibraryItem,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.22f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(item.color),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSettingsScreenContent(
    busy: Boolean,
    enabled: Boolean,
    available: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onHelpClick: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccessSettingsCard(
            enabled = enabled,
            available = available,
            onEnabledChange = onEnabledChange,
            onHelpClick = onHelpClick,
        )
        AccountDangerZoneCard(
            busy = busy,
            onExit = onExit,
        )
    }
}

@Composable
private fun AccessSettingsCard(
    enabled: Boolean,
    available: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onHelpClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.yummy_account_settings_access_section))
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            SettingsListSurface {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.yummy_account_api_key_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (available) {
                                stringResource(R.string.yummy_account_api_key_enabled_hint_short)
                            } else {
                                stringResource(R.string.yummy_account_api_key_disabled_hint_short)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onHelpClick,
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = stringResource(R.string.yummy_account_api_key_help_cd),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = onEnabledChange,
                            enabled = true,
                            modifier = Modifier.scale(0.86f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDangerZoneCard(
    busy: Boolean,
    onExit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.yummy_account_settings_account_section))
        SettingsListSurface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy, onClick = onExit)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.yummy_account_sign_out_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    OutlinedButton(
                        onClick = onExit,
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_sign_out),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsListSurface(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        content()
    }
}

@Composable
private fun EmptyState(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionTitle(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
    focusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
    disabledIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
)

private fun buildProfileSnapshot(
    profile: YummyProfile,
    libraryItems: List<YummyUserAnimeListItem>,
): YummyProfileSnapshot {
    val history = profile.watches?.history.orEmpty()
    val watchSums = profile.watches?.sum.orEmpty()
    val activityCounts = history
        .mapNotNull { it.date?.let(::epochToLocalDate) }
        .groupingBy { it }
        .eachCount()

    val today = LocalDate.now()
    val activityDays = (0 until HEATMAP_DAYS)
        .map { today.minusDays((HEATMAP_DAYS - 1 - it).toLong()) }
        .map { date ->
            ActivityDay(
                intensity = (activityCounts[date] ?: 0).coerceAtMost(4),
            )
        }

    val streakDays = generateSequence(today) { it.minusDays(1) }
        .takeWhile { date -> (activityCounts[date] ?: 0) > 0 }
        .count()

    val totalDuration = watchSums.sumOf { it.spentTime ?: 0L }
        .takeIf { it > 0L }
        ?: history.sumOf { it.duration ?: 0L }

    val recentItems = libraryItems
        .asSequence()
        .filter { !it.title.isBlank() && it.addedAt != null }
        .sortedByDescending { it.addedAt }
        .take(6)
        .map { item ->
            RecentLibraryItem(
                title = item.title,
                statusLabel = item.list.toReadableLabel(),
                dateLabel = item.addedAt?.let(::formatEpochDateShort).orEmpty(),
                color = item.list.toColor(),
            )
        }
        .toList()

    return YummyProfileSnapshot(
        watchTimeLabel = formatDurationLabel(totalDuration),
        streakDays = streakDays,
        streakLabel = streakDays.toString(),
        activeDaysCount = activityCounts.size,
        totalEpisodes = history.sumOf { it.episodeCount ?: 0 },
        favoriteCount = libraryItems.count(YummyUserAnimeListItem::isFavorite),
        favoriteHoursLabel = localizedHourLabel("0"),
        libraryTotal = libraryItems.size,
        distributionSegments = buildDistributionSegments(libraryItems),
        durationSegments = buildDurationSegments(watchSums),
        activityDays = activityDays,
        recentLibraryItems = recentItems,
        onlineDaysLabel = profile.daysOnline?.toString() ?: "—",
    )
}

private fun buildDistributionSegments(
    libraryItems: List<YummyUserAnimeListItem>,
): List<DistributionSegment> {
    val counts = libraryItems.groupingBy { it.list }.eachCount()
    return listOf(
        DistributionSegment(localizedListLabel(YummyUserList.Watching), counts[YummyUserList.Watching] ?: 0, YummyUserList.Watching.toColor()),
        DistributionSegment(localizedListLabel(YummyUserList.Planned), counts[YummyUserList.Planned] ?: 0, YummyUserList.Planned.toColor()),
        DistributionSegment(localizedListLabel(YummyUserList.Completed), counts[YummyUserList.Completed] ?: 0, YummyUserList.Completed.toColor()),
        DistributionSegment(localizedListLabel(YummyUserList.Dropped), counts[YummyUserList.Dropped] ?: 0, YummyUserList.Dropped.toColor()),
        DistributionSegment(localizedListLabel(YummyUserList.OnHold), counts[YummyUserList.OnHold] ?: 0, YummyUserList.OnHold.toColor()),
    )
}

private fun buildDurationSegments(
    watchSums: List<org.akkirrai.animeresolver.metadata.YummyProfileWatchSum>,
): List<DurationSegment> {
    fun sumFor(vararg keys: String): Long {
        val normalizedKeys = keys.map { it.lowercase(Locale.ROOT) }
        return watchSums
            .filter { item ->
                val bucket = listOf(item.alias, item.shortName, item.name)
                    .filterNotNull()
                    .joinToString(" ")
                    .lowercase(Locale.ROOT)
                normalizedKeys.any(bucket::contains)
            }
            .sumOf { it.spentTime ?: 0L }
    }

    val values = listOf(
        Triple("Смотрю", sumFor("watch", "watching", "смотр"), YummyUserList.Watching.toColor()),
        Triple("В планах", sumFor("plan", "planned", "план"), YummyUserList.Planned.toColor()),
        Triple("Просмотрено", sumFor("complete", "completed", "просмотр"), YummyUserList.Completed.toColor()),
        Triple("Брошено", sumFor("drop", "dropped", "брош"), YummyUserList.Dropped.toColor()),
        Triple("Отложено", sumFor("hold", "on hold", "отлож"), YummyUserList.OnHold.toColor()),
    )

    return values.map { (label, value, color) ->
        DurationSegment(
            label = label,
            hoursLabel = formatDurationLabel(value),
            value = value,
            color = color,
        )
    }
}

private fun YummyProfileSex?.toLabel(): String {
    return when (this) {
        YummyProfileSex.Male -> "Мужской"
        YummyProfileSex.Female -> "Женский"
        else -> "Не указано"
    }
}

private fun YummyUserList?.toReadableLabel(): String = localizedListLabel(this)

private fun localizedListLabel(list: YummyUserList?): String {
    return when (list) {
        YummyUserList.Watching -> "Смотрю"
        YummyUserList.Planned -> "В планах"
        YummyUserList.Completed -> "Просмотрено"
        YummyUserList.Dropped -> "Брошено"
        YummyUserList.OnHold -> "Отложено"
        null -> "Не указано"
    }
}

private fun YummyUserList?.toColor(): Color {
    return when (this) {
        YummyUserList.Watching -> Color(0xFF3DDC84)
        YummyUserList.Planned -> Color(0xFF5DA9FF)
        YummyUserList.Completed -> Color(0xFFFFB84D)
        YummyUserList.Dropped -> Color(0xFFFF6B6B)
        YummyUserList.OnHold -> Color(0xFFC593FF)
        null -> Color(0xFF9EA4B2)
    }
}

private fun ActivityDay.color(
    base: Color,
    muted: Boolean = false,
): Color {
    if (muted) {
        return base.copy(alpha = 0.24f)
    }
    return when (intensity) {
        0 -> base.copy(alpha = 0.12f)
        1 -> base.copy(alpha = 0.28f)
        2 -> base.copy(alpha = 0.48f)
        3 -> base.copy(alpha = 0.7f)
        else -> base
    }
}

private fun formatDurationLabel(rawDuration: Long): String {
    if (rawDuration <= 0L) return localizedHourLabel("0")
    val secondsBasedHours = rawDuration / 3600.0
    val minutesBasedHours = rawDuration / 60.0
    val hours = when {
        rawDuration >= 10_000L -> secondsBasedHours
        rawDuration >= 180L -> minutesBasedHours
        else -> secondsBasedHours
    }
    return if (hours >= 10) {
        localizedHourLabel(hours.toInt().toString())
    } else {
        localizedHourLabel(String.format(Locale.US, "%.1f", hours))
    }
}

private fun epochToLocalDate(value: Long): LocalDate {
    val instant = if (value >= 1_000_000_000_000L) {
        Instant.ofEpochMilli(value)
    } else {
        Instant.ofEpochSecond(value)
    }
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun formatEpochDate(value: Long): String {
    return epochToLocalDate(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}

private fun formatEpochDateCompact(value: Long): String {
    return epochToLocalDate(value).format(DateTimeFormatter.ofPattern("dd.MM.yy", Locale("ru")))
}

private fun formatEpochDateShort(value: Long): String {
    val date = epochToLocalDate(value)
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    val ru = Locale.getDefault().language == "ru"
    return when {
        daysAgo <= 0 -> if (ru) "Сегодня" else "Today"
        daysAgo == 1 -> if (ru) "Вчера" else "Yesterday"
        daysAgo < 7 -> if (ru) "${daysAgo}д" else "${daysAgo}d"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

private fun localizedHourLabel(value: String): String {
    return "$value ч"
}

private fun normalizeYummyAssetUrl(rawUrl: String?): String? {
    val value = rawUrl?.trim()?.takeIf(String::isNotBlank) ?: return null
    return when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "$YUMMY_WEB_BASE$value"
        else -> "$YUMMY_WEB_BASE/$value"
    }
}

private data class YummyProfileSnapshot(
    val watchTimeLabel: String,
    val streakDays: Int,
    val streakLabel: String,
    val activeDaysCount: Int,
    val totalEpisodes: Int,
    val favoriteCount: Int,
    val favoriteHoursLabel: String,
    val libraryTotal: Int,
    val distributionSegments: List<DistributionSegment>,
    val durationSegments: List<DurationSegment>,
    val activityDays: List<ActivityDay>,
    val recentLibraryItems: List<RecentLibraryItem>,
    val onlineDaysLabel: String,
)

private data class DistributionSegment(
    val label: String,
    val count: Int,
    val color: Color,
)

private data class DurationSegment(
    val label: String,
    val hoursLabel: String,
    val value: Long,
    val color: Color,
)

private data class ActivityDay(
    val intensity: Int,
)

private data class RecentLibraryItem(
    val title: String,
    val statusLabel: String,
    val dateLabel: String,
    val color: Color,
)

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
    ) : YummyAccountScreenState

    data class Error(val message: String) : YummyAccountScreenState
}

private const val HEATMAP_DAYS = 140
private const val YUMMY_WEB_BASE = "https://ru.yummyani.me"
private val WARM_ACCENT = Color(0xFFFFB86A)
private val FAVORITE_ACCENT = Color(0xFFD06BFF)
