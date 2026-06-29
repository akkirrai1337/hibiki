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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.akkirrai.hibiki.core.design.component.PosterImage
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
                title = if (state is YummyAccountScreenState.SignedIn) {
                    stringResource(R.string.yummy_account_title)
                } else {
                    stringResource(R.string.yummy_account_sign_in_title)
                },
                onBackClick = onBackClick,
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
private fun AccountTopBar(
    title: String,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 10.dp),
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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
    var selectedSegment by remember { mutableStateOf(ProfileSegment.Stats) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileCard(
            profile = profile,
            snapshot = snapshot,
            busy = busy,
            onExit = onExit,
        )
        ProfileSegmentTabs(
            selected = selectedSegment,
            onSelected = { selectedSegment = it },
        )
        when (selectedSegment) {
            ProfileSegment.Stats -> {
                StatsGrid(snapshot = snapshot)
                StatusChipsGrid(segments = snapshot.distributionSegments)
                DistributionCard(snapshot = snapshot)
                ActivityCard(snapshot = snapshot, compact = true)
            }
            ProfileSegment.Activity -> {
                ActivityCard(snapshot = snapshot, compact = false)
                StatsGrid(snapshot = snapshot)
            }
            ProfileSegment.Library -> {
                StatusChipsGrid(segments = snapshot.distributionSegments)
                DistributionCard(snapshot = snapshot)
                RecentLibraryCard(items = snapshot.recentLibraryItems)
            }
        }
        AccessSettingsCard(
            enabled = apiKeyEnabled,
            available = apiKeyAvailable,
            onEnabledChange = onApiKeyEnabledChange,
            onHelpClick = onApiKeyHelpClick,
        )
        Spacer(modifier = Modifier.height(28.dp))
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
    snapshot: YummyProfileSnapshot,
    busy: Boolean,
    onExit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box {
            ProfileBanner(
                bannerUrl = profile.banner?.cropped ?: profile.banner?.full,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)),
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        avatarUrl = profile.avatarUrl,
                        nickname = profile.nickname,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = profile.nickname.ifBlank { stringResource(R.string.yummy_account_profile_fallback) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        CompactProfileFacts(
                            registeredAt = profile.registerDate?.let(::formatEpochDate) ?: "-",
                            birthDate = profile.birthDate?.let(::formatEpochDate) ?: "-",
                            sex = profile.sex.toLabel(),
                        )
                    }
                    IconButton(
                        onClick = onExit,
                        enabled = !busy,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f)),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = stringResource(R.string.action_sign_out),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.82f),
                            )
                        }
                    }
                }
                SocialLinksRow(profile = profile)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StreakTile(
                        modifier = Modifier.weight(1f),
                        days = snapshot.streakDays.coerceAtLeast(1),
                    )
                    ProfileMetricStrip(
                        modifier = Modifier.weight(1.35f),
                        snapshot = snapshot,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactProfileFacts(
    registeredAt: String,
    birthDate: String,
    sex: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        CompactFactLine(stringResource(R.string.yummy_account_profile_registered), registeredAt)
        CompactFactLine(stringResource(R.string.yummy_account_profile_birthdate), birthDate)
        CompactFactLine(stringResource(R.string.yummy_account_profile_gender), sex)
    }
}

@Composable
private fun CompactFactLine(
    title: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.width(94.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileMetricStrip(
    snapshot: YummyProfileSnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniMetric(
                value = snapshot.watchTimeLabel,
                label = stringResource(R.string.yummy_account_stat_watch_time),
            )
            MiniMetric(
                value = snapshot.libraryTotal.toString(),
                label = stringResource(R.string.yummy_account_stat_library),
            )
        }
    }
}

@Composable
private fun MiniMetric(
    value: String,
    label: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileInfoGroup(
    items: List<Pair<String, String>>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { (title, value) ->
                ProfileInfoLine(
                    title = title,
                    value = value,
                )
            }
        }
    }
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
            .size(118.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFEDEDED)),
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
private fun ProfileBanner(
    bannerUrl: String?,
    modifier: Modifier = Modifier,
) {
    val resolvedUrl = normalizeYummyAssetUrl(bannerUrl)
    if (resolvedUrl == null) {
        DefaultBannerPlaceholder(modifier)
    } else {
        Box(modifier = modifier) {
            PosterImage(
                primaryUrl = resolvedUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = { DefaultBannerPlaceholder(Modifier.fillMaxSize()) },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f)),
            )
        }
    }
}

