package org.akkirrai.hibiki.feature.details

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.design.iconOrDefault
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.core.design.AppMotion
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.shared.design.component.AppTonalSurface
import org.akkirrai.hibiki.shared.player.formatPlaybackPosition
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.details.isNullOrZero
import org.akkirrai.hibiki.shared.details.resolveAnimeDescription
import org.akkirrai.hibiki.shared.details.resolveDetailsHeroRatings
import org.akkirrai.hibiki.shared.model.toAnime
import org.akkirrai.hibiki.shared.player.isWatchedToEnd
import org.akkirrai.hibiki.core.design.component.AnimeTitleText
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.hibiki.core.model.TitleWatchState
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.model.WatchSourceSelection
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.shared.details.DetailsUiState
import org.akkirrai.hibiki.shared.details.DetailsStatusBarScrim
import org.akkirrai.hibiki.shared.details.DetailsHeroInfo
import org.akkirrai.hibiki.shared.details.DetailsHeroActions
import org.akkirrai.hibiki.shared.details.AppDetailsHeroTextContent
import org.akkirrai.hibiki.shared.details.AppDetailsHeroPlaybackActions
import org.akkirrai.hibiki.shared.details.AppDetailsHeroMedia
import org.akkirrai.hibiki.shared.details.AppDetailsPosterPreviewSurface
import org.akkirrai.hibiki.shared.details.AppDetailsPosterPreviewAnimation
import org.akkirrai.hibiki.shared.details.AppDetailsHeroSection
import org.akkirrai.hibiki.shared.details.AppDetailsHeroOverlayBackButton
import org.akkirrai.hibiki.shared.library.AppLibraryCategorySheet
import org.akkirrai.hibiki.shared.details.AppDetailsTitleSheetContent
import org.akkirrai.hibiki.shared.details.DetailsNextEpisodeChip
import org.akkirrai.hibiki.shared.details.DetailsNestedScrollableContent
import org.akkirrai.hibiki.shared.details.AppDetailsContentList
import org.akkirrai.hibiki.shared.details.DetailsPosterCard
import org.akkirrai.hibiki.shared.details.DetailsGenresSection
import org.akkirrai.hibiki.shared.details.resolveDetailsHeroInfo
import org.akkirrai.hibiki.shared.details.isAnnouncementStatus
import org.akkirrai.hibiki.shared.details.isOngoingStatus
import org.akkirrai.hibiki.shared.details.formatRelatedAnimeMetadata
import org.akkirrai.hibiki.shared.details.extractNextEpisodeNumber
import org.akkirrai.hibiki.shared.details.toAbsoluteImageUrl
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
    val nextEpisodeEta = rememberNextEpisodeEta(currentAnime.nextEpisodeAt)
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
                    additionalBottomPadding = 100.dp,
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
                        horizontalPadding = DETAIL_CONTENT_START_PADDING,
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
            PosterPreviewOverlay(
                anime = currentAnime,
                onDismiss = { isPosterPreviewOpen = false }
            )
        }

        if (isTitleDetailsSheetOpen) {
            TitleDetailsSheet(
                title = currentAnime.title,
                description = description,
                onDismiss = { isTitleDetailsSheetOpen = false },
            )
        }

        if (isLibrarySheetOpen) {
            LibraryCategorySheet(
                selectedCategory = libraryCategory,
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
                onDismiss = { isLibrarySheetOpen = false }
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
    val posterHeightOffset by animateDpAsState(
        targetValue = if (isAtTop) 0.dp else 28.dp,
        animationSpec = tween(durationMillis = 750),
        label = "details_poster_height",
    )
    AppDetailsHeroSection(
        posterHeightOffset = posterHeightOffset,
        onPosterClick = onPosterClick,
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
                        HeroRatingsLine(
                            ratings = detailsState.anime.ratings,
                            viewCount = detailsState.anime.viewCount,
                        )
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
                            modifier = Modifier.padding(top = 2.dp),
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
        actionsContent = {
            DetailsHeroActions(
                isInLibrary = isUserLibraryCategorySelected,
                canWatch = canWatch,
                libraryLabel = stringResource(R.string.details_favorite),
                watchLabel = stringResource(R.string.details_watch),
                libraryIcon = if (isUserLibraryCategorySelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                primaryIcon = Icons.Filled.PlayArrow,
                onLibraryClick = onLibraryClick,
                onPrimaryClick = onPrimaryClick,
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
    val trailer = anime.trailer?.takeIf { it.playbackUrl != null }
    AppDetailsHeroMedia(
        imageContent = {
            NetworkImage(
                imageUrl = trailer?.thumbnailUrl ?: anime.posterUrl,
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
            resumeProgress = resumeState?.let {
                if (it.durationMs > 0L) {
                    (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
            } ?: 0f,
            onResumeClick = resumeState?.let { state -> { onResumeClick(state) } },
            resumeIconContent = {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            },
            trailerEnabled = trailer != null,
            onTrailerClick = onTrailerClick,
            trailerIconContent = {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.details_trailer),
                    modifier = Modifier.size(32.dp),
                )
            },
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
private fun HeroRatingsLine(
    ratings: List<AnimeRating>,
    viewCount: Long?,
    modifier: Modifier = Modifier,
) {
    val data = resolveDetailsHeroRatings(ratings, viewCount) ?: return
    org.akkirrai.hibiki.shared.details.DetailsHeroRatingsLine(
        rating = data.rating,
        viewCount = data.viewCount,
        ratingIcon = Icons.Filled.Star,
        viewCountIcon = Icons.Outlined.Visibility,
        modifier = modifier,
    )
}

@Composable
private fun DetailContentCard(
    anime: Anime,
    heroInfo: DetailsHeroInfo,
    modifier: Modifier = Modifier,
) {
    val sourceMaterial = localizedSourceMaterial(anime.sourceMaterial)
    val emptyValue = stringResource(R.string.search_filters_not_selected)
    val informationItems = listOfNotNull(
        org.akkirrai.hibiki.shared.details.DetailsInformationItem(
            label = stringResource(R.string.details_status),
            value = heroInfo.status.ifBlank { emptyValue },
            icon = Icons.Outlined.Check,
            accent = MaterialTheme.colorScheme.tertiary,
        ),
        org.akkirrai.hibiki.shared.details.DetailsInformationItem(
            label = stringResource(R.string.details_episodes_released),
            value = heroInfo.episodes.ifBlank { emptyValue },
            icon = Icons.Outlined.FormatListNumbered,
            accent = MaterialTheme.colorScheme.primary,
        ),
        org.akkirrai.hibiki.shared.details.DetailsInformationItem(
            label = stringResource(R.string.details_type),
            value = heroInfo.type,
            icon = Icons.Outlined.BookmarkBorder,
            accent = MaterialTheme.colorScheme.secondary,
        ),
        heroInfo.releaseDate.takeIf(String::isNotBlank)?.let { releaseDate ->
            org.akkirrai.hibiki.shared.details.DetailsInformationItem(
                label = stringResource(R.string.details_release_date),
                value = releaseDate,
                icon = Icons.Filled.DateRange,
                accent = MaterialTheme.colorScheme.primary,
            )
        },
        sourceMaterial?.let { source ->
            org.akkirrai.hibiki.shared.details.DetailsInformationItem(
                label = stringResource(R.string.details_source_material),
                value = source,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                accent = MaterialTheme.colorScheme.tertiary,
            )
        },
        heroInfo.studio.takeIf(String::isNotBlank)?.let { studio ->
            org.akkirrai.hibiki.shared.details.DetailsInformationItem(
                label = stringResource(R.string.details_studio),
                value = studio,
                icon = Icons.Filled.Business,
                accent = Color(0xFFFF9800),
            )
        },
    )
    org.akkirrai.hibiki.shared.details.DetailsInformationSection(
        title = stringResource(R.string.details_information),
        items = informationItems,
        horizontalPadding = DETAIL_INFORMATION_HORIZONTAL_PADDING,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleDetailsSheet(
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        AppDetailsTitleSheetContent(title = title, description = description)
    }
}

@Composable
private fun LibraryCategorySheet(
    selectedCategory: LibraryCategory?,
    onCategoryClick: (LibraryCategory) -> Unit,
    onRemoveClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.library_add_title)
    val subtitle = stringResource(R.string.library_add_subtitle)
    val savedNote = stringResource(R.string.library_saved_note)
    val removeAction = stringResource(R.string.library_remove_action)
    val categoryLabels = LibraryCategory.entries.associateWith { category ->
        stringResource(category.labelResId)
    }

    AppLibraryCategorySheet(
        selectedCategory = selectedCategory,
        title = title,
        subtitle = subtitle,
        savedNote = savedNote,
        removeAction = removeAction,
        categoryLabels = categoryLabels,
        onCategoryClick = onCategoryClick,
        onRemoveClick = onRemoveClick,
        onDismiss = onDismiss,
        iconContent = { category ->
            Icon(
                imageVector = category.icon(),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (category == selectedCategory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        selectedIconContent = {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun PosterPreviewOverlay(
    anime: Anime,
    onDismiss: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    fun dismissAnimated() {
        if (isDismissing) return
        isDismissing = true
        isVisible = false
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(180)
            onDismiss()
        }
    }

    BackHandler(onBack = ::dismissAnimated)

    AppDetailsPosterPreviewAnimation(visible = isVisible) { scrimAlpha, posterAlpha, posterScale ->
        AppDetailsPosterPreviewSurface(
            scrimAlpha = scrimAlpha,
            posterAlpha = posterAlpha,
            posterScale = posterScale,
            onDismiss = ::dismissAnimated,
            posterContent = { posterModifier ->
                PosterImage(
                    primaryUrl = anime.posterUrl,
                    fallbackUrl = anime.posterFallbackUrl,
                    contentDescription = anime.title,
                    modifier = posterModifier,
                    contentScale = ContentScale.Fit,
                    placeholder = { ImagePlaceholder(Modifier.fillMaxSize()) },
                )
            },
            backContent = {
                HeroOverlayBackButton(onClick = ::dismissAnimated)
            },
        )
    }
}

@Composable
private fun HeroOverlayBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppDetailsHeroOverlayBackButton(
        onClick = onClick,
        modifier = modifier,
        iconContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Color.White,
                modifier = Modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    blendMode = BlendMode.Difference
                },
            )
        },
    )
}

@Composable
private fun RelatedAnimeList(
    items: List<RelatedAnime>,
    title: String,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val displayItems = remember(items) { items.distinctBy(RelatedAnime::id) }
    val relatedItems = displayItems.map { related ->
        org.akkirrai.hibiki.shared.details.DetailsRelatedAnimeItem(
            id = related.id,
            title = related.title,
            metadata = formatRelatedAnimeMetadata(
                year = related.year,
                type = related.type,
                status = related.status,
                announcementLabel = announcementLabel,
            ),
        )
    }
    val relatedById = displayItems.associateBy(RelatedAnime::id)
    org.akkirrai.hibiki.shared.details.DetailsRelatedAnimeSection(
        items = relatedItems,
        title = title,
        horizontalPadding = DETAIL_CONTENT_START_PADDING,
        onItemClick = { item -> relatedById[item.id]?.let { onAnimeClick(it.toAnime()) } },
        poster = { item ->
            relatedById[item.id]?.let { related ->
                NetworkImage(
                    imageUrl = related.posterUrl,
                    fallbackUrl = related.posterFallbackUrl,
                    contentDescription = related.title,
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun FavoriteCircleButton(
    libraryCategory: LibraryCategory?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 41.dp,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
    org.akkirrai.hibiki.shared.details.DetailsFavoriteCircleButton(
        icon = libraryCategory.iconOrDefault(),
        isInLibrary = libraryCategory != null,
        contentDescription = stringResource(R.string.details_favorite),
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
    )
}

private fun findResumeWatchState(
    repository: WatchStateRepository,
    titleId: String,
): TitleWatchState? {
    return org.akkirrai.hibiki.shared.player.resolveResumeWatchState(repository.getEpisodeProgress(titleId))
}

@Composable
private fun buildSourceSelectorLabel(
    selectedSource: WatchSource?,
    selection: WatchSourceSelection,
): String {
    val title = selectedSource?.title ?: if (selection.autoSelect) stringResource(R.string.watch_source_auto_title) else stringResource(R.string.watch_source_fallback)
    val qualitySuffix = selectedSource?.qualityLabel?.let { " · $it" }.orEmpty()
    return "$title$qualitySuffix"
}

@Composable
private fun rememberNextEpisodeEta(nextEpisodeAt: Long?): String? {
    val seconds = nextEpisodeAt?.takeIf { it > 0L } ?: return null
    var nowEpochSeconds by remember(seconds) {
        mutableLongStateOf(System.currentTimeMillis() / 1_000L)
    }
    LaunchedEffect(seconds) {
        while (nowEpochSeconds < seconds) {
            nowEpochSeconds = System.currentTimeMillis() / 1_000L
            if (nowEpochSeconds >= seconds) break
            delay(1_000L)
        }
    }
    val deltaSeconds = seconds - nowEpochSeconds
    if (deltaSeconds <= 0L) return null
    val days = deltaSeconds / 86_400L
    val hours = deltaSeconds % 86_400L / 3_600L
    val minutes = deltaSeconds % 3_600L / 60L
    val remainingSeconds = deltaSeconds % 60L
    return when {
        days > 0L -> stringResource(R.string.details_eta_days_hours, days, hours.coerceAtLeast(0L))
        hours > 0L -> stringResource(
            R.string.details_eta_hours_minutes_seconds,
            hours,
            minutes.coerceAtLeast(0L),
            remainingSeconds.coerceAtLeast(0L),
        )
        else -> stringResource(
            R.string.details_eta_minutes_seconds,
            minutes.coerceAtLeast(0L),
            remainingSeconds.coerceAtLeast(0L),
        )
    }
}

@Composable
private fun localizedType(type: String): String {
    return when (type.uppercase()) {
        "TV" -> stringResource(R.string.details_type_series)
        "MOVIE" -> stringResource(R.string.details_type_movie)
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "SPECIAL" -> stringResource(R.string.details_type_special)
        else -> type
    }
}

@Composable
private fun localizedSourceMaterial(sourceMaterial: String?): String? {
    val normalized = sourceMaterial?.trim()?.lowercase(Locale.ROOT) ?: return null
    return when (normalized) {
        "манга", "manga" -> stringResource(R.string.details_source_material_manga)
        "манхва", "manhwa" -> stringResource(R.string.details_source_material_manhwa)
        "маньхуа", "manhua" -> stringResource(R.string.details_source_material_manhua)
        "ранобэ", "light novel" -> stringResource(R.string.details_source_material_light_novel)
        "веб-новелла", "web novel" -> stringResource(R.string.details_source_material_web_novel)
        "визуальная новелла", "visual novel" -> stringResource(R.string.details_source_material_visual_novel)
        "игра", "game" -> stringResource(R.string.details_source_material_game)
        "оригинал", "original" -> stringResource(R.string.details_source_material_original)
        else -> sourceMaterial
    }
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
                result.drawable.toBitmap(width = 96, height = 96)
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
private val DETAIL_CONTENT_START_PADDING = 24.dp
private val DETAIL_INFORMATION_HORIZONTAL_PADDING = 12.dp
private val DETAIL_SECTION_VISUAL_ALIGNMENT_OFFSET = 3.dp
private val DETAIL_SECTION_START_PADDING = DETAIL_CONTENT_START_PADDING + DETAIL_SECTION_VISUAL_ALIGNMENT_OFFSET

@Composable
private fun NetworkImage(
    imageUrl: String?,
    fallbackUrl: String? = null,
    contentDescription: String?,
    onImageSuccess: ((Drawable) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    PosterImage(
        primaryUrl = imageUrl,
        fallbackUrl = fallbackUrl,
        contentDescription = contentDescription,
        onImageSuccess = onImageSuccess,
        modifier = modifier.fillMaxSize(),
        placeholder = { ImagePlaceholder() }
    )
}

@Composable
private fun ImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
