package org.akkirrai.hibiki.core.source.extension.repository

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

private sealed interface InstallState {
    data object Idle : InstallState
    data object Installing : InstallState
    data class Failed(val message: String) : InstallState
}

@Composable
fun InstallSourceExtensionDialog(
    androidContext: Context,
    repositoryClient: SourceRepositoryClient,
    installer: SourceExtensionInstaller,
    onDismiss: () -> Unit,
) {
    var entries by remember { mutableStateOf<List<SourceRepositoryEntry>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    val installStates = remember { mutableStateMapOf<String, InstallState>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val result = repositoryClient.fetchIndex()
        result.onSuccess { index ->
            entries = repositoryClient.availableEntries(androidContext, index)
            loadError = false
        }.onFailure {
            loadError = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appText(AppTextKey.InstallExtensionDialogTitle),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }

                when {
                    loadError -> Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(appText(AppTextKey.InstallExtensionLoadFailed))
                        TextButton(onClick = {
                            loadError = false
                            entries = null
                            scope.launch {
                                repositoryClient.fetchIndex()
                                    .onSuccess { index ->
                                        entries = repositoryClient.availableEntries(androidContext, index)
                                    }
                                    .onFailure { loadError = true }
                            }
                        }) {
                            Text(appText(AppTextKey.InstallExtensionRetry))
                        }
                    }
                    entries == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    entries.orEmpty().isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(appText(AppTextKey.InstallExtensionNoneAvailable))
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries.orEmpty(), key = { it.id }) { entry ->
                            SourceExtensionRow(
                                entry = entry,
                                installState = installStates[entry.id] ?: InstallState.Idle,
                                onInstall = {
                                    installStates[entry.id] = InstallState.Installing
                                    scope.launch {
                                        installer.downloadAndVerify(entry)
                                            .onSuccess { apkFile ->
                                                installStates[entry.id] = InstallState.Idle
                                                installer.requestInstall(apkFile)
                                            }
                                            .onFailure { throwable ->
                                                installStates[entry.id] =
                                                    InstallState.Failed(throwable.message.orEmpty())
                                            }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceExtensionRow(
    entry: SourceRepositoryEntry,
    installState: InstallState,
    onInstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = entry.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "v${entry.version}", style = MaterialTheme.typography.bodySmall)
                if (installState is InstallState.Failed) {
                    Text(
                        text = installState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when (installState) {
                InstallState.Installing -> CircularProgressIndicator(modifier = Modifier.height(24.dp))
                else -> Button(onClick = onInstall) {
                    Text(appText(AppTextKey.InstallExtensionInstallButton))
                }
            }
        }
    }
}
