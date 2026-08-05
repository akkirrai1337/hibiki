package org.akkirrai.hibiki.shared.app.shell.player.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.akkirrai.hibiki.shared.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.model.PlaybackRoute
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.player.withLoadedSources
import org.akkirrai.hibiki.shared.player.withWatchSourcesError
import org.akkirrai.hibiki.shared.player.errorEpisodesState
import org.akkirrai.hibiki.shared.player.initialEpisodesState
import org.akkirrai.hibiki.shared.player.initialWatchSourcesState
import org.akkirrai.hibiki.shared.player.loadedEpisodesState
import org.akkirrai.hibiki.shared.player.resolveResumeWatchState
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository

@Composable
internal fun HibikiWatchDataEffects(
    selectedAnime: Anime?,
    detailsLoading: Boolean,
    progressRepository: PlaybackProgressRepository?,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository?,
    onDetailsAnimeChanged: (Anime?) -> Unit,
    onDetailsResumeStateChanged: (TitleWatchState?) -> Unit,
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
) {
    LaunchedEffect(progressRepository, selectedAnime?.id) {
        val animeId = selectedAnime?.id
        onDetailsResumeStateChanged(
            if (progressRepository != null && animeId != null) {
                resolveResumeWatchState(
                    progressRepository.getAllPlaybackProgress().filter { it.titleId == animeId },
                )
            } else null,
        )
    }

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
        watchPresenter.setState(
            initialWatchSourcesState(
                cachedSources = null,
                offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty(),
                forceRefresh = true,
            ),
        )
        runCatching {
            if (forceWatchSourcesRefresh) repositoryForWatch.refreshSources(anime.id)
            else repositoryForWatch.loadSources(anime.id)
        }.onSuccess { sourcesForWatch ->
            watchPresenter.update { state ->
                state.withLoadedSources(
                    sources = sourcesForWatch,
                    offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty(),
                    isLoading = false,
                )
            }
        }.onFailure { error ->
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
        val options = runCatching {
            repositoryForPlayback.getPlaybackSettingsOptions(
                sourceId = route.context.sourceId,
                episodeId = route.context.episodeId,
            )
        }.getOrNull() ?: return@LaunchedEffect
        if (activePlaybackRoute.context.episodeId == route.context.episodeId) {
            onPlaybackRouteChanged(route.copy(context = route.context.copy(settingsOptions = options)))
        }
    }

    LaunchedEffect(watchRepository, selectedWatchSource?.sourceId, episodesLoadGeneration) {
        val repositoryForWatch = watchRepository ?: return@LaunchedEffect
        val source = selectedWatchSource ?: return@LaunchedEffect
        val offlineEpisodes = offlineWatchDataRepository?.getOfflineEpisodes(source.sourceId).orEmpty()
        episodesPresenter.setState(initialEpisodesState(offlineEpisodes))
        runCatching { repositoryForWatch.getEpisodes(source.sourceId) }
            .onSuccess { episodes -> episodesPresenter.setState(loadedEpisodesState(episodes, offlineEpisodes)) }
            .onFailure { error ->
                episodesPresenter.setState(
                    errorEpisodesState(error.message ?: "Unable to load episodes", offlineEpisodes),
                )
            }
    }
}
