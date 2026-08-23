package org.akkirrai.hibiki.details.screen

import org.akkirrai.hibiki.details.state.*
import org.akkirrai.hibiki.details.model.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.Image
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.design.component.modal.AppModalBottomSheet
import org.akkirrai.hibiki.library.screen.*
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.RelatedAnime
import org.akkirrai.hibiki.player.model.TitleWatchState
import org.akkirrai.hibiki.player.formatEpisodeNumber
import org.akkirrai.hibiki.player.formatPlaybackPosition
import androidx.activity.compose.BackHandler
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.isEnglishAppLanguage
import org.akkirrai.hibiki.core.source.resolveEpisodesLabel
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

/** The three mutually-exclusive overlays Details can show -- only one can be open at a time. */
sealed interface DetailsOverlay {
    data object Poster : DetailsOverlay
    data object Title : DetailsOverlay
    data object Library : DetailsOverlay
}

/** Which overlay (if any) is currently open, and how to change it -- a single owner, not three independent flags. */
data class DetailsOverlayState(
    val overlay: DetailsOverlay? = null,
    val onOverlayChange: ((DetailsOverlay?) -> Unit)? = null,
)

data class DetailsActions(
    val onBackClick: () -> Unit,
    val onRelatedAnimeClick: (Anime) -> Unit,
    val onResumeClick: ((TitleWatchState) -> Unit)? = null,
    val onTrailerClick: (() -> Unit)? = null,
    val onWatchClick: () -> Unit = {},
    val onTitleSeedColorChange: (Long) -> Unit = {},
    val onLibraryCategorySelected: (LibraryCategory?) -> Unit = {},
)

/**
 * Shared Details visual composition. Playback and library persistence stay in
 * the host; this screen keeps the Android geometry and renders those actions
 * as unavailable until a host supplies the corresponding contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    anime: Anime,
    actions: DetailsActions,
    backHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    libraryCategory: LibraryCategory? = null,
    resumeState: TitleWatchState? = null,
    resumeFrameContent: (@Composable (Modifier) -> Unit)? = null,
    canWatch: Boolean = false,
    initialTitleSeedColor: Long? = null,
    titleSheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    detailsError: String? = null,
    overlayState: DetailsOverlayState = DetailsOverlayState(),
) {
    val preferEnglish = isEnglishAppLanguage(
        LocalAppLanguage.current,
        LocalConfiguration.current.locales[0]?.language.orEmpty().ifBlank { "en" },
    )
    val relatedTitle = appText(AppTextKey.DetailsRelatedTitle)
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
    val heroInfo = remember(anime, localizedStatus) {
        resolveDetailsHeroInfo(anime).copy(status = localizedStatus)
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
    // A fast up-down flick genuinely passes through a few pixels of scroll offset before
    // settling back at the top, so collapsing on any nonzero offset made the poster visibly
    // flash/shrink even when the user never meant to scroll down. A small tolerance absorbs
    // that without noticeably delaying the collapse once someone actually scrolls.
    val heroCollapseThresholdPx = with(LocalDensity.current) { DetailsHeroCollapseThreshold.toPx() }
    val isAtTop by remember(listState, heroCollapseThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= heroCollapseThresholdPx
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
    val mediaData = remember(uiModel.anime) {
        resolveDetailsHeroMediaData(uiModel.anime)
    }
    val nextEpisodeEta = rememberNextEpisodeEta(
        nextEpisodeAt = uiModel.anime.nextEpisodeAt,
        nowEpochSeconds = { System.currentTimeMillis() / 1_000L },
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
    var localOverlay by remember(anime.id) { mutableStateOf<DetailsOverlay?>(null) }
    var titleSeedColor by remember(anime.id, initialTitleSeedColor) {
        mutableStateOf(initialTitleSeedColor ?: detailsTitleSeedColorCache[anime.id])
    }
    val currentOverlay = if (overlayState.onOverlayChange != null) overlayState.overlay else localOverlay
    fun setOverlay(overlay: DetailsOverlay?) {
        overlayState.onOverlayChange?.invoke(overlay) ?: run { localOverlay = overlay }
    }
    val posterPreviewVisible = currentOverlay == DetailsOverlay.Poster
    val titleSheetVisible = currentOverlay == DetailsOverlay.Title
    val librarySheetVisible = currentOverlay == DetailsOverlay.Library
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
        BackHandler(
            enabled = currentOverlay != null,
            onBack = { setOverlay(null) },
        )
        run {
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
                        libraryLabel = appText(AppTextKey.DetailsFavorite),
                        watchLabel = appText(AppTextKey.Watch),
                        onPosterClick = { setOverlay(DetailsOverlay.Poster) },
                        onLibraryClick = { setOverlay(DetailsOverlay.Library) },
                        onPrimaryClick = actions.onWatchClick,
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
                                                detailsTitleSeedColorCache[anime.id] = color
                                                actions.onTitleSeedColorChange(color)
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
                                            actions.onResumeClick?.let { callback -> { callback(state) } }
                                        },
                                        trailerEnabled = mediaData.trailer != null && actions.onTrailerClick != null,
                                        onTrailerClick = actions.onTrailerClick ?: {},
                                        trailerContentDescription = appText(AppTextKey.DetailsTrailer),
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
                                onTitleClick = { setOverlay(DetailsOverlay.Title) },
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
                        episodesLabel = appText(AppTextKey.EpisodesReleased),
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
                    onItemClick = { related -> actions.onRelatedAnimeClick(related.toPreviewAnime(preferEnglish)) },
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
                onClick = actions.onBackClick,
                contentDescription = appText(AppTextKey.Back),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (posterPreviewVisible) {
            AppDetailsPosterPreviewOverlay(
                onDismissRequest = { setOverlay(null) },
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
                onDismissRequest = { setOverlay(null) },
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
                    actions.onLibraryCategorySelected(category)
                    setOverlay(null)
                },
                onRemoveClick = {
                    actions.onLibraryCategorySelected(null)
                    setOverlay(null)
                },
                onDismiss = { setOverlay(null) },
            )
        }
            }
        }
    }
}

/**
 * Optimistic placeholder shown the instant a related/similar title is tapped, before its real
 * details finish loading -- localized the same way resolveEpisodesLabel() would, so it doesn't
 * flash English text on a Russian UI while the real (also-localized) details resolve.
 */
