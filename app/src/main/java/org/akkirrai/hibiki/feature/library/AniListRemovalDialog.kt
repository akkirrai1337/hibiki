package org.akkirrai.hibiki.feature.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R

/** Asked before removing a title that came from AniList: it stays there, and the import will not bring it back. */
@Composable
internal fun AniListRemovalDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.anilist_removal_title)) },
        text = { Text(stringResource(R.string.anilist_removal_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.anilist_removal_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
