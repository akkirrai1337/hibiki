package org.akkirrai.hibiki.app.shell.player.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import org.akkirrai.hibiki.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.player.model.PlaybackRoute
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.EpisodesPresenter
import org.akkirrai.hibiki.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.player.WatchDataRepository
import org.akkirrai.hibiki.player.WatchSourcesPresenter
import org.akkirrai.hibiki.player.withLoadedSources
import org.akkirrai.hibiki.player.withWatchSourcesError
import org.akkirrai.hibiki.player.errorEpisodesState
import org.akkirrai.hibiki.player.initialEpisodesState
import org.akkirrai.hibiki.player.initialWatchSourcesState
import org.akkirrai.hibiki.player.loadedEpisodesState
import org.akkirrai.hibiki.player.mergeWatchSources

@Composable
internal fun HibikiWatchDataEffects(
    selectedAnime: Anime?,
    detailsLoading: Boolean,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository?,
    onDetailsAnimeChanged: (Anime?) -> Unit,
    watchAnime: Anime?,
    watchRepository: WatchDataRepository?,
    offlineWatchDataRepository: OfflineWatchDataRepository?,
    watchPresenter: WatchSourcesPresenter,
    watchLoadGeneration: Int,
    forceWatchSourcesRefresh: Boolean,
    activePlaybackRoute: PlaybackRoute?,
    onPlaybackRouteChanged: (PlaybackRoute) -> Unit,
    selectedWatchSource: WatchSource?,
    episodesPresenter: EpisodesPresenter,
    episodesLoadGeneration: Int,
    onSingleWatchSourceLoaded: (WatchSource) -> Unit,
) {
    // Re-entering the same anime's sources / same source's episodes after they were already
    // loaded once shouldn't blank the screen back to Loading while the (cache-hit, near-instant)
    // refetch resolves -- that blank-then-refill is what reads as "flickers like a reload".
    // These survive the generation bumps and the eager blank reset on back-navigation below,
    // since they're independent of the presenter state those touch.
    var lastLoadedWatchAnimeId by remember { mutableStateOf<String?>(null) }
    var lastLoadedEpisodesSourceId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(offlineTitleMetadataRepository, selectedAnime?.id) {
        onDetailsAnimeChanged(selectedAnime?.let { anime ->
            offlineTitleMetadataRepository?.get(anime.id) ?: anime
        })
    }

    LaunchedEffect(offlineTitleMetadataRepository, selectedAnime, detailsLoading) {
        if (selectedAnime != null && !detailsLoading) {
            offlineTitleMetadataRepository?.save(selectedAnime)
            onDetailsAnimeChanged(selectedAnime)
        }
    }

    LaunchedEffect(watchRepository, watchAnime?.id, watchLoadGeneration) {
        val repositoryForWatch = watchRepository ?: return@LaunchedEffect
        val anime = watchAnime ?: return@LaunchedEffect
        val alreadyLoaded = !forceWatchSourcesRefresh && lastLoadedWatchAnimeId == anime.id
        if (!alreadyLoaded) {
            watchPresenter.setState(
                initialWatchSourcesState(
                    cachedSources = null,
                    offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty(),
                    forceRefresh = true,
                ),
            )
        }
        try {
            val sourcesForWatch = if (forceWatchSourcesRefresh) {
                repositoryForWatch.refreshSources(anime.id)
            } else {
                repositoryForWatch.loadSources(anime.id)
            }
            val offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty()
            watchPresenter.update { state ->
                state.withLoadedSources(
                    sources = sourcesForWatch,
                    offlineSources = offlineSources,
                    isLoading = false,
                )
            }
            lastLoadedWatchAnimeId = anime.id
            // Nothing to pick between -- skip straight to that source's episodes instead of
            // making the user tap through a list with exactly one row.
            mergeWatchSources(sourcesForWatch, offlineSources).singleOrNull()?.let(onSingleWatchSourceLoaded)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            watchPresenter.update {
                it.withWatchSourcesError(error.message ?: "Unable to load watch sources")
            }
        }
    }

    LaunchedEffect(
        watchRepository,
        activePlaybackRoute?.context?.sourceId,
        activePlaybackRoute?.context?.episodeId,
    ) {
        val repositoryForPlayback = watchRepository ?: return@LaunchedEffect
        val route = activePlaybackRoute ?: return@LaunchedEffect
        val options = try {
            repositoryForPlayback.getPlaybackSettingsOptions(
                sourceId = route.context.sourceId,
                episodeId = route.context.episodeId,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return@LaunchedEffect
        }
        if (activePlaybackRoute.context.episodeId == route.context.episodeId) {
            onPlaybackRouteChanged(route.copy(context = route.context.copy(settingsOptions = options)))
        }
    }

    LaunchedEffect(watchRepository, selectedWatchSource?.sourceId, episodesLoadGeneration) {
        val repositoryForWatch = watchRepository ?: return@LaunchedEffect
        val source = selectedWatchSource ?: return@LaunchedEffect
        val offlineEpisodes = offlineWatchDataRepository?.getOfflineEpisodes(source.sourceId).orEmpty()
        if (lastLoadedEpisodesSourceId != source.sourceId) {
            episodesPresenter.setState(initialEpisodesState(offlineEpisodes))
        }
        try {
            val episodes = repositoryForWatch.getEpisodes(source.sourceId)
            episodesPresenter.setState(loadedEpisodesState(episodes, offlineEpisodes))
            lastLoadedEpisodesSourceId = source.sourceId
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            episodesPresenter.setState(
                errorEpisodesState(error.message ?: "Unable to load episodes", offlineEpisodes),
            )
        }
    }
}
