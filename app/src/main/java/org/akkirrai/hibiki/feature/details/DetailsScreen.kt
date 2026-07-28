package org.akkirrai.hibiki.feature.details

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.toBitmap
import coil3.imageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.shared.design.AppMotion
import org.akkirrai.hibiki.core.design.component.rememberDeviceScreenTopCornerShape
import org.akkirrai.hibiki.shared.design.component.AppModalBottomSheet as SharedModalBottomSheet
import org.akkirrai.hibiki.shared.player.formatPlaybackPosition
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.details.isNullOrZero
import org.akkirrai.hibiki.shared.details.resolveAnimeDescription
import org.akkirrai.hibiki.shared.details.resolveDetailsHeroRatings
import org.akkirrai.hibiki.shared.model.toAnime
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.hibiki.core.model.TitleWatchState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.source.WatchStateRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.shared.details.DetailsUiState
import org.akkirrai.hibiki.shared.details.DetailsStatusBarScrim
import org.akkirrai.hibiki.shared.details.DetailsHeroInfo
import org.akkirrai.hibiki.shared.details.AppDetailsHeroTextContent
import org.akkirrai.hibiki.shared.details.AppDetailsHeroPlaybackActions
import org.akkirrai.hibiki.shared.details.AppDetailsHeroMedia
import org.akkirrai.hibiki.shared.details.AppDetailsPosterPreviewOverlay
import org.akkirrai.hibiki.shared.details.AppDetailsHeroContent
import org.akkirrai.hibiki.shared.details.AppDetailsHeroOverlayBackButton
import org.akkirrai.hibiki.shared.details.AppDetailsImagePlaceholder
import org.akkirrai.hibiki.shared.library.AppLibraryCategorySheet
import org.akkirrai.hibiki.shared.details.AppDetailsTitleSheetContent
import org.akkirrai.hibiki.shared.details.AppDetailsTitleSheetDragHandle
import org.akkirrai.hibiki.shared.details.DetailsNextEpisodeChip
import org.akkirrai.hibiki.shared.details.DetailsInformationIcon
import org.akkirrai.hibiki.shared.details.DetailsHeroRatingsLine
import org.akkirrai.hibiki.shared.details.AppDetailsContentList
import org.akkirrai.hibiki.shared.details.DetailsGenresSection
import org.akkirrai.hibiki.shared.details.DetailsContentBottomPadding
import org.akkirrai.hibiki.shared.details.DetailsContentHorizontalPadding
import org.akkirrai.hibiki.shared.details.DetailsInformationHorizontalPadding
import org.akkirrai.hibiki.shared.details.resolveDetailsHeroInfo
import org.akkirrai.hibiki.shared.details.isAnnouncementStatus
import org.akkirrai.hibiki.shared.details.isOngoingStatus
import org.akkirrai.hibiki.shared.details.formatRelatedAnimeMetadata
import org.akkirrai.hibiki.shared.details.extractNextEpisodeNumber
import org.akkirrai.hibiki.shared.details.toAbsoluteImageUrl
import org.akkirrai.hibiki.shared.details.rememberNextEpisodeEta
import org.akkirrai.hibiki.shared.details.SourceMaterialLabels
import org.akkirrai.hibiki.shared.details.resolveSourceMaterialLabel
import org.akkirrai.hibiki.shared.details.resolveDetailsHeroMediaData
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    anime: Anime,
    onBackClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    onOpenSources: (Anime) -> Unit,
    onResumePlayback: (TitleWatchState) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = LocalAppPreferencesState.current
    val selectedAnimeSource = preferences.animeSource
    val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val screenScope = rememberCoroutineScope()
    val detailsStateKey = remember(anime.id, selectedAnimeSource) { "${selectedAnimeSource.value}:${anime.id}" }
    val savedScreenState = remember(detailsStateKey) { detailsScreenStateCache[detailsStateKey] }
    val searchRepository = remember(dependencies) { dependencies.animeSearchRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val offlineTitleMetadataRepository = remember(dependencies) { dependencies.offlineTitleMetadataRepository() }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val resumeFrameRepository = remember(dependencies) { dependencies.resumeFrameRepository() }
    var currentAnime by remember(detailsStateKey) { mutableStateOf(savedScreenState?.anime ?: anime) }
    var titleSeedColor by remember(detailsStateKey) {
        mutableStateOf(
            titleSeedColorCache[detailsStateKey]
                ?: readStoredTitleSeedColor(context, detailsStateKey)
        )
    }
    var libraryCategory by remember(anime.id) {
        mutableStateOf(libraryRepository.getLibraryCategory(anime.id))
    }
    var isLibrarySheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isPosterPreviewOpen by remember(anime.id) { mutableStateOf(false) }
    var isTitleDetailsSheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isScreenTransitionSettled by remember(anime.id) { mutableStateOf(false) }
    var resumeState by remember(anime.id) { mutableStateOf<TitleWatchState?>(null) }
    var resumeFrame by remember(anime.id) { mutableStateOf<File?>(null) }
    val listState = remember(anime.id) {
        LazyListState(
            firstVisibleItemIndex = savedScreenState?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = savedScreenState?.firstVisibleItemScrollOffset ?: 0,
        )
    }
    val localizedEpisodeWord = stringResource(R.string.details_episode_label)
    val currentAnimeState by rememberUpdatedState(currentAnime)
    val screenTransitionSettledState by rememberUpdatedState(isScreenTransitionSettled)

    suspend fun refreshWatchStateSnapshot() {
        val snapshot = withContext(Dispatchers.IO) {
            DetailsWatchSnapshot(
                libraryCategory = libraryRepository.getLibraryCategory(anime.id),
                resumeState = findResumeWatchState(watchStateRepository, anime.id),
                resumeFrame = resumeFrameRepository.getFrame(anime.id),
            )
        }
        libraryCategory = snapshot.libraryCategory
        resumeState = snapshot.resumeState
        resumeFrame = snapshot.resumeFrame
    }

    DisposableEffect(searchRepository) {
        onDispose {
            searchRepository.close()
        }
    }

    DisposableEffect(detailsStateKey, listState) {
        onDispose {
            detailsScreenStateCache[detailsStateKey] = DetailsScreenSavedState(
                anime = currentAnimeState,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(anime.id, selectedAnimeSource) {
        withContext(Dispatchers.IO) {
            offlineTitleMetadataRepository.get(anime.id)
        }?.let { cachedAnime ->
            currentAnime = cachedAnime
        }
        runCatching { searchRepository.getDetails(anime.id, currentAnime) }
            .onSuccess {
                currentAnime = it
                withContext(Dispatchers.IO) {
                    offlineTitleMetadataRepository.save(it)
                }
            }
    }

    LaunchedEffect(anime.id) {
        refreshWatchStateSnapshot()
    }

    LaunchedEffect(anime.id) {
        delay(AppMotion.ScreenTransitionDurationMillis.toLong())
        isScreenTransitionSettled = true
    }

    LaunchedEffect(
        anime.id,
        currentAnime.posterUrl,
        currentAnime.posterFallbackUrl,
        currentAnime.screenshots,
    ) {
        if (titleSeedColor == null) {
            delay(80)
        }
        if (titleSeedColor == null) {
            extractTitleSeedColor(
                context = context,
                imageUrls = listOfNotNull(
                    currentAnime.posterUrl,
                    currentAnime.posterFallbackUrl,
                    currentAnime.screenshots.firstOrNull(),
                ),
            )?.let { extractedColor ->
                titleSeedColorCache[detailsStateKey] = extractedColor
                storeTitleSeedColor(context, detailsStateKey, extractedColor)
                titleSeedColor = extractedColor
            }
        }
    }

    DisposableEffect(lifecycleOwner, anime.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && screenTransitionSettledState) {
                screenScope.launch {
                    refreshWatchStateSnapshot()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val heroInfo = remember(currentAnime, localizedEpisodeWord) {
        resolveDetailsHeroInfo(currentAnime, localizedEpisodeWord)
    }
    val description = remember(currentAnime) {
        resolveAnimeDescription(currentAnime)
    }
    val sourceDescriptor = remember(currentAnime.id, selectedAnimeSource) {
        AnimeSourceRegistry.descriptorForTitle(currentAnime.id, selectedAnimeSource)
    }
    val nextEpisodeEta = rememberNextEpisodeEta(
        nextEpisodeAt = currentAnime.nextEpisodeAt,
        nowEpochSeconds = { System.currentTimeMillis() / 1_000L },
        daysHoursLabel = { days, hours -> stringResource(R.string.details_eta_days_hours, days, hours) },
        hoursMinutesSecondsLabel = { hours, minutes, seconds ->
            stringResource(R.string.details_eta_hours_minutes_seconds, hours, minutes, seconds)
        },
        minutesSecondsLabel = { minutes, seconds ->
            stringResource(R.string.details_eta_minutes_seconds, minutes, seconds)
        },
    )
        ?.takeIf { isOngoingStatus(heroInfo.status) }
    val nextEpisodeNumber = remember(currentAnime.episodesLabel) {
        extractNextEpisodeNumber(currentAnime.episodesLabel)
    }
    val uiModel = remember(
        currentAnime,
        heroInfo,
        description,
        sourceDescriptor.contentFeatures,
    ) {
        buildDetailsUiModel(
            anime = currentAnime,
            hero = heroInfo,
            description = description,
            contentFeatures = sourceDescriptor.contentFeatures,
        )
    }
    val canWatch = remember(selectedAnimeSource, currentAnime.episodesLabel, heroInfo.status) {
        sourceDescriptor.supportsPlayback &&
            !isAnnouncementStatus(heroInfo.status, currentAnime.episodesLabel)
    }
    val fallbackColorScheme = MaterialTheme.colorScheme
    val resolvedTitleSeedColor = titleSeedColor
    val titleColorScheme = if (resolvedTitleSeedColor == null) {
        fallbackColorScheme
    } else {
        rememberDynamicColorScheme(
            seedColor = Color(resolvedTitleSeedColor),
            isDark = fallbackColorScheme.background.luminance() < 0.5f,
            style = PaletteStyle.Vibrant,
        )
    }
    val detailsColorScheme = if (preferences.useAmoledTheme) {
        titleColorScheme.copy(
            background = fallbackColorScheme.background,
            onBackground = fallbackColorScheme.onBackground,
            surface = fallbackColorScheme.surface,
            onSurface = fallbackColorScheme.onSurface,
        )
    } else {
        titleColorScheme
    }
    MaterialTheme(colorScheme = detailsColorScheme) {
        Surface(
            modifier = modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppDetailsContentList(
                    state = listState,
                    bottomContentPadding = contentPadding.calculateBottomPadding(),
                    additionalBottomPadding = DetailsContentBottomPadding,
                ) {
            item {
                DetailHeroSection(
                    detailsState = DetailsUiState(
                        anime = uiModel.anime,
                        libraryCategory = libraryCategory,
                        resumeState = resumeState,
                    ),
                    heroInfo = uiModel.hero,
                    description = uiModel.description,
                    nextEpisodeEta = nextEpisodeEta,
                    nextEpisodeNumber = nextEpisodeNumber,
                    canWatch = canWatch,
                    resumeFrame = resumeFrame,
                    isTitleDetailsSheetOpen = isTitleDetailsSheetOpen,
                    listState = listState,
                    onPosterLoaded = { drawable ->
                        screenScope.launch(Dispatchers.Default) {
                            val extractedColor = extractTitleSeedColor(drawable)
                            if (extractedColor != null) {
                                withContext(Dispatchers.Main.immediate) {
                                    if (titleSeedColor == null) {
                                        titleSeedColorCache[detailsStateKey] = extractedColor
                                        storeTitleSeedColor(context, detailsStateKey, extractedColor)
                                        titleSeedColor = extractedColor
                                    }
                                }
                            }
                        }
                    },
                    onPosterClick = { isPosterPreviewOpen = true },
                    onTitleClick = { isTitleDetailsSheetOpen = true },
                    onLibraryClick = {
                        isLibrarySheetOpen = true
                    },
                    onPrimaryClick = { onOpenSources(currentAnime) },
                    onResumeClick = onResumePlayback,
                    onTrailerClick = {
                        currentAnime.trailer?.playbackUrl?.let(uriHandler::openUri)
                    },
                )
            }

            item {
                DetailContentCard(
                    anime = uiModel.anime,
                    heroInfo = uiModel.hero,
                    modifier = Modifier,
                )
            }

            if (uiModel.anime.genres.isNotEmpty()) {
                item {
                    DetailsGenresSection(
                        genres = uiModel.anime.genres,
                        title = stringResource(R.string.details_genres),
                        horizontalPadding = DetailsContentHorizontalPadding,
                    )
                }
            }

            itemsIndexed(
                items = uiModel.sections,
                key = { _, section -> section.key }
            ) { _, section ->
                when (section) {
                    is RelatedSection -> {
                        RelatedAnimeList(
                            items = section.items,
                            title = stringResource(R.string.details_related),
                            onAnimeClick = onRelatedAnimeClick,
                        )
                    }
                    is SimilarSection -> {
                        RelatedAnimeList(
                            items = section.items,
                            title = stringResource(R.string.details_similar),
                            onAnimeClick = onRelatedAnimeClick,
                        )
                    }
                }
            }
                }

                DetailsStatusBarScrim(
                    listState = listState,
                    modifier = Modifier.align(Alignment.TopStart),
                )

                HeroOverlayBackButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }

        if (isPosterPreviewOpen) {
            AppDetailsPosterPreviewOverlay(
                onDismissRequest = { isPosterPreviewOpen = false },
                backHandler = { onBack -> BackHandler(onBack = onBack) },
                posterContent = { posterModifier ->
                    AppPosterImage(
                        primaryUrl = currentAnime.posterUrl,
                        fallbackUrl = currentAnime.posterFallbackUrl,
                        contentDescription = currentAnime.title,
                        modifier = posterModifier,
                        contentScale = ContentScale.Fit,
                        placeholder = {
                            AppDetailsImagePlaceholder(modifier = Modifier.fillMaxSize())
                        },
                    )
                },
                backContent = { onDismiss ->
                    HeroOverlayBackButton(onClick = onDismiss)
                },
            )
        }

        if (isTitleDetailsSheetOpen) {
            val titleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            SharedModalBottomSheet(
                onDismissRequest = { isTitleDetailsSheetOpen = false },
                sheetState = titleSheetState,
                modifier = Modifier.fillMaxHeight(),
                shape = rememberDeviceScreenTopCornerShape(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                scrimColor = Color.Black.copy(alpha = 0.5f),
                dragHandleContent = { expanded ->
                    AppDetailsTitleSheetDragHandle(expanded = expanded)
                },
            ) {
                AppDetailsTitleSheetContent(
                    title = currentAnime.title,
                    description = description,
                )
            }
        }

        if (isLibrarySheetOpen) {
            val categoryLabels = LibraryCategory.entries.associateWith { category ->
                stringResource(category.labelResId)
            }
            AppLibraryCategorySheet(
                selectedCategory = libraryCategory,
                title = stringResource(R.string.library_add_title),
                subtitle = stringResource(R.string.library_add_subtitle),
                savedNote = stringResource(R.string.library_saved_note),
                removeAction = stringResource(R.string.library_remove_action),
                categoryLabels = categoryLabels,
                onCategoryClick = { category ->
                    libraryRepository.saveToLibrary(currentAnime, category)
                    libraryCategory = category
                    isLibrarySheetOpen = false
                },
                onRemoveClick = {
                    libraryRepository.removeFromLibrary(currentAnime.id)
                    libraryCategory = libraryRepository.getLibraryCategory(currentAnime.id)
                    isLibrarySheetOpen = false
                },
                onDismiss = { isLibrarySheetOpen = false },
            )
        }
    }
}

@Composable
private fun DetailHeroSection(
    detailsState: DetailsUiState,
    heroInfo: DetailsHeroInfo,
    description: String,
    nextEpisodeEta: String?,
    nextEpisodeNumber: Int?,
    canWatch: Boolean,
    resumeFrame: File?,
    isTitleDetailsSheetOpen: Boolean,
    listState: LazyListState,
    onPosterLoaded: (Drawable) -> Unit,
    onPosterClick: () -> Unit,
    onTitleClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onPrimaryClick: () -> Unit,
    onResumeClick: (TitleWatchState) -> Unit,
    onTrailerClick: () -> Unit,
) {
    val anime = detailsState.anime
    val libraryCategory = detailsState.libraryCategory
    val resumeState = detailsState.resumeState
    val isUserLibraryCategorySelected = libraryCategory != null && libraryCategory != LibraryCategory.Saved
    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    AppDetailsHeroContent(
        posterExpanded = isAtTop,
        isInLibrary = isUserLibraryCategorySelected,
        canWatch = canWatch,
        libraryLabel = stringResource(R.string.details_favorite),
        watchLabel = stringResource(R.string.details_watch),
        onPosterClick = onPosterClick,
        onLibraryClick = onLibraryClick,
        onPrimaryClick = onPrimaryClick,
        posterContent = {
            NetworkImage(
                imageUrl = detailsState.anime.posterUrl,
                fallbackUrl = detailsState.anime.posterFallbackUrl,
                contentDescription = detailsState.anime.title,
                onImageSuccess = onPosterLoaded,
            )
        },
        mediaContent = { mediaModifier ->
            DetailHeroMedia(
                detailsState = detailsState,
                resumeFrame = resumeFrame,
                onResumeClick = onResumeClick,
                onTrailerClick = onTrailerClick,
                modifier = mediaModifier,
            )
        },
        textContent = { textModifier ->
            AppDetailsHeroTextContent(
                title = detailsState.anime.title,
                description = description,
                backgroundColor = MaterialTheme.colorScheme.background,
                onTitleClick = onTitleClick,
                ratingsContent = if (detailsState.anime.ratings.isNotEmpty() || !detailsState.anime.viewCount.isNullOrZero()) {
                    {
                        resolveDetailsHeroRatings(
                            detailsState.anime.ratings,
                            detailsState.anime.viewCount,
                        )?.let { ratings ->
                            DetailsHeroRatingsLine(
                                rating = ratings.rating,
                                viewCount = ratings.viewCount,
                            )
                        }
                    }
                } else {
                    null
                },
                nextEpisodeContent = nextEpisodeEta?.let { eta ->
                    {
                        DetailsNextEpisodeChip(
                            text = if (nextEpisodeNumber != null) {
                                stringResource(R.string.details_next_episode_countdown_numbered, nextEpisodeNumber, eta)
                            } else {
                                stringResource(R.string.details_next_episode_countdown, eta)
                            },
                            icon = ImageVector.vectorResource(R.drawable.hourglass),
                        )
                    }
                },
                expandIconContent = {
                    val expandToCollapse = AnimatedImageVector.animatedVectorResource(R.drawable.expand_collapse_anim)
                    Icon(
                        painter = rememberAnimatedVectorPainter(
                            animatedImageVector = expandToCollapse,
                            atEnd = isTitleDetailsSheetOpen,
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = textModifier,
            )
        },
    )
}

@Composable
private fun DetailHeroMedia(
    detailsState: DetailsUiState,
    resumeFrame: File?,
    onResumeClick: (TitleWatchState) -> Unit,
    onTrailerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anime = detailsState.anime
    val resumeState = detailsState.resumeState
    val mediaData = resolveDetailsHeroMediaData(anime, resumeState)
    AppDetailsHeroMedia(
        imageContent = {
            NetworkImage(
                imageUrl = mediaData.trailer?.thumbnailUrl ?: anime.posterUrl,
                fallbackUrl = anime.posterUrl ?: anime.posterFallbackUrl,
                contentDescription = null,
            )
        },
        frameContent = if (resumeState != null && resumeFrame != null) {
            {
                ResumeFrameImage(
                    frame = resumeFrame,
                    version = resumeState.updatedAt,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            null
        },
        playbackContent = {
            AppDetailsHeroPlaybackActions(
            resumeTitle = resumeState?.let { stringResource(R.string.details_watch_continue) },
            resumeSubtitle = resumeState?.let {
                stringResource(
                    R.string.details_continue_episode_position,
                    formatEpisodeNumber(it.episodeNumber),
                    formatPlaybackPosition(it.positionMs),
                )
            },
            resumeProgress = mediaData.resumeProgress,
            onResumeClick = resumeState?.let { state -> { onResumeClick(state) } },
            trailerEnabled = mediaData.trailer != null,
            onTrailerClick = onTrailerClick,
            trailerContentDescription = stringResource(R.string.details_trailer),
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun ResumeFrameImage(
    frame: File,
    version: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(frame)
            .memoryCacheKey("${frame.absolutePath}:$version")
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {},
        error = {},
    )
}

@Composable
private fun DetailContentCard(
    anime: Anime,
    heroInfo: DetailsHeroInfo,
    modifier: Modifier = Modifier,
) {
    val sourceMaterial = resolveSourceMaterialLabel(
        sourceMaterial = anime.sourceMaterial,
        labels = SourceMaterialLabels(
            manga = stringResource(R.string.details_source_material_manga),
            manhwa = stringResource(R.string.details_source_material_manhwa),
            manhua = stringResource(R.string.details_source_material_manhua),
            lightNovel = stringResource(R.string.details_source_material_light_novel),
            webNovel = stringResource(R.string.details_source_material_web_novel),
            visualNovel = stringResource(R.string.details_source_material_visual_novel),
            game = stringResource(R.string.details_source_material_game),
            original = stringResource(R.string.details_source_material_original),
        ),
    )
    org.akkirrai.hibiki.shared.details.AppDetailsInformationContent(
        heroInfo = heroInfo,
        title = stringResource(R.string.details_information),
        emptyValue = stringResource(R.string.search_filters_not_selected),
        statusLabel = stringResource(R.string.details_status),
        episodesLabel = stringResource(R.string.details_episodes_released),
        typeLabel = stringResource(R.string.details_type),
        releaseDateLabel = stringResource(R.string.details_release_date),
        sourceMaterialLabel = stringResource(R.string.details_source_material),
        studioLabel = stringResource(R.string.details_studio),
        sourceMaterial = sourceMaterial,
        horizontalPadding = DetailsInformationHorizontalPadding,
        modifier = modifier,
    )
}

@Composable
private fun HeroOverlayBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppDetailsHeroOverlayBackButton(
        onClick = onClick,
        modifier = modifier,
        contentDescription = stringResource(R.string.cd_back),
    )
}

@Composable
private fun RelatedAnimeList(
    items: List<RelatedAnime>,
    title: String,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    org.akkirrai.hibiki.shared.details.AppDetailsRelatedAnimeList(
        items = items,
        title = title,
        announcementLabel = stringResource(R.string.anime_meta_announcement),
        horizontalPadding = DetailsContentHorizontalPadding,
        onItemClick = { related -> onAnimeClick(related.toAnime()) },
        poster = { related ->
            NetworkImage(
                imageUrl = related.posterUrl,
                fallbackUrl = related.posterFallbackUrl,
                contentDescription = related.title,
            )
        },
        modifier = modifier,
    )
}

private fun findResumeWatchState(
    repository: WatchStateRepository,
    titleId: String,
): TitleWatchState? {
    return org.akkirrai.hibiki.shared.player.resolveResumeWatchState(repository.getEpisodeProgress(titleId))
}

private suspend fun extractTitleSeedColor(
    context: Context,
    imageUrls: List<String>,
): Int? {
    for (rawUrl in imageUrls.distinct()) {
        val url = rawUrl.toAbsoluteImageUrl() ?: continue
        val result = runCatching {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(96)
                    .build(),
            )
        }.getOrNull() as? SuccessResult ?: continue
        val palette = withContext(Dispatchers.Default) {
            val bitmap = runCatching {
                result.image.toBitmap(width = 96, height = 96)
            }.getOrNull() ?: return@withContext null
            runCatching {
                Palette.from(bitmap)
                    .maximumColorCount(24)
                    .generate()
            }.getOrNull()
        } ?: continue
        return (
            palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
            )?.rgb
    }
    return null
}

private fun extractTitleSeedColor(drawable: Drawable): Int? {
    val bitmap = runCatching {
        drawable.toBitmap(width = 96, height = 96)
    }.getOrNull() ?: return null
    val palette = runCatching {
        Palette.from(bitmap)
            .maximumColorCount(24)
            .generate()
    }.getOrNull() ?: return null
    return (
        palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
        )?.rgb
}

private data class DetailsScreenSavedState(
    val anime: Anime,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private data class DetailsWatchSnapshot(
    val libraryCategory: LibraryCategory?,
    val resumeState: TitleWatchState?,
    val resumeFrame: File?,
)

private fun readStoredTitleSeedColor(context: Context, key: String): Int? {
    val preferences = context.getSharedPreferences(TITLE_COLOR_PREFERENCES_NAME, Context.MODE_PRIVATE)
    return preferences.getInt(key, 0).takeIf { preferences.contains(key) }
}

private fun storeTitleSeedColor(context: Context, key: String, color: Int) {
    context.getSharedPreferences(TITLE_COLOR_PREFERENCES_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(key, color)
        .apply()
}

private val detailsScreenStateCache = ConcurrentHashMap<String, DetailsScreenSavedState>()
private val titleSeedColorCache = ConcurrentHashMap<String, Int>()
private const val TITLE_COLOR_PREFERENCES_NAME = "title_color_cache"
private val DETAIL_SECTION_VISUAL_ALIGNMENT_OFFSET = 3.dp

@Composable
private fun NetworkImage(
    imageUrl: String?,
    fallbackUrl: String? = null,
    contentDescription: String?,
    onImageSuccess: ((Drawable) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    AppPosterImage(
        primaryUrl = imageUrl,
        fallbackUrl = fallbackUrl,
        contentDescription = contentDescription,
        onImageSuccess = { image ->
            onImageSuccess?.invoke(image.asDrawable(resources))
        },
        modifier = modifier.fillMaxSize(),
        placeholder = {
            AppDetailsImagePlaceholder()
        }
    )
}
