package org.akkirrai.hibiki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.anilist.AniListLibraryPush
import org.akkirrai.hibiki.core.anilist.AniListLibrarySync
import org.akkirrai.hibiki.core.anilist.AniListNsfwSourceException
import org.akkirrai.hibiki.core.anilist.AniListPushPlan
import org.akkirrai.hibiki.core.anilist.AniListPushResult
import org.akkirrai.hibiki.core.anilist.AniListPrivateListException
import org.akkirrai.hibiki.core.anilist.AniListRepository
import org.akkirrai.hibiki.core.anilist.AniListSyncReport
import org.akkirrai.hibiki.core.anilist.AniListUserNotFoundException
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

/** One-way AniList to Hibiki library sync: who to read, which source to look the titles up on, and the run itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AniListSyncDialog(
    defaultSourceId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sync = remember { AniListLibrarySync(context) }
    val scope = rememberCoroutineScope()
    val sources = AnimeSourceRegistry.sources
    val account = remember { AniListRepository(context) }
    var connected by remember { mutableStateOf(account.currentAccessToken() != null) }
    var viewerName by remember { mutableStateOf<String?>(null) }
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
        viewerName = if (connected) {
            runCatching { withContext(Dispatchers.IO) { account.getViewer().name } }.getOrNull()
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
    val push = remember { AniListLibraryPush(context) }
    var pushPlan by remember { mutableStateOf<AniListPushPlan?>(null) }
    var pushResult by remember { mutableStateOf<AniListPushResult?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var report by remember { mutableStateOf<AniListSyncReport?>(sync.lastReport()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUnmatched by remember { mutableStateOf(false) }

    val privateText = stringResource(R.string.anilist_sync_private)
    val missingText = stringResource(R.string.anilist_sync_user_missing)
    val offlineText = stringResource(R.string.home_error_no_internet)
    val failedText = stringResource(R.string.anilist_sync_failed)
    val nsfwSourceText = stringResource(R.string.anilist_sync_nsfw_source)

    fun describe(error: Throwable): String? = when (error) {
        is AniListPrivateListException -> privateText
        is AniListUserNotFoundException -> missingText
        is NoInternetConnectionException -> offlineText
        is AniListNsfwSourceException -> nsfwSourceText
        else -> "$failedText: ${error.message.orEmpty()}"
    }

    pushPlan?.let { plan ->
        PushPreviewDialog(
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

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(stringResource(R.string.settings_anilist_sync)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.anilist_sync_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                if (useAccount && account.isConfigured) {
                    if (connected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.anilist_sync_connected_as, viewerName.orEmpty()),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(enabled = !running, onClick = { account.disconnect(); connected = false }) {
                                Text(stringResource(R.string.anilist_sync_sign_out))
                            }
                        }
                    } else {
                        Button(
                            enabled = !running,
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
                            modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = stringResource(R.string.anilist_sync_source),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sources.isEmpty()) {
                    Text(stringResource(R.string.settings_sources_empty))
                } else {
                    var sourceMenuOpen by remember { mutableStateOf(false) }
                    val chosen = sources.firstOrNull { it.id.value == sourceId } ?: sources.first()
                    ExposedDropdownMenuBox(
                        expanded = sourceMenuOpen,
                        onExpandedChange = { if (!running) sourceMenuOpen = it },
                    ) {
                        OutlinedTextField(
                            value = chosen.name,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !running,
                            singleLine = true,
                            leadingIcon = { SourceIcon(chosen) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceMenuOpen) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                            sources.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.name) },
                                    leadingIcon = { SourceIcon(source) },
                                    onClick = {
                                        sourceId = source.id.value
                                        sourceMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                if (useAccount && connected) {
                    OutlinedButton(
                        enabled = !running && sourceId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            sync.sourceId = sourceId
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
                    ) { Text(stringResource(R.string.anilist_push_button)) }
                }
                pushResult?.let { result ->
                    Text(
                        text = stringResource(R.string.anilist_push_result, result.sent, result.failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (running) {
                    Text(
                        text = stringResource(R.string.anilist_sync_running, progress.first, progress.second),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                report?.takeIf { !running }?.let { last ->
                    Text(
                        text = stringResource(
                            R.string.anilist_sync_result,
                            last.added,
                            last.updated,
                            last.unmatched.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
        },
        confirmButton = {
            Button(
                enabled = !running && sourceId.isNotBlank() &&
                    (if (useAccount && account.isConfigured) connected else userName.isNotBlank()),
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
            ) { Text(stringResource(R.string.anilist_sync_start)) }
        },
        dismissButton = {
            TextButton(enabled = !running, onClick = onDismiss) { Text(stringResource(R.string.anilist_sync_close)) }
        },
    )
}

@Composable
private fun PushPreviewDialog(
    plan: AniListPushPlan,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.anilist_push_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (plan.items.isEmpty()) {
                        stringResource(R.string.anilist_push_nothing)
                    } else {
                        stringResource(R.string.anilist_push_summary, plan.items.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                plan.items.forEach { item ->
                    Column {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        val details = buildList {
                            item.statusCategory?.let { add(stringResource(it.labelResId)) }
                            item.progress?.let { add(stringResource(R.string.anilist_push_episode, it)) }
                            if (item.favourite) add("\u2605")
                        }.joinToString(" \u00b7 ")
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (plan.skipped.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.anilist_push_skipped, plan.skipped.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    plan.skipped.forEach { name ->
                        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            if (plan.items.isNotEmpty()) {
                Button(onClick = onSend) { Text(stringResource(R.string.anilist_push_send)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.anilist_sync_close)) } },
    )
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
