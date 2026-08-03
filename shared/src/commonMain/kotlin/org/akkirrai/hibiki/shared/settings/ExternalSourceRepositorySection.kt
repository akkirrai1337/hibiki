package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.beakokit.api.SourceErrorCode
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceRepositoryUrlException
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState
import org.akkirrai.hibiki.shared.source.repositoryDisplayName

data class ExternalSourceRepositorySectionLabels(
    val title: String,
    val urlLabel: String,
    val urlHint: String,
    val invalidUrlError: String,
    val operationFailedError: String,
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
) {
    var url by remember { mutableStateOf("") }
    var submittedUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.isBusy, state.error, state.repositories) {
        if (submittedUrl != null && !state.isBusy && state.error == null) {
            url = ""
            submittedUrl = null
        }
    }

    AppSettingsSection(title = labels.title, modifier = modifier) {
        AppSettingsVerticalItem(
            headerContent = {
                AppSettingsItemHeader(
                    iconContent = {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(SettingsItemIconSize),
                        )
                    },
                    title = labels.urlLabel,
                )
            },
            shape = RoundedCornerShape(SettingsItemOuterCornerRadius),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(labels.urlHint) },
                maxLines = 3,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    submittedUrl = url.trim()
                    onAddRepository(submittedUrl.orEmpty())
                },
                enabled = !state.isBusy && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isBusy) labels.busyLabel else labels.addLabel)
            }
            state.error?.let { error ->
                Text(
                    text = when {
                        error is SourceRepositoryUrlException ||
                            (error as? SourceException)?.code == SourceErrorCode.INVALID_REQUEST -> labels.invalidUrlError
                        else -> labels.operationFailedError
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.repositories.isNotEmpty()) {
            AppSettingsItems(count = state.repositories.size) { index, shape ->
                val repository = state.repositories[index]
                AppSettingsItemRow(
                    iconContent = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            modifier = Modifier.size(SettingsItemIconSize),
                        )
                    },
                    shape = shape,
                    onClick = {},
                    trailing = {
                        IconButton(
                            onClick = { onRemoveRepository(repository.url) },
                            enabled = !state.isBusy,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = labels.removeLabel,
                            )
                        }
                    },
                ) {
                    Text(
                        text = repositoryDisplayName(repository.url),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (state.packages.isNotEmpty()) {
            AppSettingsSection(title = labels.packagesTitle) {
                AppSettingsItems(count = state.packages.size) { index, shape ->
                    val packageStatus = state.packages[index]
                    val title = packageStatus.availableManifest.sourceInfo?.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: packageStatus.sourceId.value
                    val status = when {
                        packageStatus.activePackage == null -> labels.installLabel
                        packageStatus.updateAvailable -> labels.updateLabel
                        else -> labels.installedLabel
                    }
                    AppSettingsVerticalItem(
                        headerContent = {
                            AppSettingsItemHeader(
                                iconContent = {
                                    Icon(
                                        imageVector = if (packageStatus.updateAvailable) {
                                            Icons.Outlined.SystemUpdate
                                        } else {
                                            Icons.Outlined.CloudDownload
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(SettingsItemIconSize),
                                    )
                                },
                                title = "$title · ${packageStatus.availableManifest.packageVersion}",
                            )
                        },
                        shape = shape,
                    ) {
                        Text(text = status, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (packageStatus.activePackage == null || packageStatus.updateAvailable) {
                                Button(
                                    onClick = { onInstallPackage(packageStatus.sourceId) },
                                    enabled = !state.isBusy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (packageStatus.activePackage == null) labels.installLabel else labels.updateLabel)
                                }
                            }
                            if (packageStatus.rollbackAvailable) {
                                Button(
                                    onClick = { onRollbackPackage(packageStatus.sourceId) },
                                    enabled = !state.isBusy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(labels.rollbackLabel)
                                }
                            }
                        }
                    }
                }
            }
        }

        AppSettingsItems(count = 1) { _, shape ->
            AppSettingsIconActionItem(
                icon = Icons.Outlined.Refresh,
                title = labels.refreshLabel,
                shape = shape,
                onClick = onRefresh,
            )
        }
    }
}
