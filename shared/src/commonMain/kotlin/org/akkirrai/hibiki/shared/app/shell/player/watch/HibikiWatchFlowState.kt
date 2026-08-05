package org.akkirrai.hibiki.shared.app.shell.player.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.model.TitleWatchState

internal class HibikiWatchFlowState {
    var detailsAnime by mutableStateOf<Anime?>(null)
    var detailsResumeState by mutableStateOf<TitleWatchState?>(null)
    var watchLoadGeneration by mutableStateOf(0)
    var forceWatchSourcesRefresh by mutableStateOf(false)
    var episodesLoadGeneration by mutableStateOf(0)
}

@Composable
internal fun rememberHibikiWatchFlowState(): HibikiWatchFlowState = remember {
    HibikiWatchFlowState()
}
