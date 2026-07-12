package org.akkirrai.hibiki.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.update.AppUpdate

@Composable
fun AppUpdateDialog(
    update: AppUpdate,
    downloadProgress: Float?,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    val isDownloading = downloadProgress != null
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onLater() },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.update_available_version, update.version, formatFileSize(update.apkSizeBytes)),
                    fontWeight = FontWeight.Medium,
                )
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.update_downloading, (downloadProgress * 100).toInt()))
                }
                if (update.notes.isNotBlank()) {
                    Text(
                        text = update.notes,
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !isDownloading) {
                Text(stringResource(R.string.update_action_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !isDownloading) {
                Text(stringResource(R.string.update_action_later))
            }
        },
    )
}

private fun formatFileSize(bytes: Long): String {
    val mib = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(java.util.Locale.getDefault(), mib)
}