private fun RelatedAnime.toPreviewAnime(preferEnglish: Boolean): Anime = Anime(
    id = id,
    title = title,
    subtitle = listOfNotNull(type, year?.toString()).joinToString(" · "),
    episodesLabel = resolveEpisodesLabel(
        releasedCount = episodeCount,
        fallbackLabel = null,
        preferEnglish = preferEnglish,
    ),
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

private val detailsScrollStateCache = DetailsSessionCache<DetailsScrollPosition>()
private val detailsTitleSeedColorCache = DetailsSessionCache<Long>()

@Composable
private fun AppLibraryCategorySheet(
    selectedCategory: LibraryCategory?,
    title: String,
    subtitle: String,
    savedNote: String,
    removeAction: String,
    categoryLabels: Map<LibraryCategory, String>,
    onCategoryClick: (LibraryCategory) -> Unit,
    onRemoveClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = LibraryCategorySheetMaxWidth),
            shape = RoundedCornerShape(LibraryCategorySheetCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = LibraryCategorySheetElevation,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LibraryCategorySheetMaxHeight)
                    .padding(LibraryCategorySheetContentPadding),
                verticalArrangement = Arrangement.spacedBy(LibraryCategorySheetSectionGap),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LibraryCategorySheetHeaderGap)) {
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
                            modifier = Modifier.padding(top = LibraryCategorySheetDividerTopPadding),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                        )
                    }
                }

                items(
                    items = LibraryCategory.entries.filter {
                        it != LibraryCategory.Saved && it != LibraryCategory.Recent
                    },
                    key = LibraryCategory::name,
                ) { category ->
                    AppLibraryCategorySheetItem(
                        label = categoryLabels.getValue(category),
                        selected = category == selectedCategory,
                        onClick = { onCategoryClick(category) },
                        iconContent = { iconModifier ->
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = null,
                                modifier = iconModifier,
                                tint = if (category == selectedCategory) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        selectedIconContent = { iconModifier ->
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = iconModifier,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }

                if (selectedCategory == LibraryCategory.Saved) {
                    item {
                        Text(
                            text = savedNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = LibraryCategorySheetSavedNoteTopPadding),
                        )
                    }
                } else if (selectedCategory != null) {
                    item {
                        TextButton(
                            onClick = onRemoveClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = LibraryCategorySheetRemoveTopPadding),
                            shape = RoundedCornerShape(LibraryCategorySheetRemoveCornerRadius),
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
private fun AppLibraryCategorySheetItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable (Modifier) -> Unit,
    selectedIconContent: @Composable (Modifier) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(LibraryCategorySheetItemCornerRadius),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.46f)
        },
        border = BorderStroke(
            width = LibraryCategorySheetItemBorderWidth,
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
                .heightIn(min = LibraryCategorySheetItemMinHeight)
                .padding(horizontal = LibraryCategorySheetItemHorizontalPadding, vertical = LibraryCategorySheetItemVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(LibraryCategorySheetItemContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent(Modifier.size(LibraryCategorySheetItemIconSize))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                selectedIconContent(Modifier.size(LibraryCategorySheetItemSelectedIconSize))
            }
        }
    }
}
