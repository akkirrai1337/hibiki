package org.akkirrai.hibiki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.anilist.AniListLibrarySync
import org.akkirrai.hibiki.core.anilist.AniListPrivateListException
import org.akkirrai.hibiki.core.anilist.AniListRepository
import org.akkirrai.hibiki.core.anilist.AniListSyncReport
import org.akkirrai.hibiki.core.anilist.AniListUserNotFoundException
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

/** One-way AniList to Hibiki library sync: who to read, which source to look the titles up on, and the run itself. */
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
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var report by remember { mutableStateOf<AniListSyncReport?>(sync.lastReport()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUnmatched by remember { mutableStateOf(false) }

    val privateText = stringResource(R.string.anilist_sync_private)
    val missingText = stringResource(R.string.anilist_sync_user_missing)
    val offlineText = stringResource(R.string.home_error_no_internet)
    val failedText = stringResource(R.string.anilist_sync_failed)

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
                    if (account.isConfigured) {
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
                        Text(
                            text = stringResource(R.string.anilist_sync_or_public),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    Column(Modifier.selectableGroup()) {
                        sources.forEach { source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = source.id.value == sourceId,
                                        enabled = !running,
                                        role = Role.RadioButton,
                                        onClick = { sourceId = source.id.value },
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = source.id.value == sourceId, onClick = null)
                                Text(source.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
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
                enabled = !running && (connected || userName.isNotBlank()) && sourceId.isNotBlank(),
                onClick = {
                    sync.userName = userName
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
                        } catch (error: AniListPrivateListException) {
                            errorMessage = privateText
                        } catch (error: AniListUserNotFoundException) {
                            errorMessage = missingText
                        } catch (error: NoInternetConnectionException) {
                            errorMessage = offlineText
                        } catch (error: Throwable) {
                            errorMessage = "$failedText: ${error.message.orEmpty()}"
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
