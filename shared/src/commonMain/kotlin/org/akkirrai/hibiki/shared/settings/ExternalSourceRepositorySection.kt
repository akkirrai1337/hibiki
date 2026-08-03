package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState
import org.akkirrai.beakokit.api.SourceId

data class ExternalSourceRepositorySectionLabels(
    val title: String,
    val urlLabel: String,
    val urlHint: String,
    val addLabel: String,
    val refreshLabel: String,
    val removeLabel: String,
    val busyLabel: String,
    val packagesTitle: String,
    val installLabel: String,
    val updateLabel: String,
    val installedLabel: String,
    val rollbackLabel: String,
)

/** Settings component for repository endpoints; host decides when to expose it. */
@Composable
fun ExternalSourceRepositorySection(
    state: ExternalSourceRepositoryUiState,
    labels: ExternalSourceRepositorySectionLabels,
    onAddRepository: (String) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onRefresh: () -> Unit,
    onInstallPackage: (SourceId) -> Unit = {},
    onRollbackPackage: (SourceId) -> Unit = {},
    modifier: Modifier = Modifier,
    errorMessage: (Throwable) -> String = { it.message.orEmpty() },
) {
    var url by remember { mutableStateOf("") }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = labels.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(labels.urlLabel) },
                supportingText = { Text(labels.urlHint) },
                singleLine = true,
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onAddRepository(url.trim())
                    url = ""
                },
                enabled = !state.isBusy && url.isNotBlank(),
            ) {
                Text(if (state.isBusy) labels.busyLabel else labels.addLabel)
            }
        }
        state.error?.let { error ->
            Text(
                text = errorMessage(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.repositories.forEach { repository ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = repository.url,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = { onRemoveRepository(repository.url) },
                    enabled = !state.isBusy,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = labels.removeLabel,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (state.packages.isNotEmpty()) {
            Text(text = labels.packagesTitle, style = MaterialTheme.typography.titleSmall)
            state.packages.forEach { packageStatus ->
                val title = packageStatus.availableManifest.sourceInfo?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: packageStatus.sourceId.value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$title · ${packageStatus.availableManifest.packageVersion}",
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = when {
                            packageStatus.activePackage == null -> labels.installLabel
                            packageStatus.updateAvailable -> labels.updateLabel
                            else -> labels.installedLabel
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (packageStatus.activePackage == null || packageStatus.updateAvailable) {
                        Button(
                            onClick = { onInstallPackage(packageStatus.sourceId) },
                            enabled = !state.isBusy,
                        ) {
                            Text(
                                if (packageStatus.activePackage == null) {
                                    labels.installLabel
                                } else {
                                    labels.updateLabel
                                },
                            )
                        }
                    }
                    if (packageStatus.rollbackAvailable) {
                        Button(
                            onClick = { onRollbackPackage(packageStatus.sourceId) },
                            enabled = !state.isBusy,
                        ) {
                            Text(labels.rollbackLabel)
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onRefresh,
            enabled = !state.isBusy,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = labels.refreshLabel,
            )
        }
    }
}
