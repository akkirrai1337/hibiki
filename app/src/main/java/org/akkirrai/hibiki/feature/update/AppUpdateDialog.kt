package org.akkirrai.hibiki.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.update.AppUpdate
import org.akkirrai.hibiki.shared.update.AppUpdateDialogContent
import org.akkirrai.hibiki.shared.update.formatDownloadSize

@Composable
fun AppUpdateDialog(
    update: AppUpdate,
    downloadProgress: Float?,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    AppUpdateDialogContent(
        versionLabel = stringResource(R.string.update_version_label, update.version),
        sizeLabel = formatDownloadSize(update.apkSizeBytes),
        notes = update.notes,
        downloadProgress = downloadProgress,
        title = stringResource(R.string.update_available_title),
        downloadingLabel = { progress ->
            stringResource(R.string.update_downloading, progress)
        },
        whatsNewLabel = stringResource(R.string.update_whats_new),
        actionLabel = stringResource(
            if (update.isDownloaded) R.string.update_action_install else R.string.update_action_download,
        ),
        laterLabel = stringResource(R.string.update_action_later),
        onUpdate = onUpdate,
        onLater = onLater,
    )
}
