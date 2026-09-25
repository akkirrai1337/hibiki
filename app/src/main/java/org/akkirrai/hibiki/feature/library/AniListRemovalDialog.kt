package org.akkirrai.hibiki.feature.library

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.anilist.AniListLibraryPush
import org.akkirrai.hibiki.core.anilist.AniListLibrarySync
import org.akkirrai.hibiki.core.anilist.AniListRemovalMode

private val removalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Asked before removing a title that is synced with AniList: only from this library (its AniList entry
 * stays), or from AniList too. "Don't ask again" remembers the choice; Settings > AniList sync undoes it.
 */
@Composable
internal fun AniListRemovalDialog(
    onChoose: (everywhere: Boolean, remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var remember by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.anilist_removal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.anilist_removal_message), style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { remember = !remember },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it })
                    Text(stringResource(R.string.anilist_removal_dont_ask), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChoose(false, remember) }) { Text(stringResource(R.string.anilist_removal_local)) }
                Button(onClick = { onChoose(true, remember) }) { Text(stringResource(R.string.anilist_removal_everywhere)) }
            }
        },
    )
}

/** Whether removing this title needs the question: synced, and no standing answer given. Null means ask. */
internal fun standingRemovalChoice(context: Context): Boolean? = when (AniListLibrarySync(context).removalMode) {
    AniListRemovalMode.LOCAL -> false
    AniListRemovalMode.EVERYWHERE -> true
    else -> null
}

/**
 * Records what removing [titleId] means for the sync; call it after the title has left the library.
 * "Everywhere" deletes the AniList entry in the background and says how it went.
 */
internal fun applyAniListRemoval(context: Context, titleId: String, everywhere: Boolean, remember: Boolean = false) {
    val appContext = context.applicationContext
    val sync = AniListLibrarySync(appContext)
    if (remember) {
        sync.removalMode = if (everywhere) AniListRemovalMode.EVERYWHERE else AniListRemovalMode.LOCAL
    }
    sync.exclude(titleId)
    if (!everywhere) {
        sync.keepOnAniList(titleId)
        return
    }
    sync.unkeepOnAniList(titleId)
    removalScope.launch {
        val removed = runCatching { AniListLibraryPush(appContext).removeEverywhere(titleId) }.getOrDefault(false)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                appContext,
                if (removed) R.string.anilist_removal_done else R.string.anilist_removal_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
