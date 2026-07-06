package org.akkirrai.hibiki.feature.details

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.design.iconOrDefault
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppBackButton
import org.akkirrai.hibiki.core.design.component.AppBackButtonStyle
import org.akkirrai.hibiki.core.design.component.AppFilledIconButtonStyle
import org.akkirrai.hibiki.core.design.component.AppSection
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.design.component.AnimeTitleText
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.RelatedAnime
import org.akkirrai.hibiki.core.model.TitleWatchState
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.model.WatchSourceSelection
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeWatchRepository
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.YummyIdMigration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun DetailsScreen(
    anime: Anime,
    onBackClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    onOpenSources: (Anime) -> Unit,
    onOpenDownloadSources: (Anime) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dependencies = remember(context) { context.applicationContext.hibikiDependencies() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val savedScreenState = remember(anime.id) { detailsScreenStateCache[anime.id] }
    val searchRepository = remember(dependencies) { dependencies.animeSearchRepository() }
    val watchRepository = remember(dependencies) { dependencies.animeWatchRepository() }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val offlineTitleMetadataRepository = remember(dependencies) { dependencies.offlineTitleMetadataRepository() }
    val offlineDownloadRepository = remember(dependencies) { dependencies.offlineDownloadRepository() }
    var currentAnime by remember(anime.id) { mutableStateOf(savedScreenState?.anime ?: anime) }
    var isDescriptionExpanded by remember(anime.id) { mutableStateOf(savedScreenState?.isDescriptionExpanded ?: false) }
    var isAlternativeTitlesExpanded by remember(anime.id) { mutableStateOf(savedScreenState?.isAlternativeTitlesExpanded ?: false) }
    var isRelatedSheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isMetaExpanded by remember(anime.id) { mutableStateOf(savedScreenState?.isMetaExpanded ?: false) }
    var libraryCategory by remember(anime.id) { mutableStateOf<LibraryCategory?>(null) }
    var isLibrarySheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isDownloadSheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isDownloadEnqueueing by remember(anime.id) { mutableStateOf(false) }
    var isPosterPreviewOpen by remember(anime.id) { mutableStateOf(false) }
    val listState = remember(anime.id) {
        LazyListState(
            firstVisibleItemIndex = savedScreenState?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = savedScreenState?.firstVisibleItemScrollOffset ?: 0,
        )
    }
    val watchSources = remember(anime.id) { mutableStateListOf<WatchSource>() }
    var watchProgress by remember(anime.id) { mutableStateOf<TitleWatchState?>(null) }
    var episodeProgressItems by remember(anime.id) { mutableStateOf<List<EpisodeWatchProgress>>(emptyList()) }
    var sourceSelection by remember(anime.id) { mutableStateOf(watchStateRepository.getSelectedSource(anime.id)) }
    val fallbackDescriptionA = stringResource(R.string.details_description_fallback_a)
    val fallbackDescriptionB = stringResource(R.string.details_description_fallback_b)
    val localizedEpisodeWord = stringResource(R.string.details_episode_label)
    val currentAnimeState by rememberUpdatedState(currentAnime)
    val descriptionExpandedState by rememberUpdatedState(isDescriptionExpanded)
    val alternativeTitlesExpandedState by rememberUpdatedState(isAlternativeTitlesExpanded)
    val metaExpandedState by rememberUpdatedState(isMetaExpanded)

    DisposableEffect(searchRepository, watchRepository) {
        onDispose {
            searchRepository.close()
            watchRepository.close()
        }
    }

    DisposableEffect(anime.id, listState) {
        onDispose {
            detailsScreenStateCache[anime.id] = DetailsScreenSavedState(
                anime = currentAnimeState,
                isDescriptionExpanded = descriptionExpandedState,
                isAlternativeTitlesExpanded = alternativeTitlesExpandedState,
                isMetaExpanded = metaExpandedState,
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
        libraryCategory = libraryRepository.getLibraryCategory(anime.id)
        watchProgress = watchStateRepository.getTitleWatchState(anime.id)
        episodeProgressItems = watchStateRepository.getEpisodeProgress(anime.id)
        sourceSelection = watchStateRepository.getSelectedSource(anime.id)
        val cached = watchRepository.getCachedSources(anime.id)
        watchSources.clear()
        watchSources.addAll(cached?.sources.orEmpty())
        if (cached == null) {
            runCatching {
                watchRepository.loadSources(anime.id) { updated ->
                    watchSources.clear()
                    watchSources.addAll(updated)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, anime.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                libraryCategory = libraryRepository.getLibraryCategory(anime.id)
                watchProgress = watchStateRepository.getTitleWatchState(anime.id)
                episodeProgressItems = watchStateRepository.getEpisodeProgress(anime.id)
                sourceSelection = watchStateRepository.getSelectedSource(anime.id)
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
    val description = remember(currentAnime, fallbackDescriptionA, fallbackDescriptionB) {
        buildDescription(currentAnime, fallbackDescriptionA, fallbackDescriptionB)
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
    val relatedItems = remember(uiModel.sections) {
        uiModel.sections.filterIsInstance<RelatedSection>().firstOrNull()?.items.orEmpty()
    }
    val selectedSource = remember(watchSources.toList(), sourceSelection) {
        resolveSelectedSource(
            sources = watchSources,
            selection = sourceSelection,
        )
    }
    val selectedSourceProgressItems = remember(episodeProgressItems, selectedSource) {
        filterProgressItemsForSelectedSource(
            progressItems = episodeProgressItems,
            selectedSource = selectedSource,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
        ) {
            NetworkImage(
                imageUrl = currentAnime.posterUrl,
                fallbackUrl = currentAnime.posterFallbackUrl,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
                    .blur(32.dp)
                    .alpha(0.74f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                0.24f to MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                                0.44f to MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                0.68f to MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                DetailHeroSection(
                    anime = uiModel.anime,
                    heroInfo = uiModel.hero,
                    expandedTitles = isAlternativeTitlesExpanded,
                    onToggleTitles = {
                        isAlternativeTitlesExpanded = !isAlternativeTitlesExpanded
                    },
                    metaExpanded = isMetaExpanded,
                    onToggleMetaExpanded = {
                        isMetaExpanded = !isMetaExpanded
                    },
                    canWatch = canWatch,
                    libraryCategory = libraryCategory,
                    onPosterClick = { isPosterPreviewOpen = true },
                    onLibraryClick = {
                        isLibrarySheetOpen = true
                    },
                    onDownloadClick = {
                        offlineTitleMetadataRepository.save(currentAnime)
                        onOpenDownloadSources(currentAnime)
                    },
                    onPrimaryClick = { onOpenSources(currentAnime) },
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
                    modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
                )
            }

            itemsIndexed(
                items = uiModel.sections,
                key = { _, section -> section.key }
            ) { _, section ->
                when (section) {
                    is ScreenshotsSection -> ScreenshotsSectionContent(
                        screenshots = section.screenshots,
                    )

                    is RelatedSection -> {
                        RelatedAnimeList(
                            items = section.items,
                            modifier = Modifier.padding(
                                start = DETAIL_SECTION_START_PADDING,
                                end = UiDimens.ScreenPadding,
                            ),
                            onOpen = {
                                isRelatedSheetOpen = true
                            },
                        )
                    }
                }
            }
        }

        AppBackButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = UiDimens.ScreenPadding, top = 8.dp),
            style = AppBackButtonStyle.HeroOverlay
        )
    }

    if (isPosterPreviewOpen) {
        PosterPreviewOverlay(
            anime = currentAnime,
            onDismiss = { isPosterPreviewOpen = false }
        )
    }

    if (isRelatedSheetOpen && relatedItems.isNotEmpty()) {
        RelatedAnimeSheet(
            items = relatedItems,
            currentAnime = currentAnime,
            title = stringResource(R.string.details_related_titles),
            subtitle = stringResource(
                R.string.details_related_franchise,
                remember(currentAnime.title) { buildCompactFranchiseTitle(currentAnime.title) },
            ),
            countLabel = stringResource(R.string.details_related_count_label, relatedItems.size),
            currentLabel = stringResource(R.string.details_current),
            episodeLabel = localizedEpisodeWord,
            onDismiss = { isRelatedSheetOpen = false },
            onAnimeClick = { related ->
                isRelatedSheetOpen = false
                onRelatedAnimeClick(related.toAnime())
            },
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

    if (isDownloadSheetOpen) {
        DownloadEpisodesSheet(
            subtitle = buildSourceSelectorLabel(
                selectedSource = selectedSource,
                selection = sourceSelection,
            ).takeIf(String::isNotBlank),
            isLoading = isDownloadEnqueueing,
            onSelectionClick = { selection ->
                if (isDownloadEnqueueing) return@DownloadEpisodesSheet
                coroutineScope.launch {
                    isDownloadEnqueueing = true
                    try {
                        val loadedSources = if (watchSources.isEmpty()) {
                            runCatching {
                                watchRepository.loadSources(currentAnime.id) { updated ->
                                    watchSources.clear()
                                    watchSources.addAll(updated)
                                }
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                        val source = selectedSource
                            ?: watchSources.firstOrNull()
                            ?: loadedSources.firstOrNull()
                        if (source == null) {
                            Toast.makeText(context, context.getString(R.string.details_voiceovers_not_found), Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val episodes = runCatching {
                            watchRepository.getCachedEpisodes(source.sourceId)
                                ?: watchRepository.getEpisodes(source.sourceId)
                        }.getOrDefault(emptyList())
                            .sortedBy { it.number }
                        if (episodes.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.details_episodes_not_found), Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val nextEpisodeNumber = resolveNextEpisodeNumber(
                            progressItems = selectedSourceProgressItems,
                            episodeCount = source.episodeCount,
                        ) ?: episodes.first().number
                        val episodesToDownload = when (selection) {
                            DownloadEpisodeSelection.NextOne,
                            DownloadEpisodeSelection.NextThree,
                            DownloadEpisodeSelection.NextFive -> episodes
                                .filter { it.number >= nextEpisodeNumber }
                                .take(selection.limit ?: 1)
                            DownloadEpisodeSelection.All -> episodes
                        }
                        if (episodesToDownload.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.details_no_episodes_for_download), Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val count = offlineDownloadRepository.enqueueEpisodes(
                            source = source,
                            episodes = episodesToDownload,
                        )
                        Toast.makeText(context, context.getString(R.string.details_downloads_added, count), Toast.LENGTH_SHORT).show()
                        isDownloadSheetOpen = false
                    } catch (throwable: Throwable) {
                        Toast.makeText(
                            context,
                            throwable.message ?: context.getString(R.string.details_downloads_add_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } finally {
                        isDownloadEnqueueing = false
                    }
                }
            },
            onDismiss = {
                if (!isDownloadEnqueueing) {
                    isDownloadSheetOpen = false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadEpisodesSheet(
    subtitle: String?,
    isLoading: Boolean,
    onSelectionClick: (DownloadEpisodeSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = UiDimens.ScreenPadding, end = UiDimens.ScreenPadding, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.details_download_episodes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } ?: Spacer(modifier = Modifier.height(4.dp))

            DownloadEpisodeSelection.entries.forEach { selection ->
                DownloadSheetListItem(
                    text = stringResource(selection.labelResId),
                    enabled = !isLoading,
                    onClick = { onSelectionClick(selection) },
                )
            }
            if (isLoading) {
                Text(
                    text = stringResource(R.string.details_downloads_enqueuing),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadSheetListItem(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.52f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class DownloadEpisodeSelection(
    @param:StringRes val labelResId: Int,
    val limit: Int?,
) {
    NextOne(R.string.details_download_next_episode, 1),
    NextThree(R.string.details_download_next_three, 3),
    NextFive(R.string.details_download_next_five, 5),
    All(R.string.details_download_all, null),
}

@Composable
private fun DetailHeroSection(
    anime: Anime,
    heroInfo: HeroInfo,
    expandedTitles: Boolean,
    onToggleTitles: () -> Unit,
    metaExpanded: Boolean,
    onToggleMetaExpanded: () -> Unit,
    canWatch: Boolean,
    libraryCategory: LibraryCategory?,
    onPosterClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPrimaryClick: () -> Unit,
) {
    val heroHorizontalPadding = DETAIL_CONTENT_START_PADDING
    val isUserLibraryCategorySelected = libraryCategory != null && libraryCategory != LibraryCategory.Saved
    val libraryButtonText = when {
        isUserLibraryCategorySelected -> stringResource(libraryCategory!!.labelResId)
        else -> stringResource(R.string.library_button_title)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(DETAIL_HERO_POSTER_TOP_PADDING))
        PosterHeroInline(
            anime = anime,
            onPosterClick = onPosterClick,
        )
        val titleLift = when {
            anime.title.length >= 44 -> 14.dp
            anime.title.length >= 30 -> 8.dp
            else -> 0.dp
        }
        Spacer(modifier = Modifier.height(DETAIL_HERO_TITLE_TOP_SPACING - titleLift))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = heroHorizontalPadding,
                    end = heroHorizontalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TitleCluster(
                anime = anime,
                expandedTitles = expandedTitles,
                onToggleTitles = onToggleTitles,
                contentColor = MaterialTheme.colorScheme.onSurface,
                secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            RatingsSummary(
                ratings = anime.ratings,
                viewCount = anime.viewCount,
                primaryColor = MaterialTheme.colorScheme.onSurface,
                secondaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            val metaChips = rememberMetaChips(anime, heroInfo)
            if (metaChips.isNotEmpty()) {
                MetaChipRow(
                    items = metaChips,
                    expanded = metaExpanded,
                    onToggleExpanded = onToggleMetaExpanded,
                )
            }
            Spacer(modifier = Modifier.height(0.dp))
            if (canWatch) {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.details_watch),
                            maxLines = 2,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailsSecondaryActionButton(
                        text = libraryButtonText,
                        icon = if (isUserLibraryCategorySelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        active = isUserLibraryCategorySelected,
                        onClick = onLibraryClick,
                        modifier = Modifier.weight(1f),
                    )
                    DetailsSecondaryActionButton(
                        text = stringResource(R.string.watch_download),
                        icon = Icons.Outlined.Download,
                        active = false,
                        onClick = onDownloadClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Button(
                    onClick = onLibraryClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUserLibraryCategorySelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.94f)
                        },
                        contentColor = if (isUserLibraryCategorySelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                    ),
                ) {
                    Icon(
                        imageVector = if (isUserLibraryCategorySelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUserLibraryCategorySelected) {
                            stringResource(libraryCategory!!.labelResId)
                        } else {
                            stringResource(R.string.library_add_title)
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

        }
    }
}

@Composable
private fun DetailsSecondaryActionButton(
    text: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.66f)
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun DetailContentCard(
    anime: Anime,
    heroInfo: HeroInfo,
    description: String,
    descriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DETAIL_CONTENT_CARD_INNER_PADDING),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            OverviewFacts(anime = anime, heroInfo = heroInfo)
            Spacer(modifier = Modifier.height(14.dp))
            DescriptionContent(
                description = description,
                expanded = descriptionExpanded,
                onToggleExpanded = onToggleDescription,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PosterHeroInline(
    anime: Anime,
    onPosterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DETAIL_HERO_POSTER_BLOCK_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 174.dp, height = 246.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onPosterClick)
                .background(Color.White.copy(alpha = 0.07f))
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(18.dp),
                    clip = false
                )
        ) {
            NetworkImage(
                imageUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title
            )
        }
    }
}

@Composable
private fun TitleCluster(
    anime: Anime,
    expandedTitles: Boolean,
    onToggleTitles: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val alternativeTitles = remember(anime.alternativeTitles) {
        anime.alternativeTitles
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }
    var alternativeTitlesOverflow by remember(alternativeTitles) { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        val titleInlineContent: Map<String, InlineTextContent> = if (trailingAction != null) {
            mapOf(
                TITLE_BOOKMARK_INLINE_ID to InlineTextContent(
                    Placeholder(
                        width = 1.15.em,
                        height = 1.15.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextTop,
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(start = 6.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        trailingAction()
                    }
                }
            )
        } else {
            emptyMap()
        }
        val titleText = remember(anime.title, trailingAction) {
            buildAnnotatedString {
                append(anime.title)
                if (trailingAction != null) {
                    appendInlineContent(TITLE_BOOKMARK_INLINE_ID, " ")
                }
            }
        }

        AnimeTitleText(
            text = titleText,
            inlineContent = titleInlineContent,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            ),
            color = contentColor,
            textAlign = TextAlign.Start,
            baseMaxLines = 2,
        )
        if (alternativeTitles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alternativeTitles.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (expandedTitles) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expandedTitles) {
                        alternativeTitlesOverflow = result.hasVisualOverflow
                    }
                }
            )
            if (expandedTitles || alternativeTitlesOverflow) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(if (expandedTitles) R.string.details_hide else R.string.details_show_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.clickable(onClick = onToggleTitles)
                )
            }
        }
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
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
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.52f)
        },
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

        AppBackButton(
            onClick = ::dismissAnimated,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = UiDimens.ScreenPadding, top = 8.dp),
            style = AppBackButtonStyle.HeroOverlay,
        )
    }
}

@Composable
private fun RatingsSummary(
    ratings: List<AnimeRating>,
    viewCount: Long?,
    primaryColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (ratings.isEmpty()) return
    val primary = ratings.firstOrNull { it.source.contains("yummy", ignoreCase = true) } ?: ratings.first()
    val secondary = ratings
        .filterNot { it.source.equals(primary.source, ignoreCase = true) }
        .take(4)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = buildString {
                append("★ ")
                append(formatRating(primary.value))
                append(" ")
                append(primary.source)
                primary.votes?.takeIf { it > 0 }?.let { votes ->
                    append(" · ")
                    append(stringResource(R.string.details_rating_votes_compact, formatCount(votes.toLong())))
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp
            ),
            color = primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        viewCount?.takeIf { it > 0L }?.let { views ->
            Text(
                text = stringResource(R.string.details_views_compact, formatCount(views)),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                ),
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary.joinToString(" · ") { "${it.source} ${formatRating(it.value)}" },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = secondaryColor,
                maxLines = 2,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaChipRow(
    items: List<MetaChipUi>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val visibleItems = if (expanded) items else items.take(META_CHIP_COLLAPSED_COUNT)
    val hiddenCount = (items.size - visibleItems.size).coerceAtLeast(0)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visibleItems.forEach { item ->
            CompactMetaChip(item = item, onClick = null)
        }
        if (!expanded && hiddenCount > 0) {
            CompactMetaChip(
                item = MetaChipUi(label = "+$hiddenCount", kind = MetaChipKind.Overflow),
                onClick = onToggleExpanded,
            )
        } else if (expanded && items.size > META_CHIP_COLLAPSED_COUNT) {
            CompactMetaChip(
                item = MetaChipUi(label = stringResource(R.string.details_hide), kind = MetaChipKind.Collapse),
                onClick = onToggleExpanded,
            )
        }
    }
}

@Composable
private fun CompactMetaChip(
    item: MetaChipUi,
    onClick: (() -> Unit)?,
) {
    val containerColor = when (item.kind) {
        MetaChipKind.Status -> Color(0xFF7250DB).copy(alpha = 0.18f)
        MetaChipKind.AgeRating -> Color(0xFFE0A71A).copy(alpha = 0.14f)
        MetaChipKind.Overflow -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
        MetaChipKind.Collapse -> MaterialTheme.colorScheme.primaryContainer
        MetaChipKind.Genre -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    }
    val contentColor = when (item.kind) {
        MetaChipKind.Status -> Color(0xFF4C2AAE)
        MetaChipKind.AgeRating -> Color(0xFFFFD66B)
        MetaChipKind.Overflow -> MaterialTheme.colorScheme.onSecondaryContainer
        MetaChipKind.Collapse -> MaterialTheme.colorScheme.onPrimaryContainer
        MetaChipKind.Genre -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when (item.kind) {
        MetaChipKind.Status -> Color(0xFF6A48D1)
        MetaChipKind.AgeRating -> Color(0xFFFFC53D)
        MetaChipKind.Overflow -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        MetaChipKind.Collapse -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        MetaChipKind.Genre -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }

    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = item.label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor
        )
    }
}

@Composable
private fun FactsLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OverviewFacts(
    anime: Anime,
    heroInfo: HeroInfo,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FactsLine(label = stringResource(R.string.details_type), value = localizedType(heroInfo.type))
        if (heroInfo.releaseDate.isNotBlank()) {
            FactsLine(label = stringResource(R.string.details_release_date), value = heroInfo.releaseDate)
        }
        if (heroInfo.status.isNotBlank()) {
            FactsLine(label = stringResource(R.string.details_status), value = heroInfo.status)
        }
        formatNextEpisodeEta(anime.nextEpisodeAt)?.let { eta ->
            FactsLine(label = stringResource(R.string.details_eta_label), value = eta)
        }
        if (!anime.sourceMaterial.isNullOrBlank()) {
            FactsLine(label = stringResource(R.string.details_source_material), value = anime.sourceMaterial)
        }
        if (heroInfo.studio.isNotBlank()) {
            FactsLine(label = stringResource(R.string.details_studio), value = heroInfo.studio)
        }
        if (heroInfo.episodes.isNotBlank()) {
            FactsLine(label = stringResource(R.string.details_episodes_released), value = heroInfo.episodes)
        }
    }
}

@Composable
private fun DescriptionContent(
    description: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 5,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = stringResource(if (expanded) R.string.details_hide else R.string.details_expand),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onToggleExpanded)
    )
}

@Composable
private fun ScreenshotsSectionContent(
    screenshots: List<String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.details_screenshots),
            modifier = Modifier.padding(
                start = DETAIL_SECTION_START_PADDING,
                end = UiDimens.ScreenPadding,
            ),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        ScreenshotsRow(screenshots)
    }
}

@Composable
private fun ScreenshotsRow(screenshots: List<String>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            start = DETAIL_SECTION_START_PADDING,
            end = UiDimens.ScreenPadding,
        ),
    ) {
        items(screenshots) { screenshot ->
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                NetworkImage(
                    imageUrl = screenshot,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun RelatedAnimeList(
    items: List<RelatedAnime>,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
        },
        animationSpec = tween(durationMillis = 140),
        label = "Related anime preview color",
    )
    val previewItems = remember(items) { items.take(3) }
    val previewTitle = remember(items) {
        items
            .take(2)
            .joinToString(" • ") { it.title }
            .ifBlank { null }
    }

    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp),
        shape = cardShape,
        color = cardColor,
        tonalElevation = 1.dp,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.details_related),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.54f),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = items.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.width(72.dp),
                    horizontalArrangement = Arrangement.spacedBy((-10).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    previewItems.forEach { anime ->
                        AppTonalSurface(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            NetworkImage(
                                imageUrl = anime.posterUrl,
                                fallbackUrl = anime.posterFallbackUrl,
                                contentDescription = anime.title,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    previewTitle?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.details_related_show_all_count, items.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedAnimeSheet(
    items: List<RelatedAnime>,
    currentAnime: Anime,
    title: String,
    subtitle: String,
    countLabel: String,
    currentLabel: String,
    episodeLabel: String,
    onDismiss: () -> Unit,
    onAnimeClick: (RelatedAnime) -> Unit,
) {
    val normalizedCurrentAnimeId = YummyIdMigration.normalizeTitleId(currentAnime.id)
    BackHandler(onBack = onDismiss)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = countLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RelatedSheetCloseButton(onClick = onDismiss)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = RelatedAnime::id) { related ->
                    val isCurrentAnime = YummyIdMigration.normalizeTitleId(related.id) == normalizedCurrentAnimeId
                    RelatedAnimeSheetRow(
                        anime = related,
                        isCurrentAnime = isCurrentAnime,
                        currentLabel = currentLabel,
                        episodeLabel = episodeLabel,
                        onClick = { if (!isCurrentAnime) onAnimeClick(related) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedSheetCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RelatedAnimeSheetRow(
    anime: RelatedAnime,
    isCurrentAnime: Boolean,
    currentLabel: String,
    episodeLabel: String,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowPressed by rowInteractionSource.collectIsPressedAsState()
    val rowColor by animateColorAsState(
        targetValue = when {
            isCurrentAnime -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
            isRowPressed -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.52f)
        },
        animationSpec = tween(durationMillis = 130),
        label = "Related anime sheet row color",
    )
    val meta = remember(anime.id, anime.type, anime.year, anime.episodeCount, episodeLabel) {
        buildRelatedAnimeMeta(anime = anime, episodeLabel = episodeLabel)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isCurrentAnime,
        shape = rowShape,
        color = rowColor,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
        interactionSource = rowInteractionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTonalSurface(
                modifier = Modifier
                    .size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                NetworkImage(
                    imageUrl = anime.posterUrl,
                    fallbackUrl = anime.posterFallbackUrl,
                    contentDescription = anime.title,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    AnimeTitleText(
                        text = anime.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = if (isCurrentAnime) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (isCurrentAnime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        baseMaxLines = 3,
                    )
                    if (isCurrentAnime) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = currentLabel,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!isCurrentAnime) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun buildCompactFranchiseTitle(title: String): String {
    return title
        .substringBefore(". ")
        .substringBefore(" — ")
        .substringBefore(" - ")
        .substringBefore(": ")
        .trim()
        .takeIf(String::isNotBlank)
        ?: title
}

private fun buildRelatedAnimeMeta(
    anime: RelatedAnime,
    episodeLabel: String,
): String? {
    val parts = buildList {
        anime.type
            ?.toRelatedAnimeTypeLabel()
            ?.takeIf(String::isNotBlank)
            ?.let(::add)
        anime.year
            ?.takeIf { it > 0 }
            ?.toString()
            ?.let(::add)
        anime.episodeCount
            ?.takeIf { it > 0 }
            ?.let { count -> add("$count $episodeLabel") }
    }
    return parts.joinToString(" · ").takeIf(String::isNotBlank)
}

private fun String.toRelatedAnimeTypeLabel(): String {
    val value = trim()
    return when (value.lowercase(Locale.ROOT)) {
        "tv", "tv_series", "series", "serial", "сериал" -> "TV"
        "ova" -> "OVA"
        "ona" -> "ONA"
        "special", "спешл" -> "Special"
        "movie", "film", "фильм" -> "Movie"
        else -> value.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
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

private const val WATCHED_END_TOLERANCE_MS = 1_000L

private fun formatEpisodeNumber(number: Double): String {
    return if (number % 1.0 == 0.0) {
        number.toInt().toString()
    } else {
        number.toString()
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
            ?.takeIf { it.isNotBlank() && it != DEFAULT_YEAR && it != UNKNOWN_VALUE }
            ?: year.takeUnless { it == DEFAULT_YEAR || it == UNKNOWN_VALUE }.orEmpty(),
        episodes = episodes,
        status = status,
        studio = anime.studios.joinToString(", "),
    )
}

private fun buildDescription(anime: Anime, fallbackA: String, fallbackB: String): String {
    return anime.description?.takeIf(String::isNotBlank) ?: "${anime.title} $fallbackA $fallbackB"
}

private fun rememberMetaChips(
    anime: Anime,
    heroInfo: HeroInfo,
): List<MetaChipUi> {
    val dedupe = linkedSetOf<String>()
    return buildList {
        heroInfo.status.takeIf(String::isNotBlank)?.let { status ->
            if (dedupe.add(status.lowercase(Locale.getDefault()))) {
                add(MetaChipUi(label = status, kind = MetaChipKind.Status))
            }
        }
        anime.ageRating?.takeIf(String::isNotBlank)?.let { ageRating ->
            if (dedupe.add(ageRating.lowercase(Locale.getDefault()))) {
                add(MetaChipUi(label = ageRating, kind = MetaChipKind.AgeRating))
            }
        }
        anime.genres
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { genre ->
                if (dedupe.add(genre.lowercase(Locale.getDefault()))) {
                    add(MetaChipUi(label = genre, kind = MetaChipKind.Genre))
                }
            }
    }
}

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

private const val META_CHIP_COLLAPSED_COUNT = 5
private const val DEFAULT_TYPE = "TV"
private const val DEFAULT_YEAR = "Unknown"
private const val UNKNOWN_VALUE = "Unknown"
private const val TITLE_BOOKMARK_INLINE_ID = "title_bookmark"

private data class DetailsScreenSavedState(
    val anime: Anime,
    val isDescriptionExpanded: Boolean,
    val isAlternativeTitlesExpanded: Boolean,
    val isMetaExpanded: Boolean,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private val detailsScreenStateCache = ConcurrentHashMap<String, DetailsScreenSavedState>()
private val DETAIL_HERO_POSTER_TOP_PADDING = 66.dp
private val DETAIL_HERO_POSTER_BLOCK_HEIGHT = 252.dp
private val DETAIL_HERO_TITLE_TOP_SPACING = 28.dp
private val DETAIL_CONTENT_START_PADDING = UiDimens.ScreenPadding
private val DETAIL_CONTENT_CARD_INNER_PADDING = 15.dp
private val DETAIL_SECTION_VISUAL_ALIGNMENT_OFFSET = 3.dp
private val DETAIL_SECTION_START_PADDING = DETAIL_CONTENT_START_PADDING + DETAIL_SECTION_VISUAL_ALIGNMENT_OFFSET

private data class MetaChipUi(
    val label: String,
    val kind: MetaChipKind,
)

private enum class MetaChipKind {
    Status,
    AgeRating,
    Genre,
    Overflow,
    Collapse,
}

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
