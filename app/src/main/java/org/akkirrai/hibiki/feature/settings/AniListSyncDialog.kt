package org.akkirrai.hibiki.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.anilist.AniListLibraryPush
import org.akkirrai.hibiki.core.anilist.AniListLibrarySync
import org.akkirrai.hibiki.core.anilist.AniListNsfwSourceException
import org.akkirrai.hibiki.core.anilist.AniListPrivateListException
import org.akkirrai.hibiki.core.anilist.AniListPushItem
import org.akkirrai.hibiki.core.anilist.AniListPushPlan
import org.akkirrai.hibiki.core.anilist.AniListPushResult
import org.akkirrai.hibiki.core.anilist.AniListRepository
import org.akkirrai.hibiki.core.anilist.AniListSyncReport
import org.akkirrai.hibiki.core.anilist.AniListUserNotFoundException
import org.akkirrai.hibiki.core.anilist.AniListViewer
import org.akkirrai.hibiki.core.design.component.anime.AnimeSourceBadge
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

/** AniList sync: who to sign in as, which source to look titles up on, and the import and send runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AniListSyncDialog(
    defaultSourceId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sync = remember { AniListLibrarySync(context) }
    val push = remember { AniListLibraryPush(context) }
    val scope = rememberCoroutineScope()
    val sources = AnimeSourceRegistry.sources
    val account = remember { AniListRepository(context) }
    var connected by remember { mutableStateOf(account.currentAccessToken() != null) }
    var viewer by remember { mutableStateOf<AniListViewer?>(null) }
    // The sign-in happens in the browser and returns through AniListAuthActivity, so the state is
    // read again whenever this screen comes back to the front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) connected = account.currentAccessToken() != null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            account.close()
        }
    }
    LaunchedEffect(connected) {
        viewer = if (connected) {
            runCatching { withContext(Dispatchers.IO) { account.getViewer() } }.getOrNull()
        } else {
            null
        }
    }

    var userName by remember { mutableStateOf(sync.userName) }
    var sourceId by remember {
        mutableStateOf(
            sync.sourceId.ifBlank { defaultSourceId }
                .takeIf { id -> sources.any { it.id.value == id } }
                ?: sources.firstOrNull()?.id?.value.orEmpty(),
        )
    }
    var useAccount by remember { mutableStateOf(sync.useAccount) }
    var skipNsfw by remember { mutableStateOf(sync.skipNsfw) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var report by remember { mutableStateOf<AniListSyncReport?>(sync.lastReport()) }
    var pushPlan by remember { mutableStateOf<AniListPushPlan?>(null) }
    var pushResult by remember { mutableStateOf<AniListPushResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUnmatched by remember { mutableStateOf(false) }

    val privateText = stringResource(R.string.anilist_sync_private)
    val missingText = stringResource(R.string.anilist_sync_user_missing)
    val offlineText = stringResource(R.string.home_error_no_internet)
    val failedText = stringResource(R.string.anilist_sync_failed)
    val nsfwSourceText = stringResource(R.string.anilist_sync_nsfw_source)

    fun describe(error: Throwable): String = when (error) {
        is AniListPrivateListException -> privateText
        is AniListUserNotFoundException -> missingText
        is NoInternetConnectionException -> offlineText
        is AniListNsfwSourceException -> nsfwSourceText
        else -> "$failedText: ${error.message.orEmpty()}"
    }

    val accountMode = useAccount && account.isConfigured
    val canImport = !running && sourceId.isNotBlank() && (if (accountMode) connected else userName.isNotBlank())

    Dialog(
        onDismissRequest = { if (!running) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !running, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(enabled = !running, onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.anilist_sync_close))
                    }
                    Text(
                        text = stringResource(R.string.settings_anilist_sync),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SyncCard {
                        if (account.isConfigured) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = useAccount,
                                    onClick = { useAccount = true },
                                    enabled = !running,
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                ) { Text(stringResource(R.string.anilist_sync_method_account)) }
                                SegmentedButton(
                                    selected = !useAccount,
                                    onClick = { useAccount = false },
                                    enabled = !running,
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                ) { Text(stringResource(R.string.anilist_sync_method_username)) }
                            }
                        }
                        if (accountMode) {
                            if (connected) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    ) {
                                        AsyncImage(
                                            model = viewer?.avatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = viewer?.name.orEmpty(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(R.string.anilist_sync_signed_in),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(enabled = !running, onClick = { account.disconnect(); connected = false }) {
                                        Text(stringResource(R.string.anilist_sync_sign_out))
                                    }
                                }
                            } else {
                                Button(
                                    enabled = !running,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        account.beginAuthorization()?.let { request ->
                                            runCatching {
                                                context.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(request.url),
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                ) { Text(stringResource(R.string.anilist_sync_sign_in)) }
                            }
                        } else {
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { userName = it },
                                label = { Text(stringResource(R.string.anilist_sync_username)) },
                                singleLine = true,
                                enabled = !running,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    SyncCard {
                        Text(
                            text = stringResource(R.string.anilist_sync_source),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (sources.isEmpty()) {
                            Text(stringResource(R.string.settings_sources_empty))
                        } else {
                            var menuOpen by remember { mutableStateOf(false) }
                            val chosen = sources.firstOrNull { it.id.value == sourceId } ?: sources.first()
                            ExposedDropdownMenuBox(
                                expanded = menuOpen,
                                onExpandedChange = { if (!running) menuOpen = it },
                            ) {
                                OutlinedTextField(
                                    value = chosen.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !running,
                                    singleLine = true,
                                    leadingIcon = { SourceIcon(chosen) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                )
                                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    sources.forEach { source ->
                                        DropdownMenuItem(
                                            text = { Text(source.name) },
                                            leadingIcon = { SourceIcon(source) },
                                            onClick = {
                                                sourceId = source.id.value
                                                menuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.anilist_sync_skip_nsfw),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = skipNsfw,
                                enabled = !running,
                                onCheckedChange = {
                                    skipNsfw = it
                                    sync.skipNsfw = it
                                },
                            )
                        }
                    }

                    Button(
                        enabled = canImport,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        onClick = {
                            sync.userName = userName
                            sync.useAccount = useAccount
                            sync.sourceId = sourceId
                            errorMessage = null
                            running = true
                            progress = 0 to 0
                            scope.launch {
                                try {
                                    report = withContext(Dispatchers.IO) {
                                        sync.run { done, total -> progress = done to total }
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Throwable) {
                                    errorMessage = describe(error)
                                } finally {
                                    running = false
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(R.string.anilist_import_button),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (accountMode && connected) {
                        FilledTonalButton(
                            enabled = !running && sourceId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            onClick = {
                                sync.sourceId = sourceId
                                sync.useAccount = useAccount
                                errorMessage = null
                                pushResult = null
                                running = true
                                progress = 0 to 0
                                scope.launch {
                                    try {
                                        pushPlan = withContext(Dispatchers.IO) {
                                            push.plan { done, total -> progress = done to total }
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Throwable) {
                                        errorMessage = describe(error)
                                    } finally {
                                        running = false
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = stringResource(R.string.anilist_push_button),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    if (running) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val (done, total) = progress
                            if (total > 0) {
                                LinearProgressIndicator(
                                    progress = { done.toFloat() / total },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                text = stringResource(R.string.anilist_sync_running, done, total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    errorMessage?.let { message ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    pushResult?.let { result ->
                        SyncCard {
                            Text(
                                text = stringResource(R.string.anilist_push_result, result.sent, result.failed),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    report?.takeIf { !running }?.let { last ->
                        SyncCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(last.added, stringResource(R.string.anilist_stat_added), Modifier.weight(1f))
                                StatTile(last.updated, stringResource(R.string.anilist_stat_updated), Modifier.weight(1f))
                                StatTile(last.unmatched.size, stringResource(R.string.anilist_stat_missing), Modifier.weight(1f))
                            }
                            if (last.unmatched.isNotEmpty()) {
                                TextButton(onClick = { showUnmatched = !showUnmatched }) {
                                    Text(stringResource(R.string.anilist_sync_unmatched))
                                }
                                if (showUnmatched) {
                                    last.unmatched.forEach { name ->
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pushPlan?.let { plan ->
        PushPreview(
            plan = plan,
            onDismiss = { pushPlan = null },
            onSend = {
                pushPlan = null
                errorMessage = null
                running = true
                progress = 0 to plan.items.size
                scope.launch {
                    try {
                        pushResult = withContext(Dispatchers.IO) {
                            push.execute(plan) { done, total -> progress = done to total }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        errorMessage = describe(error)
                    } finally {
                        running = false
                    }
                }
            },
        )
    }
}

@Composable
private fun SyncCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What a send would change, one card per title with its cover, so it can be checked before it is written. */
@Composable
private fun PushPreview(
    plan: AniListPushPlan,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSkipped by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.anilist_sync_close))
                    }
                    Text(
                        text = stringResource(R.string.anilist_push_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = if (plan.items.isEmpty()) {
                        stringResource(R.string.anilist_push_nothing)
                    } else {
                        stringResource(R.string.anilist_push_summary, plan.items.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(plan.items, key = { it.titleId }) { item -> PushItemCard(item) }
                    if (plan.skipped.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showSkipped = !showSkipped }) {
                                Text(stringResource(R.string.anilist_push_skipped, plan.skipped.size))
                            }
                        }
                        if (showSkipped) {
                            items(plan.skipped) { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
                if (plan.items.isNotEmpty()) {
                    Button(
                        onClick = onSend,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(R.string.anilist_push_send_count, plan.items.size),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PushItemCard(item: AniListPushItem) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.statusCategory?.let { category ->
                        InfoChip {
                            Icon(category.icon(), contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(stringResource(category.labelResId), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    item.progress?.let { episode ->
                        InfoChip {
                            Text(
                                text = stringResource(R.string.anilist_push_episode, episode),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (item.favourite) {
                        InfoChip {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFFC107),
                            )
                        }
                    }
                }
                AnimeSourceBadge(titleId = item.titleId)
            }
        }
    }
}

@Composable
private fun InfoChip(content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun SourceIcon(source: AnimeSourceDescriptor) {
    AsyncImage(
        model = source.iconUrl,
        placeholder = painterResource(source.iconRes),
        error = painterResource(source.iconRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp).clip(CircleShape),
    )
}