@Composable
private fun DefaultBannerPlaceholder(
    modifier: Modifier = Modifier,
) {
    val markColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = 28.dp.toPx()
            val radius = 5.dp.toPx()
            var y = -cell
            while (y < size.height + cell) {
                var x = -cell
                while (x < size.width + cell) {
                    drawCircle(
                        color = markColor,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(x + cell / 2f, y + cell / 2f),
                    )
                    x += cell
                }
                y += cell
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.TopCenter)
                .background(Color(0xFFFF6166)),
        )
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
            modifier = Modifier.size(54.dp),
            tint = Color(0xFF6D6D6D),
        )
    }
}

@Composable
private fun ProfileInfoLine(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title:",
            modifier = Modifier.width(116.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value.ifBlank { "â€”" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SocialLinksRow(
    profile: YummyProfile,
) {
    val links = buildList {
        if (profile.ids?.vkId != null) add("VK" to Color(0xFF3375D6))
        if (!profile.ids?.telegramNickname.isNullOrBlank()) add("TG" to Color(0xFF2699D6))
        if (!profile.ids?.shikimoriNickname.isNullOrBlank()) add("SH" to Color(0xFF4F5366))
    }
    if (links.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        links.forEach { (text, color) ->
            SocialBadge(text = text, color = color)
        }
    }
}

@Composable
private fun SocialBadge(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun StreakTile(
    days: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = days.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.yummy_account_streak_site_days),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileSegmentTabs(
    selected: ProfileSegment,
    onSelected: (ProfileSegment) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ProfileSegment.entries.forEach { segment ->
                SegmentTab(
                    modifier = Modifier.weight(1f),
                    text = stringResource(segment.titleRes),
                    selected = selected == segment,
                    onClick = { onSelected(segment) },
                )
            }
        }
    }
}

@Composable
private fun SegmentTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        } else {
            Color.Transparent
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusChipsGrid(
    segments: List<DistributionSegment>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.yummy_account_statuses_title))
            segments.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { segment ->
                        StatusChip(
                            modifier = Modifier.weight(1f),
                            segment = segment,
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    segment: DistributionSegment,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.46f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(segment.color),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = segment.count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(
    snapshot: YummyProfileSnapshot,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.yummy_account_stat_watch_time),
            value = snapshot.watchTimeLabel,
            subtitle = stringResource(R.string.yummy_account_stat_episodes, snapshot.totalEpisodes),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.yummy_account_stat_streak),
            value = snapshot.streakLabel,
            subtitle = stringResource(R.string.yummy_account_stat_active_days, snapshot.activeDaysCount),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.yummy_account_stat_library),
            value = snapshot.libraryTotal.toString(),
            subtitle = stringResource(R.string.yummy_account_stat_favorites, snapshot.favoriteCount),
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DistributionCard(
    snapshot: YummyProfileSnapshot,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.yummy_account_distribution_title))
            if (snapshot.distributionSegments.none { it.count > 0 }) {
                DistributionEmptyPreview(segments = snapshot.distributionSegments)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DistributionDonut(
                        segments = snapshot.distributionSegments,
                        total = snapshot.libraryTotal,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        snapshot.distributionSegments.filter { it.count > 0 }.forEach { segment ->
                            DistributionRow(segment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionEmptyPreview(
    segments: List<DistributionSegment>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(82.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Text(
                text = "0",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            segments.take(3).forEach { segment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(segment.color.copy(alpha = 0.44f)),
                    )
                    Text(
                        text = segment.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                    )
                }
            }
            Text(
                text = stringResource(R.string.yummy_account_distribution_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DistributionDonut(
    segments: List<DistributionSegment>,
    total: Int,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier.size(92.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            var startAngle = -90f
            val safeTotal = total.coerceAtLeast(1)
            segments.filter { it.count > 0 }.forEach { segment ->
                val sweep = segment.count / safeTotal.toFloat() * 360f
                drawArc(
                    color = segment.color,
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
                text = total.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.yummy_account_distribution_total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DistributionRow(
    segment: DistributionSegment,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(segment.color),
        )
        Text(
            text = segment.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = segment.count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityCard(
    snapshot: YummyProfileSnapshot,
    compact: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.yummy_account_activity_title))
            if (snapshot.activeDaysCount == 0) {
                ActivityHeatmap(
                    days = snapshot.activityDays.takeLast(34),
                    columns = 17,
                    cellSize = 10,
                    muted = true,
                )
                EmptyState(text = stringResource(R.string.yummy_account_activity_empty))
            } else {
                ActivityHeatmap(
                    days = if (compact) snapshot.activityDays.takeLast(34) else snapshot.activityDays,
                    columns = 17,
                    cellSize = if (compact) 10 else 14,
                )
                Text(
                    text = stringResource(
                        R.string.yummy_account_activity_summary,
                        snapshot.activeDaysCount,
                        snapshot.streakDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityHeatmap(
    days: List<ActivityDay>,
    columns: Int = 17,
    cellSize: Int = 14,
    muted: Boolean = false,
) {
    val rows = days.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { day ->
                    Box(
                        modifier = Modifier
                            .size(cellSize.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                day.color(
                                    base = MaterialTheme.colorScheme.primary,
                                    muted = muted,
                                ),
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.yummy_account_recent_library_title))
            if (items.isEmpty()) {
                EmptyState(text = stringResource(R.string.yummy_account_recent_library_empty))
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
private fun AccessSettingsCard(
    enabled: Boolean,
    available: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onHelpClick: () -> Unit,
) {
    val statusHint = if (available) {
        stringResource(R.string.yummy_account_api_key_enabled_hint)
    } else {
        stringResource(R.string.yummy_account_api_key_disabled_hint)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.yummy_account_access_title))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.yummy_account_api_key_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
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
                    }
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun EmptyState(
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.54f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
        libraryTotal = libraryItems.size,
        distributionSegments = buildDistributionSegments(libraryItems),
        activityDays = activityDays,
        recentLibraryItems = recentItems,
        onlineDaysLabel = profile.daysOnline?.toString() ?: "â€”",
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

private fun YummyProfileSex?.toLabel(): String {
    val ru = Locale.getDefault().language == "ru"
    return when (this) {
        YummyProfileSex.Male -> if (ru) "ÐœÑƒÐ¶ÑÐºÐ¾Ð¹" else "Male"
        YummyProfileSex.Female -> if (ru) "Ð–ÐµÐ½ÑÐºÐ¸Ð¹" else "Female"
        else -> "â€”"
    }
}

private fun YummyUserList?.toReadableLabel(): String = localizedListLabel(this)

private fun localizedListLabel(list: YummyUserList?): String {
    val ru = Locale.getDefault().language == "ru"
    return when (list) {
        YummyUserList.Watching -> if (ru) "Ð¡Ð¼Ð¾Ñ‚Ñ€ÑŽ" else "Watching"
        YummyUserList.Planned -> if (ru) "Ð—Ð°Ð¿Ð»Ð°Ð½Ð¸Ñ€Ð¾Ð²Ð°Ð½Ð¾" else "Planned"
        YummyUserList.Completed -> if (ru) "ÐŸÑ€Ð¾ÑÐ¼Ð¾Ñ‚Ñ€ÐµÐ½Ð¾" else "Completed"
        YummyUserList.Dropped -> if (ru) "Ð‘Ñ€Ð¾ÑˆÐµÐ½Ð¾" else "Dropped"
        YummyUserList.OnHold -> if (ru) "ÐžÑ‚Ð»Ð¾Ð¶ÐµÐ½Ð¾" else "On hold"
        null -> if (ru) "ÐÐµ ÑƒÐºÐ°Ð·Ð°Ð½Ð¾" else "Unknown"
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
        return base.copy(alpha = 0.08f)
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

private fun formatEpochDateShort(value: Long): String {
    val date = epochToLocalDate(value)
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
    val ru = Locale.getDefault().language == "ru"
    return when {
        daysAgo <= 0 -> if (ru) "Ð¡ÐµÐ³Ð¾Ð´Ð½Ñ" else "Today"
        daysAgo == 1 -> if (ru) "Ð’Ñ‡ÐµÑ€Ð°" else "Yesterday"
        daysAgo < 7 -> if (ru) "${daysAgo}Ð´" else "${daysAgo}d"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

private fun localizedHourLabel(value: String): String {
    return if (Locale.getDefault().language == "ru") "$value Ñ‡" else "${value}h"
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
    val libraryTotal: Int,
    val distributionSegments: List<DistributionSegment>,
    val activityDays: List<ActivityDay>,
    val recentLibraryItems: List<RecentLibraryItem>,
    val onlineDaysLabel: String,
)

private data class DistributionSegment(
    val label: String,
    val count: Int,
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

private enum class ProfileSegment(
    val titleRes: Int,
) {
    Stats(R.string.yummy_account_segment_stats),
    Activity(R.string.yummy_account_segment_activity),
    Library(R.string.yummy_account_segment_library),
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

private const val HEATMAP_DAYS = 68
private const val YUMMY_WEB_BASE = "https://ru.yummyani.me"
