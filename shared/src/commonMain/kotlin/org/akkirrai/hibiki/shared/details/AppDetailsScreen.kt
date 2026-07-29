package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.RelatedAnime
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

/**
 * Shared Details visual composition. Playback and library persistence stay in
 * the host; this screen keeps the Android geometry and renders those actions
 * as unavailable until a host supplies the corresponding contract.
 */
@Composable
fun AppDetailsScreen(
    anime: Anime,
    onBackClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val localizedEpisodeWord = appText(AppTextKey.Episodes)
    val relatedTitle = appText(AppTextKey.Related)
    val similarTitle = appText(AppTextKey.Similar)
    val announcementLabel = appText(AppTextKey.Unknown)
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
                        isInLibrary = false,
                        canWatch = false,
                        libraryLabel = appText(AppTextKey.Favorite),
                        watchLabel = appText(AppTextKey.Watch),
                        onPosterClick = {},
                        onLibraryClick = {},
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
                                playbackContent = {},
                                modifier = mediaModifier,
                            )
                        },
                        textContent = { textModifier ->
                            AppDetailsHeroTextContent(
                                title = uiModel.anime.title,
                                description = uiModel.description,
                                backgroundColor = MaterialTheme.colorScheme.background,
                                onTitleClick = {},
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
