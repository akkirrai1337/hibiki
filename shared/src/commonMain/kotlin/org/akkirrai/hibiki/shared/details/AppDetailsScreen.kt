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
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.shared.library.AppLibraryCategorySheet
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.RelatedAnime
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
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    initialLibraryCategory: LibraryCategory? = null,
    onLibraryCategoryChange: (LibraryCategory?) -> Unit = {},
    isDetailsLoading: Boolean = false,
    detailsError: String? = null,
) {
    val localizedEpisodeWord = appText(AppTextKey.Episodes)
    val relatedTitle = appText(AppTextKey.Related)
    val similarTitle = appText(AppTextKey.Similar)
    val announcementLabel = appText(AppTextKey.Unknown)
    val categoryLabels = mapOf(
        LibraryCategory.Watching to appText(AppTextKey.LibraryWatching),
        LibraryCategory.Planned to appText(AppTextKey.LibraryPlanned),
        LibraryCategory.Completed to appText(AppTextKey.LibraryCompleted),
        LibraryCategory.Dropped to appText(AppTextKey.LibraryDropped),
        LibraryCategory.OnHold to appText(AppTextKey.LibraryOnHold),
        LibraryCategory.Favorite to appText(AppTextKey.LibraryFavorite),
        LibraryCategory.Saved to appText(AppTextKey.LibrarySaved),
    )
    val heroInfo = remember(anime) {
        resolveDetailsHeroInfo(anime, localizedEpisodeWord)
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
    var isPosterPreviewOpen by remember(anime.id) { mutableStateOf(false) }
    var isTitleDetailsSheetOpen by remember(anime.id) { mutableStateOf(false) }
    var isLibrarySheetOpen by remember(anime.id) { mutableStateOf(false) }
    var libraryCategory by remember(anime.id, initialLibraryCategory) {
        mutableStateOf(initialLibraryCategory)
    }

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
                        canWatch = false,
                        libraryLabel = appText(AppTextKey.Favorite),
                        watchLabel = appText(AppTextKey.Watch),
                        onPosterClick = { isPosterPreviewOpen = true },
                        onLibraryClick = { isLibrarySheetOpen = true },
                        onPrimaryClick = {},
                        posterContent = {
                            AppPosterImage(
                                primaryUrl = uiModel.anime.posterUrl,
                                fallbackUrl = uiModel.anime.posterFallbackUrl,
                                contentDescription = uiModel.anime.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholder = { AppDetailsImagePlaceholder(modifier = Modifier.fillMaxSize()) },
                            )
                        },
                        mediaContent = { mediaModifier ->
                            AppDetailsHeroMedia(
                                imageContent = {
                                    AppPosterImage(
                                        primaryUrl = uiModel.anime.screenshots.firstOrNull()
                                            ?: uiModel.anime.posterUrl,
                                        fallbackUrl = uiModel.anime.posterFallbackUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        placeholder = { AppDetailsImagePlaceholder(modifier = Modifier.fillMaxSize()) },
                                    )
                                },
                                frameContent = null,
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
                                },
                                modifier = mediaModifier,
                            )
                        },
                        textContent = { textModifier ->
                            AppDetailsHeroTextContent(
                                title = uiModel.anime.title,
                                description = uiModel.description,
                                backgroundColor = MaterialTheme.colorScheme.background,
                                onTitleClick = { isTitleDetailsSheetOpen = true },
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
                                nextEpisodeContent = null,
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
                        sourceMaterial = uiModel.anime.sourceMaterial,
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

        if (isPosterPreviewOpen) {
            AppDetailsPosterPreviewOverlay(
                onDismissRequest = { isPosterPreviewOpen = false },
                backHandler = { },
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

        if (isTitleDetailsSheetOpen) {
            val titleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            AppModalBottomSheet(
                onDismissRequest = { isTitleDetailsSheetOpen = false },
                sheetState = titleSheetState,
                modifier = Modifier.fillMaxHeight(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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

        if (isLibrarySheetOpen) {
            AppLibraryCategorySheet(
                selectedCategory = libraryCategory,
                title = appText(AppTextKey.LibraryAddTitle),
                subtitle = appText(AppTextKey.LibraryAddSubtitle),
                savedNote = appText(AppTextKey.LibrarySavedNote),
                removeAction = appText(AppTextKey.LibraryRemoveAction),
                categoryLabels = categoryLabels,
                onCategoryClick = { category ->
                    libraryCategory = category
                    onLibraryCategoryChange(category)
                    isLibrarySheetOpen = false
                },
                onRemoveClick = {
                    libraryCategory = null
                    onLibraryCategoryChange(null)
                    isLibrarySheetOpen = false
                },
                onDismiss = { isLibrarySheetOpen = false },
            )
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
