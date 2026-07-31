package org.akkirrai.hibiki.feature.details

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.TitleWatchState
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.shared.details.AppDetailsScreen
import org.akkirrai.hibiki.shared.details.resolveDetailsPlaybackAvailability
import org.akkirrai.hibiki.shared.player.resolveResumeWatchState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

@Composable
fun SharedAndroidDetailsScreen(
    anime: Anime,
    onBackClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    onOpenSources: (Anime) -> Unit,
    onResumePlayback: (TitleWatchState) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
    val preferences = org.akkirrai.hibiki.app.settings.LocalAppPreferencesState.current
    val searchRepository = remember(dependencies) { dependencies.animeSearchRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val resumeFrameRepository = remember(dependencies) { dependencies.resumeFrameRepository() }
    val offlineRepository = remember(dependencies) { dependencies.offlineTitleMetadataRepository() }
    val detailsStateKey = remember(anime.id, preferences.animeSource) {
        "${preferences.animeSource.value}:${anime.id}"
    }
    var currentAnime by remember(anime.id, preferences.animeSource) { mutableStateOf(anime) }
    var isLoading by remember(anime.id, preferences.animeSource) { mutableStateOf(true) }
    var detailsError by remember(anime.id, preferences.animeSource) { mutableStateOf<String?>(null) }
    var libraryCategory by remember(anime.id) { mutableStateOf(libraryRepository.getLibraryCategory(anime.id)) }
    var resumeState by remember(anime.id) { mutableStateOf<TitleWatchState?>(null) }
    val resumeFrame = remember(anime.id, resumeState?.updatedAt) {
        resumeFrameRepository.getFrame(anime.id)
    }
    val titleColorPreferences = remember(context) {
        context.getSharedPreferences(TITLE_COLOR_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    val initialTitleSeedColor = remember(detailsStateKey) {
        titleColorPreferences.getInt(detailsStateKey, 0)
            .takeIf { titleColorPreferences.contains(detailsStateKey) }
            ?.toLong()
    }

    DisposableEffect(searchRepository) {
        onDispose { searchRepository.close() }
    }

    LaunchedEffect(anime.id, preferences.animeSource) {
        isLoading = true
        detailsError = null
        withContext(Dispatchers.IO) {
            offlineRepository.get(anime.id)
        }?.let { currentAnime = it }
        runCatching {
            withContext(Dispatchers.IO) { searchRepository.getDetails(anime.id, currentAnime) }
        }.onSuccess {
            currentAnime = it
            withContext(Dispatchers.IO) { offlineRepository.save(it) }
            isLoading = false
        }.onFailure {
            detailsError = it.message
            isLoading = false
        }
    }

    LaunchedEffect(anime.id) {
        resumeState = withContext(Dispatchers.IO) {
            resolveResumeWatchState(watchStateRepository.getEpisodeProgress(anime.id))
        }
    }

    val sourceDescriptor = remember(currentAnime.id, preferences.animeSource) {
        AnimeSourceRegistry.descriptorForTitle(currentAnime.id, preferences.animeSource)
    }
    val canWatch = resolveDetailsPlaybackAvailability(
        supportsPlayback = sourceDescriptor.supportsPlayback,
        status = currentAnime.status,
        episodesLabel = currentAnime.episodesLabel,
    )

    AppDetailsScreen(
        anime = currentAnime,
        onBackClick = onBackClick,
        onRelatedAnimeClick = onRelatedAnimeClick,
        libraryRepository = libraryRepository,
        resumeState = resumeState,
        resumeFrameContent = resumeFrame?.let { frame ->
            { frameModifier ->
                AsyncImage(
                    model = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = frameModifier,
                )
            }
        },
        onResumeClick = onResumePlayback,
        onTrailerClick = { currentAnime.trailer?.playbackUrl?.let(uriHandler::openUri) },
        canWatch = canWatch,
        onWatchClick = { onOpenSources(currentAnime) },
        initialTitleSeedColor = initialTitleSeedColor,
        onTitleSeedColorChange = { color ->
            titleColorPreferences.edit().putInt(detailsStateKey, color.toInt()).apply()
        },
        contentPadding = contentPadding,
        initialLibraryCategory = libraryCategory,
        onLibraryCategoryChange = { category ->
            libraryCategory = category
        },
        isDetailsLoading = isLoading,
        detailsError = detailsError,
        modifier = modifier,
    )
}

private const val TITLE_COLOR_PREFERENCES_NAME = "title_color_cache"
