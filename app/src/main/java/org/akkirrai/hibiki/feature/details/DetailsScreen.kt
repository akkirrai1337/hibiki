package org.akkirrai.hibiki.feature.details

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.design.iconOrDefault
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppBackButton
import org.akkirrai.hibiki.core.design.component.AppBackButtonStyle
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
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
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.YummyIdMigration
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

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
    val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val savedScreenState = remember(anime.id) { detailsScreenStateCache[anime.id] }
    val searchRepository = remember(dependencies) { dependencies.animeSearchRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val offlineTitleMetadataRepository = remember(dependencies) { dependencies.offlineTitleMetadataRepository() }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val resumeFrameRepository = remember(dependencies) { dependencies.resumeFrameRepository() }
    var currentAnime by remember(anime.id) { mutableStateOf(savedScreenState?.anime ?: anime) }
    var isDescriptionExpanded by remember(anime.id) { mutableStateOf(savedScreenState?.isDescriptionExpanded ?: false) }
    var libraryCategory by remember(anime.id) { mutableStateOf<LibraryCategory?>(null) }
    var isLibrarySheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isPosterPreviewOpen by remember(anime.id) { mutableStateOf(false) }
    var isTitleDetailsSheetOpen by remember(anime.id) { mutableStateOf(false) }
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
    val descriptionExpandedState by rememberUpdatedState(isDescriptionExpanded)

    fun refreshWatchStateSnapshot() {
        libraryCategory = libraryRepository.getLibraryCategory(anime.id)
        resumeState = findResumeWatchState(watchStateRepository, anime.id)
        resumeFrame = resumeFrameRepository.getFrame(anime.id)
    }

    DisposableEffect(searchRepository) {
        onDispose {
            searchRepository.close()
        }
    }

    DisposableEffect(anime.id, listState) {
        onDispose {
            detailsScreenStateCache[anime.id] = DetailsScreenSavedState(
                anime = currentAnimeState,
                isDescriptionExpanded = descriptionExpandedState,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(anime.id) {
        offlineTitleMetadataRepository.get(anime.id)?.let { cachedAnime ->
            currentAnime = cachedAnime
        }
        runCatching { searchRepository.getDetails(anime.id, currentAnime) }
            .onSuccess {
                currentAnime = it
                offlineTitleMetadataRepository.save(it)
            }
        refreshWatchStateSnapshot()
    }

    DisposableEffect(lifecycleOwner, anime.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshWatchStateSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val heroInfo = remember(currentAnime, localizedEpisodeWord) {
        buildHeroInfo(currentAnime, localizedEpisodeWord)
    }
    val description = remember(currentAnime) {
        buildDescription(currentAnime)
    }
    val uiModel = remember(
        currentAnime,
        heroInfo,
        description,
        isDescriptionExpanded,
    ) {
        buildDetailsUiModel(
            anime = currentAnime,
            hero = heroInfo,
            description = description,
            isDescriptionExpanded = isDescriptionExpanded,
        )
    }
    val canWatch = remember(currentAnime.episodesLabel, heroInfo.status) {
        !isAnnouncementStatus(heroInfo.status, currentAnime.episodesLabel) && currentAnime.episodesLabel
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { label -> Regex("""\d+""").find(label)?.value?.toIntOrNull() }
            ?.let { episodeCount -> episodeCount > 0 }
            ?: false
    }

    Surface(
        modifier = modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 100.dp,
                ),
            ) {
            item {
                DetailHeroSection(
                    anime = uiModel.anime,
                    heroInfo = uiModel.hero,
                    description = uiModel.description.text,
                    canWatch = canWatch,
                    libraryCategory = libraryCategory,
                    resumeState = resumeState,
                    resumeFrame = resumeFrame,
                    isTitleDetailsSheetOpen = isTitleDetailsSheetOpen,
                    listState = listState,
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
                    description = uiModel.description.text,
                    descriptionExpanded = uiModel.description.expanded,
                    onToggleDescription = {
                        isDescriptionExpanded = !isDescriptionExpanded
                    },
                    modifier = Modifier,
                )
            }

            if (uiModel.anime.genres.isNotEmpty()) {
                item {
                    GenresSection(genres = uiModel.anime.genres)
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
                            onAnimeClick = onRelatedAnimeClick,
                        )
                    }
                }
            }
        }

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

@Composable
private fun DetailHeroSection(
    anime: Anime,
    heroInfo: HeroInfo,
    description: String,
    canWatch: Boolean,
    libraryCategory: LibraryCategory?,
    resumeState: TitleWatchState?,
    resumeFrame: File?,
    isTitleDetailsSheetOpen: Boolean,
    listState: LazyListState,
    onPosterClick: () -> Unit,
    onTitleClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onPrimaryClick: () -> Unit,
    onResumeClick: (TitleWatchState) -> Unit,
    onTrailerClick: () -> Unit,
) {
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

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
        ) {
            DetailHeroMedia(
                anime = anime,
                resumeState = resumeState,
                resumeFrame = resumeFrame,
                onResumeClick = onResumeClick,
                onTrailerClick = onTrailerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.background)
            )
            PosterHeroInline(
                anime = anime,
                height = 165.dp - posterHeightOffset,
                onPosterClick = onPosterClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = DETAIL_CONTENT_START_PADDING),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = DETAIL_CONTENT_START_PADDING + 131.dp,
                        end = DETAIL_CONTENT_START_PADDING,
                        bottom = 6.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val expandToCollapse = AnimatedImageVector.animatedVectorResource(
                    R.drawable.expand_collapse_anim
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onTitleClick)
                        .padding(end = 20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                lineHeight = 27.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        painter = rememberAnimatedVectorPainter(
                            animatedImageVector = expandToCollapse,
                            atEnd = isTitleDetailsSheetOpen,
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HeroRatingsLine(ratings = anime.ratings, viewCount = anime.viewCount)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DETAIL_CONTENT_START_PADDING),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onLibraryClick,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = if (isUserLibraryCategorySelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isUserLibraryCategorySelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isUserLibraryCategorySelected) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = stringResource(R.string.details_favorite),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = onPrimaryClick,
                enabled = canWatch,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = Color.Transparent,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.details_watch),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            modifier = Modifier
                .padding(horizontal = DETAIL_CONTENT_START_PADDING, vertical = 8.dp)
                .height(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.details_overview),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun DetailHeroMedia(
    anime: Anime,
    resumeState: TitleWatchState?,
    resumeFrame: File?,
    onResumeClick: (TitleWatchState) -> Unit,
    onTrailerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trailer = anime.trailer?.takeIf { it.playbackUrl != null }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (resumeState != null && resumeFrame != null) {
            ResumeFrameImage(
                frame = resumeFrame,
                version = resumeState.updatedAt,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NetworkImage(
                imageUrl = trailer?.thumbnailUrl
                    ?: anime.screenshots.firstOrNull()
                    ?: anime.posterUrl,
                fallbackUrl = anime.posterUrl ?: anime.posterFallbackUrl,
                contentDescription = null,
            )
        }

        when {
            resumeState != null -> {
                val progress = if (resumeState.durationMs > 0L) {
                    (resumeState.positionMs.toFloat() / resumeState.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Surface(
                    onClick = { onResumeClick(resumeState) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                    contentColor = Color.White,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.details_watch_continue),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = stringResource(
                                    R.string.details_continue_episode_position,
                                    formatEpisodeNumber(resumeState.episodeNumber),
                                    formatPlaybackPosition(resumeState.positionMs),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.24f),
                    )
                }
            }

            trailer != null -> {
                Surface(
                    onClick = onTrailerClick,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.38f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.details_trailer),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
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
        loading = { ImagePlaceholder(Modifier.fillMaxSize()) },
        error = { ImagePlaceholder(Modifier.fillMaxSize()) },
    )
}

@Composable
private fun HeroRatingsLine(
    ratings: List<AnimeRating>,
    viewCount: Long?,
) {
    val rating = ratings.firstOrNull { it.source.contains("yummy", ignoreCase = true) } ?: ratings.firstOrNull()
    if (rating == null && viewCount.isNullOrZero()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rating?.let {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFFFC107),
            )
            Text(
                text = formatRating(it.value),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        viewCount?.takeIf { it > 0 }?.let {
            if (rating != null) {
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = formatCount(it),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun Long?.isNullOrZero(): Boolean = this == null || this == 0L

@Composable
private fun DetailContentCard(
    anime: Anime,
    heroInfo: HeroInfo,
    description: String,
    descriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nextEpisodeEta = formatNextEpisodeEta(anime.nextEpisodeAt)
    val sourceMaterial = localizedSourceMaterial(anime.sourceMaterial)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        DetailSectionTitle(
            text = stringResource(R.string.details_information),
            modifier = Modifier.padding(horizontal = DETAIL_CONTENT_START_PADDING),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = DETAIL_CONTENT_START_PADDING),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DetailInfoPill(
                    label = stringResource(R.string.details_status),
                    value = heroInfo.status.ifBlank { stringResource(R.string.search_filters_not_selected) },
                    icon = Icons.Outlined.Check,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
            nextEpisodeEta?.let { eta ->
                item {
                    DetailInfoPill(
                        label = stringResource(R.string.details_eta_label),
                        value = eta,
                        icon = Icons.Filled.AccessTime,
                        accent = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                DetailInfoPill(
                    label = stringResource(R.string.details_episodes_released),
                    value = heroInfo.episodes.ifBlank { stringResource(R.string.search_filters_not_selected) },
                    icon = Icons.Outlined.FormatListNumbered,
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                DetailInfoPill(
                    label = stringResource(R.string.details_type),
                    value = heroInfo.type,
                    icon = Icons.Outlined.BookmarkBorder,
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
            heroInfo.releaseDate.takeIf(String::isNotBlank)?.let { releaseDate ->
                item {
                    DetailInfoPill(
                        label = stringResource(R.string.details_release_date),
                        value = releaseDate,
                        icon = Icons.Filled.DateRange,
                        accent = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            sourceMaterial?.let { source ->
                item {
                    DetailInfoPill(
                        label = stringResource(R.string.details_source_material),
                        value = source,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            heroInfo.studio.takeIf(String::isNotBlank)?.let { studio ->
                item {
                    DetailInfoPill(
                        label = stringResource(R.string.details_studio),
                        value = studio,
                        icon = Icons.Filled.Business,
                        accent = Color(0xFFFF9800),
                    )
                }
            }
        }
        if (description.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DETAIL_CONTENT_START_PADDING),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.details_synopsis),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                    )
                    DescriptionContent(
                        description = description,
                        expanded = descriptionExpanded,
                        onToggleExpanded = onToggleDescription,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailInfoPill(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
) {
    Surface(
        modifier = Modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                contentColor = accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleDetailsSheet(
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenCornerRadiusPx = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.findActivity()
                ?.windowManager
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                ?.radius
                ?: 0
        } else {
            0
        }
    }
    val screenCornerRadius = with(density) { screenCornerRadiusPx.toDp() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        shape = RoundedCornerShape(
            topStart = screenCornerRadius,
            topEnd = screenCornerRadius,
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PosterHeroInline(
    anime: Anime,
    height: Dp,
    onPosterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(115.dp)
            .height(height)
            .clickable(onClick = onPosterClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        NetworkImage(
            imageUrl = anime.posterUrl,
            fallbackUrl = anime.posterFallbackUrl,
            contentDescription = anime.title,
        )
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                        )
                    }
                }

                items(
                    items = LibraryCategory.entries.filter { it != LibraryCategory.Saved },
                    key = LibraryCategory::name,
                ) { category ->
                    LibraryCategorySheetItem(
                        category = category,
                        label = categoryLabels.getValue(category),
                        selected = category == selectedCategory,
                        onClick = { onCategoryClick(category) },
                    )
                }

                if (selectedCategory == LibraryCategory.Saved) {
                    item {
                        Text(
                            text = savedNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else if (selectedCategory != null) {
                    item {
                        TextButton(
                            onClick = onRemoveClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(removeAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCategorySheetItem(
    category: LibraryCategory,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.46f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)
            },
        ),
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon(),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PosterPreviewOverlay(
    anime: Anime,
    onDismiss: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.78f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "posterPreviewScrimAlpha"
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "posterPreviewPosterAlpha"
    )
    val posterScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.94f,
        animationSpec = tween(durationMillis = 220),
        label = "posterPreviewPosterScale"
    )

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(onClick = ::dismissAnimated)
    ) {
        PosterImage(
            primaryUrl = anime.posterUrl,
            fallbackUrl = anime.posterFallbackUrl,
            contentDescription = anime.title,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = posterAlpha
                    scaleX = posterScale
                    scaleY = posterScale
                },
            contentScale = ContentScale.Fit,
            placeholder = { ImagePlaceholder(Modifier.fillMaxSize()) }
        )

        HeroOverlayBackButton(
            onClick = ::dismissAnimated,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun HeroOverlayBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBackButton(
        onClick = onClick,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = UiDimens.ScreenPadding, top = 8.dp),
        style = AppBackButtonStyle.HeroOverlay,
    )
}

@Composable
private fun DescriptionContent(
    description: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    var hasOverflow by remember(description) { mutableStateOf(false) }
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        maxLines = if (expanded) Int.MAX_VALUE else 5,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (!expanded) hasOverflow = layout.hasVisualOverflow
        },
    )
    if (hasOverflow) {
        Text(
            text = stringResource(if (expanded) R.string.details_hide else R.string.details_expand),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onToggleExpanded)
        )
    }
}

@Composable
private fun GenresSection(
    genres: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(24.dp))
        DetailSectionTitle(
            text = stringResource(R.string.details_categories),
            modifier = Modifier.padding(horizontal = DETAIL_CONTENT_START_PADDING),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.details_genres),
            modifier = Modifier.padding(horizontal = DETAIL_CONTENT_START_PADDING),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = DETAIL_CONTENT_START_PADDING),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(genres.distinct(), key = { it }) { genre ->
                Surface(
                    modifier = Modifier.height(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedAnimeList(
    items: List<RelatedAnime>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(32.dp))
        DetailSectionTitle(
            text = stringResource(R.string.details_related),
            modifier = Modifier.padding(horizontal = DETAIL_CONTENT_START_PADDING),
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentPadding = PaddingValues(horizontal = DETAIL_CONTENT_START_PADDING),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = RelatedAnime::id) { related ->
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAnimeClick(related.toAnime()) }
                        .padding(bottom = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        NetworkImage(
                            imageUrl = related.posterUrl,
                            fallbackUrl = related.posterFallbackUrl,
                            contentDescription = related.title,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = related.year?.toString().orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        text = related.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteCircleButton(
    libraryCategory: LibraryCategory?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 41.dp,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
    val isInLibrary = libraryCategory != null
    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = libraryCategory.iconOrDefault(),
            contentDescription = stringResource(R.string.details_favorite),
            modifier = Modifier.size(iconSize),
            tint = if (isInLibrary) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            }
        )
    }
}

private data class WatchCtaState(
    val label: String,
    val secondaryLabel: String? = null,
    val action: WatchPrimaryAction,
)

private enum class WatchPrimaryAction {
    OpenEpisodes,
    OpenPlayer,
}

private fun resolveSelectedSource(
    sources: List<WatchSource>,
    selection: WatchSourceSelection,
): WatchSource? {
    if (sources.isEmpty()) return null
    return when {
        selection.autoSelect -> sources.first()
        else -> sources.firstOrNull { it.sourceId == selection.sourceId } ?: sources.first()
    }
}

@Composable
private fun buildWatchCtaState(
    progress: TitleWatchState?,
    progressItems: List<EpisodeWatchProgress>,
    selectedSource: WatchSource?,
): WatchCtaState {
    if (progress == null || selectedSource == null) {
        return WatchCtaState(label = stringResource(R.string.details_watch), action = WatchPrimaryAction.OpenEpisodes)
    }
    val inProgressEpisode = progressItems
        .filter { it.positionMs > 0L && !it.isWatchedToEnd() }
        .maxByOrNull(EpisodeWatchProgress::updatedAt)
    if (inProgressEpisode != null) {
        val remainingMinutes = ((inProgressEpisode.durationMs - inProgressEpisode.positionMs).coerceAtLeast(0L) / 60_000L)
            .coerceAtLeast(1L)
        return WatchCtaState(
            label = stringResource(R.string.details_watch_continue_episode, formatEpisodeNumber(inProgressEpisode.episodeNumber)),
            secondaryLabel = stringResource(R.string.details_watch_remaining, remainingMinutes),
            action = WatchPrimaryAction.OpenPlayer,
        )
    }

    val nextEpisodeNumber = resolveNextEpisodeNumber(
        progressItems = progressItems,
        episodeCount = selectedSource.episodeCount,
    )
    if (nextEpisodeNumber != null) {
        return WatchCtaState(
            label = stringResource(R.string.details_watch_continue_episode, formatEpisodeNumber(nextEpisodeNumber)),
            action = WatchPrimaryAction.OpenEpisodes,
        )
    }

    return WatchCtaState(
        label = stringResource(R.string.details_watch_rewatch),
        action = WatchPrimaryAction.OpenEpisodes,
    )
}

private fun filterProgressItemsForSelectedSource(
    progressItems: List<EpisodeWatchProgress>,
    selectedSource: WatchSource?,
): List<EpisodeWatchProgress> {
    if (selectedSource == null) return progressItems
    return progressItems.filter { it.sourceId == selectedSource.sourceId }
}

private fun resolveSelectedSourceProgress(
    fallbackProgress: TitleWatchState?,
    selectedSourceProgressItems: List<EpisodeWatchProgress>,
): TitleWatchState? {
    val latest = selectedSourceProgressItems.maxByOrNull(EpisodeWatchProgress::updatedAt)
        ?: return fallbackProgress
    return TitleWatchState(
        titleId = latest.titleId,
        episodeId = latest.episodeId,
        episodeNumber = latest.episodeNumber,
        sourceId = latest.sourceId,
        voiceoverId = latest.voiceoverId,
        sourceTitle = latest.sourceTitle,
        quality = latest.quality,
        positionMs = latest.positionMs,
        durationMs = latest.durationMs,
        updatedAt = latest.updatedAt,
    )
}

private fun resolveNextEpisodeNumber(
    progressItems: List<EpisodeWatchProgress>,
    episodeCount: Int?,
): Double? {
    val watchedNumbers = progressItems
        .filter(EpisodeWatchProgress::isWatchedToEnd)
        .map(EpisodeWatchProgress::episodeNumber)
    val lastWatched = watchedNumbers.maxOrNull() ?: return 1.0
    val nextSavedEpisode = progressItems
        .map(EpisodeWatchProgress::episodeNumber)
        .filter { it > lastWatched }
        .minOrNull()
    if (nextSavedEpisode != null) {
        return nextSavedEpisode
    }
    val inferredNextEpisode = lastWatched + 1.0
    return if (episodeCount == null || inferredNextEpisode <= episodeCount.toDouble()) {
        inferredNextEpisode
    } else {
        null
    }
}

private fun EpisodeWatchProgress.isWatchedToEnd(): Boolean {
    return durationMs > 0L && positionMs >= (durationMs - WATCHED_END_TOLERANCE_MS).coerceAtLeast(0L)
}

private fun findResumeWatchState(
    repository: WatchStateRepository,
    titleId: String,
): TitleWatchState? {
    val latest = repository.getEpisodeProgress(titleId)
        .asSequence()
        .filter { it.positionMs > 0L && !it.isWatchedToEnd() }
        .maxByOrNull(EpisodeWatchProgress::updatedAt)
        ?: return null
    return TitleWatchState(
        titleId = latest.titleId,
        episodeId = latest.episodeId,
        episodeNumber = latest.episodeNumber,
        sourceId = latest.sourceId,
        voiceoverId = latest.voiceoverId,
        sourceTitle = latest.sourceTitle,
        quality = latest.quality,
        positionMs = latest.positionMs,
        durationMs = latest.durationMs,
        updatedAt = latest.updatedAt,
    )
}

private const val WATCHED_END_TOLERANCE_MS = 1_000L

private fun formatEpisodeNumber(number: Double): String {
    return if (number % 1.0 == 0.0) {
        number.toInt().toString()
    } else {
        number.toString()
    }
}

private fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
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

internal data class HeroInfo(
    val type: String,
    val releaseDate: String,
    val episodes: String,
    val status: String,
    val studio: String,
)

private fun buildHeroInfo(anime: Anime, localizedEpisodeWord: String): HeroInfo {
    val parts = anime.subtitle
        .split(Regex("\\s*[·|]\\s*"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val type = parts.getOrNull(0)?.uppercase().orEmpty().ifBlank { DEFAULT_TYPE }
    val year = parts.getOrNull(1).orEmpty()

    val rawEpisodes = anime.episodesLabel
        .replace("episodes", localizedEpisodeWord, ignoreCase = true)
        .replace("episode", localizedEpisodeWord, ignoreCase = true)
        .takeIf { it.isNotBlank() && it != UNKNOWN_VALUE }
        .orEmpty()

    val episodeCount = Regex("""\d+""")
        .find(rawEpisodes)
        ?.value
        ?.toIntOrNull()

    val episodes = rawEpisodes
        .takeIf { episodeCount == null || episodeCount > 0 }
        .orEmpty()

    val status = anime.status.takeUnless { it.isBlank() || it == UNKNOWN_VALUE }.orEmpty()
    return HeroInfo(
        type = type,
        releaseDate = anime.releaseDate
            ?.takeIf(::isKnownReleaseDate)
            ?: year.takeIf(::isKnownReleaseDate).orEmpty(),
        episodes = episodes,
        status = status,
        studio = anime.studios.joinToString(", "),
    )
}

private fun buildDescription(anime: Anime): String {
    return anime.description?.takeIf(String::isNotBlank).orEmpty()
}

private fun String.isKnownValue(): Boolean {
    val normalized = trim()
    return normalized.isNotEmpty() &&
        !normalized.equals(UNKNOWN_VALUE, ignoreCase = true) &&
        normalized != "0"
}

private fun isKnownReleaseDate(value: String): Boolean = value.isKnownValue()

private fun watchSourcesAvailable(
    selectedSource: WatchSource?,
    selection: WatchSourceSelection,
): Boolean {
    return selectedSource != null || !selection.sourceTitle.isNullOrBlank()
}

private fun isAnnouncementStatus(status: String, episodesLabel: String = ""): Boolean {
    val values = listOf(status, episodesLabel).map { it.trim().lowercase(Locale.getDefault()) }
    return values.any { value ->
        value == "анонс" || value == "announcement" || value == "announced" || value == "anons"
    }
}

@Composable
private fun formatNextEpisodeEta(nextEpisodeAt: Long?): String? {
    val seconds = nextEpisodeAt?.takeIf { it > 0L } ?: return null
    val deltaSeconds = seconds - System.currentTimeMillis() / 1000L
    if (deltaSeconds <= 0L) return null
    val days = deltaSeconds / 86_400L
    val hours = (deltaSeconds % 86_400L) / 3_600L
    val minutes = (deltaSeconds % 3_600L) / 60L
    return when {
        days > 0L -> stringResource(R.string.details_eta_days_hours, days, hours.coerceAtLeast(0L))
        hours > 0L -> stringResource(R.string.details_eta_hours_minutes, hours, minutes.coerceAtLeast(0L))
        else -> stringResource(R.string.details_eta_minutes, minutes.coerceAtLeast(1L))
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

private fun formatRating(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatCount(value: Long): String {
    return when {
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun RelatedAnime.toAnime(): Anime = Anime(
    id = id,
    title = title,
    subtitle = "",
    episodesLabel = "",
    status = "",
    posterUrl = posterUrl,
    posterFallbackUrl = posterFallbackUrl
)

private const val DEFAULT_TYPE = "TV"
private const val DEFAULT_YEAR = "Unknown"
private const val UNKNOWN_VALUE = "Unknown"

private data class DetailsScreenSavedState(
    val anime: Anime,
    val isDescriptionExpanded: Boolean,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private val detailsScreenStateCache = ConcurrentHashMap<String, DetailsScreenSavedState>()
private val DETAIL_CONTENT_START_PADDING = 24.dp
private val DETAIL_SECTION_VISUAL_ALIGNMENT_OFFSET = 3.dp
private val DETAIL_SECTION_START_PADDING = DETAIL_CONTENT_START_PADDING + DETAIL_SECTION_VISUAL_ALIGNMENT_OFFSET

@Composable
private fun NetworkImage(
    imageUrl: String?,
    fallbackUrl: String? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    PosterImage(
        primaryUrl = imageUrl,
        fallbackUrl = fallbackUrl,
        contentDescription = contentDescription,
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
