package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import coil3.Image
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.shared.library.AppLibraryCategorySheet
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.RelatedAnime
import org.akkirrai.hibiki.shared.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.formatPlaybackPosition
import org.akkirrai.hibiki.shared.platform.currentEpochSeconds
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

/**
 * Shared Details visual composition. Playback and library persistence stay in
 * the host; this screen keeps the Android geometry and renders those actions
 * as unavailable until a host supplies the corresponding contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    anime: Anime,
    onBackClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    backHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    libraryRepository: LibraryRepository? = null,
    resumeState: TitleWatchState? = null,
    resumeFrameContent: (@Composable (Modifier) -> Unit)? = null,
    onResumeClick: ((TitleWatchState) -> Unit)? = null,
    onTrailerClick: (() -> Unit)? = null,
    canWatch: Boolean = false,
    onWatchClick: () -> Unit = {},
    initialTitleSeedColor: Long? = null,
    onTitleSeedColorChange: (Long) -> Unit = {},
    titleSheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    initialLibraryCategory: LibraryCategory? = null,
    onLibraryCategoryChange: (LibraryCategory?) -> Unit = {},
    isDetailsLoading: Boolean = false,
    detailsError: String? = null,
    posterPreviewOpen: Boolean? = null,
    onPosterPreviewOpenChange: ((Boolean) -> Unit)? = null,
    titleSheetOpen: Boolean? = null,
    onTitleSheetOpenChange: ((Boolean) -> Unit)? = null,
    librarySheetOpen: Boolean? = null,
    onLibrarySheetOpenChange: ((Boolean) -> Unit)? = null,
) {
    val localizedEpisodeWord = appText(AppTextKey.Episodes)
    val relatedTitle = appText(AppTextKey.Related)
    val similarTitle = appText(AppTextKey.Similar)
    val announcementLabel = appText(AppTextKey.Announcement)
    val localizedStatus = resolveDetailsStatusLabel(
        status = anime.status,
        ongoingLabel = appText(AppTextKey.Ongoing),
        releasedLabel = appText(AppTextKey.Released),
        announcementLabel = announcementLabel,
    )
    val categoryLabels = mapOf(
        LibraryCategory.Watching to appText(AppTextKey.LibraryWatching),
        LibraryCategory.Planned to appText(AppTextKey.LibraryPlanned),
        LibraryCategory.Completed to appText(AppTextKey.LibraryCompleted),
        LibraryCategory.Dropped to appText(AppTextKey.LibraryDropped),
        LibraryCategory.OnHold to appText(AppTextKey.LibraryOnHold),
        LibraryCategory.Favorite to appText(AppTextKey.LibraryFavorite),
        LibraryCategory.Saved to appText(AppTextKey.LibrarySaved),
    )
    val sourceMaterial = resolveSourceMaterialLabel(
        sourceMaterial = anime.sourceMaterial,
        labels = SourceMaterialLabels(
            manga = appText(AppTextKey.SourceMaterialManga),
            manhwa = appText(AppTextKey.SourceMaterialManhwa),
            manhua = appText(AppTextKey.SourceMaterialManhua),
            lightNovel = appText(AppTextKey.SourceMaterialLightNovel),
            webNovel = appText(AppTextKey.SourceMaterialWebNovel),
            visualNovel = appText(AppTextKey.SourceMaterialVisualNovel),
            game = appText(AppTextKey.SourceMaterialGame),
            original = appText(AppTextKey.SourceMaterialOriginal),
        ),
    )
    val heroInfo = remember(anime, localizedEpisodeWord, localizedStatus) {
        resolveDetailsHeroInfo(anime, localizedEpisodeWord).copy(status = localizedStatus)
    }
    val uiModel = remember(anime, heroInfo) {
        buildDetailsUiModel(
            anime = anime,
            hero = heroInfo,
            description = anime.description.orEmpty(),
            includeRelated = true,
            includeSimilar = true,
        )
    }
    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val savedScrollPosition = detailsScrollStateCache[anime.id]
    LaunchedEffect(anime.id) {
        savedScrollPosition?.let { position ->
            listState.scrollToItem(position.index, position.offset)
        }
    }
    DisposableEffect(anime.id, listState) {
        onDispose {
            detailsScrollStateCache[anime.id] = DetailsScrollPosition(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
            )
        }
    }
    val mediaData = remember(uiModel.anime, resumeState) {
        resolveDetailsHeroMediaData(uiModel.anime)
    }
    val nextEpisodeEta = rememberNextEpisodeEta(
        nextEpisodeAt = uiModel.anime.nextEpisodeAt,
        nowEpochSeconds = ::currentEpochSeconds,
        daysHoursLabel = { days, hours ->
            appText(AppTextKey.NextEpisodeEtaDaysHours).formatAppText(days, hours)
        },
        hoursMinutesSecondsLabel = { hours, minutes, seconds ->
            appText(AppTextKey.NextEpisodeEtaHoursMinutesSeconds).formatAppText(hours, minutes, seconds)
        },
        minutesSecondsLabel = { minutes, seconds ->
            appText(AppTextKey.NextEpisodeEtaMinutesSeconds).formatAppText(minutes, seconds)
        },
    )
    var isPosterPreviewOpen by remember(anime.id) { mutableStateOf(false) }
    var isTitleDetailsSheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isLibrarySheetOpen by remember(anime.id) { mutableStateOf(false) }
    var libraryCategory by remember(anime.id, initialLibraryCategory) {
        mutableStateOf(libraryRepository?.getLibraryCategory(anime.id) ?: initialLibraryCategory)
    }
    var titleSeedColor by remember(anime.id, initialTitleSeedColor) {
        mutableStateOf(initialTitleSeedColor)
    }
    val posterPreviewVisible = posterPreviewOpen ?: isPosterPreviewOpen
    val titleSheetVisible = titleSheetOpen ?: isTitleDetailsSheetOpen
    val librarySheetVisible = librarySheetOpen ?: isLibrarySheetOpen
    fun setPosterPreviewVisible(visible: Boolean) {
        onPosterPreviewOpenChange?.invoke(visible) ?: run { isPosterPreviewOpen = visible }
    }
    fun setTitleSheetVisible(visible: Boolean) {
        onTitleSheetOpenChange?.invoke(visible) ?: run { isTitleDetailsSheetOpen = visible }
    }
    fun setLibrarySheetVisible(visible: Boolean) {
        onLibrarySheetOpenChange?.invoke(visible) ?: run { isLibrarySheetOpen = visible }
    }
    val screenScope = rememberCoroutineScope()
    val fallbackColorScheme = MaterialTheme.colorScheme
    val detailsColorScheme = titleSeedColor?.let { seedColor ->
        rememberDynamicColorScheme(
            seedColor = Color(seedColor),
            isDark = fallbackColorScheme.background.luminance() < 0.5f,
            style = PaletteStyle.Vibrant,
        )
    } ?: fallbackColorScheme

    MaterialTheme(colorScheme = detailsColorScheme) {
        AppSystemBackHandler(
            enabled = detailsOverlayBackTarget(
                librarySheetOpen = librarySheetVisible,
                titleSheetOpen = titleSheetVisible,
                posterPreviewOpen = posterPreviewVisible,
            ) != DetailsOverlayBackTarget.None,
            onBack = {
                when (detailsOverlayBackTarget(librarySheetVisible, titleSheetVisible, posterPreviewVisible)) {
                    DetailsOverlayBackTarget.Library -> setLibrarySheetVisible(false)
                    DetailsOverlayBackTarget.Title -> setTitleSheetVisible(false)
                    DetailsOverlayBackTarget.Poster -> setPosterPreviewVisible(false)
                    DetailsOverlayBackTarget.None -> Unit
                }
            },
        ) {
            Surface(
                modifier = modifier.fillMaxSize(),
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
                    AppDetailsHeroContent(
                        posterExpanded = isAtTop,
                        isInLibrary = libraryCategory != null && libraryCategory != LibraryCategory.Saved,
                        canWatch = canWatch,
                        libraryLabel = appText(AppTextKey.Favorite),
                        watchLabel = appText(AppTextKey.Watch),
                        onPosterClick = { setPosterPreviewVisible(true) },
                        onLibraryClick = { setLibrarySheetVisible(true) },
                        onPrimaryClick = onWatchClick,
                        posterContent = {
                            AppPosterImage(
                                primaryUrl = uiModel.anime.posterUrl,
                                fallbackUrl = uiModel.anime.posterFallbackUrl,
                                contentDescription = uiModel.anime.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onImageSuccess = { image: Image ->
                                    if (titleSeedColor == null) {
                                        screenScope.launch {
                                            extractTitleSeedColor(image)?.let { color ->
                                                titleSeedColor = color
                                                onTitleSeedColorChange(color)
                                            }
                                        }
                                    }
                                },
                                placeholder = { AppDetailsImagePlaceholder(modifier = Modifier.fillMaxSize()) },
                            )
                        },
                        mediaContent = { mediaModifier ->
                            val heroPrimaryUrl = mediaData.trailer?.thumbnailUrl
                                ?: uiModel.anime.screenshots.firstOrNull()
                                ?: uiModel.anime.posterUrl
                            val heroFallbackUrl = if (heroPrimaryUrl != uiModel.anime.posterUrl) {
                                uiModel.anime.posterUrl ?: uiModel.anime.posterFallbackUrl
                            } else {
                                uiModel.anime.posterFallbackUrl
                            }
                            AppDetailsHeroMedia(
                                imageContent = {
                                    AppPosterImage(
                                        primaryUrl = heroPrimaryUrl,
                                        fallbackUrl = heroFallbackUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        placeholder = { AppDetailsImagePlaceholder(modifier = Modifier.fillMaxSize()) },
                                    )
                                },
                                frameContent = resumeState?.let {
                                    resumeFrameContent?.let { content ->
                                        { content(Modifier.fillMaxSize()) }
                                    }
                                },
                                playbackContent = {
                                    if (isDetailsLoading) {
                                        CircularProgressIndicator()
                                    }
                                    detailsError?.let { message ->
                                        Text(
                                            text = message,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(24.dp),
                                        )
                                    }
                                    AppDetailsHeroPlaybackActions(
                                        resumeTitle = resumeState?.let { appText(AppTextKey.WatchContinue) },
                                        resumeSubtitle = resumeState?.let {
                                            appText(AppTextKey.WatchContinueEpisodePosition).formatAppText(
                                                formatEpisodeNumber(it.episodeNumber),
                                                formatPlaybackPosition(it.positionMs),
                                            )
                                        },
                                        onResumeClick = resumeState?.let { state ->
                                            onResumeClick?.let { callback -> { callback(state) } }
                                        },
                                        trailerEnabled = mediaData.trailer != null && onTrailerClick != null,
                                        onTrailerClick = onTrailerClick ?: {},
                                        trailerContentDescription = appText(AppTextKey.Trailer),
                                    )
                                },
                                modifier = mediaModifier,
                            )
                        },
                        textContent = { textModifier ->
                            AppDetailsHeroTextContent(
                                title = uiModel.anime.title,
                                description = uiModel.description,
                                backgroundColor = MaterialTheme.colorScheme.background,
                                onTitleClick = { setTitleSheetVisible(true) },
                                ratingsContent = resolveDetailsHeroRatings(
                                    uiModel.anime.ratings,
                                    uiModel.anime.viewCount,
                                )?.let { ratings ->
                                    {
                                        DetailsHeroRatingsLine(
                                            rating = ratings.rating,
                                            viewCount = ratings.viewCount,
                                        )
                                    }
                                },
                                nextEpisodeContent = nextEpisodeEta?.let { eta ->
                                    {
                                        DetailsNextEpisodeChip(
                                            text = if (heroInfo.nextEpisodeNumber != null) {
                                                appText(AppTextKey.NextEpisodeCountdownNumbered)
                                                    .formatAppText(heroInfo.nextEpisodeNumber, eta)
                                            } else {
                                                appText(AppTextKey.NextEpisodeCountdown).formatAppText(eta)
                                            },
                                            icon = Icons.Outlined.HourglassEmpty,
                                        )
                                    }
                                },
                                expandIconContent = {
                                    Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                                },
                                modifier = textModifier,
                            )
                        },
                    )
                }
                item {
                    AppDetailsInformationContent(
                        heroInfo = uiModel.hero,
                        title = appText(AppTextKey.Information),
                        emptyValue = appText(AppTextKey.Unknown),
                        statusLabel = appText(AppTextKey.Status),
                        episodesLabel = appText(AppTextKey.Episodes),
                        typeLabel = appText(AppTextKey.Type),
                        releaseDateLabel = appText(AppTextKey.ReleaseDate),
                        sourceMaterialLabel = appText(AppTextKey.SourceMaterial),
                        studioLabel = appText(AppTextKey.Studio),
                        sourceMaterial = sourceMaterial,
                        horizontalPadding = DetailsInformationHorizontalPadding,
                    )
                }
                if (uiModel.anime.genres.isNotEmpty()) {
                    item {
                        DetailsGenresSection(
                            genres = uiModel.anime.genres,
                            title = appText(AppTextKey.Genres),
                            horizontalPadding = DetailsContentHorizontalPadding,
                        )
                    }
                }
                appDetailsRelatedSections(
                    sections = uiModel.sections,
                    relatedTitle = relatedTitle,
                    similarTitle = similarTitle,
                    announcementLabel = announcementLabel,
                    horizontalPadding = DetailsContentHorizontalPadding,
                    onItemClick = { related -> onRelatedAnimeClick(related.toPreviewAnime()) },
                    poster = { related ->
                        AppPosterImage(
                            primaryUrl = related.posterUrl,
                            fallbackUrl = related.posterFallbackUrl,
                            contentDescription = related.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = { AppDetailsImagePlaceholder(modifier = Modifier.fillMaxSize()) },
                        )
                    },
                )
            }
            DetailsStatusBarScrim(
                listState = listState,
                modifier = Modifier.align(Alignment.TopStart),
            )
            AppDetailsHeroOverlayBackButton(
                onClick = onBackClick,
                contentDescription = appText(AppTextKey.Back),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (posterPreviewVisible) {
            AppDetailsPosterPreviewOverlay(
                onDismissRequest = { setPosterPreviewVisible(false) },
                backHandler = backHandler,
                posterContent = { posterModifier ->
                    AppPosterImage(
                        primaryUrl = uiModel.anime.posterUrl,
                        fallbackUrl = uiModel.anime.posterFallbackUrl,
                        contentDescription = uiModel.anime.title,
                        modifier = posterModifier,
                        contentScale = ContentScale.Fit,
                        placeholder = { AppDetailsImagePlaceholder(modifier = Modifier.fillMaxSize()) },
                    )
                },
                backContent = { onDismiss ->
                    AppDetailsHeroOverlayBackButton(
                        onClick = onDismiss,
                        contentDescription = appText(AppTextKey.Back),
                    )
                },
            )
        }

        if (titleSheetVisible) {
            val titleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            AppModalBottomSheet(
                onDismissRequest = { setTitleSheetVisible(false) },
                sheetState = titleSheetState,
                modifier = Modifier.fillMaxHeight(),
                shape = titleSheetShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                dragHandleContent = { expanded ->
                    AppDetailsTitleSheetDragHandle(expanded = expanded)
                },
            ) {
                AppDetailsTitleSheetContent(
                    title = uiModel.anime.title,
                    description = uiModel.description,
                )
            }
        }

        if (librarySheetVisible) {
            AppLibraryCategorySheet(
                selectedCategory = libraryCategory,
                title = appText(AppTextKey.LibraryAddTitle),
                subtitle = appText(AppTextKey.LibraryAddSubtitle),
                savedNote = appText(AppTextKey.LibrarySavedNote),
                removeAction = appText(AppTextKey.LibraryRemoveAction),
                categoryLabels = categoryLabels,
                onCategoryClick = { category ->
                    libraryRepository?.saveToLibrary(uiModel.anime, category)
                    libraryCategory = category
                    onLibraryCategoryChange(category)
                    setLibrarySheetVisible(false)
                },
                onRemoveClick = {
                    libraryRepository?.removeFromLibrary(uiModel.anime.id)
                    libraryCategory = null
                    onLibraryCategoryChange(null)
                    setLibrarySheetVisible(false)
                },
                onDismiss = { setLibrarySheetVisible(false) },
            )
        }
            }
        }
    }
}

private fun RelatedAnime.toPreviewAnime(): Anime = Anime(
    id = id,
    title = title,
    subtitle = listOfNotNull(type, year?.toString()).joinToString(" · "),
    episodesLabel = episodeCount?.let { "$it episodes" } ?: "Episodes unknown",
    status = status ?: "Unknown",
    posterUrl = posterUrl,
)

private fun String.formatAppText(vararg args: Any): String = args.fold(this) { text, argument ->
    text.replaceFirst(Regex("%[sd]"), argument.toString())
}

private data class DetailsScrollPosition(
    val index: Int,
    val offset: Int,
)

private val detailsScrollStateCache = mutableMapOf<String, DetailsScrollPosition>()
