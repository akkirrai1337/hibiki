package org.akkirrai.hibiki.shared.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppUpdateDialogContent(
    versionLabel: String,
    sizeLabel: String,
    notes: String,
    downloadProgress: Float?,
    iconContent: @Composable () -> Unit,
    title: String,
    downloadingLabel: @Composable (Int) -> String,
    whatsNewLabel: String,
    actionLabel: String,
    laterLabel: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    val isDownloading = downloadProgress != null
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onLater() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    iconContent()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppUpdateInfoPill(versionLabel = versionLabel, size = sizeLabel)
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress!!.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(downloadingLabel((downloadProgress!! * 100).toInt()))
                }
                if (notes.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = whatsNewLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = notes.trim(),
                            modifier = Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !isDownloading) {
                Text(actionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !isDownloading) {
                Text(laterLabel)
            }
        },
    )
}
