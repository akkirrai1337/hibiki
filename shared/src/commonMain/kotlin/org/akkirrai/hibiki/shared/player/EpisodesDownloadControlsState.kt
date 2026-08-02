package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun rememberEpisodesDownloadControlsVisible(
    sourceId: String,
    downloadMode: Boolean,
): MutableState<Boolean> = remember(sourceId, downloadMode) {
    mutableStateOf(downloadMode)
}
