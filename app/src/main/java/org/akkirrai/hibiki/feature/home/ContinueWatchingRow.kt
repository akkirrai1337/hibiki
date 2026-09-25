package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.anime.AnimeSourceBadge
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.model.formatEpisodeNumber
import org.akkirrai.hibiki.core.model.formatPlaybackTime
import org.akkirrai.hibiki.core.model.isWatchedToEnd
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository

/** What a continue-watching card shows besides the title: where the viewer stopped and the frame there. */
private data class ContinueInfo(val progress: EpisodeWatchProgress?, val frame: File?)

/**
 * Continue watching as one row of 16:9 cards, the last captured frame of each title with the episode and
 * how far it got, most recent first - the same shelf the desktop app has.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingRow(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
    sharedFrameModifier: @Composable (Anime) -> Modifier = { Modifier },
) {
    val context = LocalContext.current.applicationContext
    val ids = items.map { it.id }
    val info by produceState(emptyMap<String, ContinueInfo>(), ids) {
        val watchState = WatchStateRepository(context)
        val frames = ResumeFrameRepository(context)
        // Read again whenever progress is written, so a frame or an episode change shows up in place.
        WatchStateRepository.changes.onStart { emit(Unit) }.collect {
            value = withContext(Dispatchers.IO) {
                ids.associateWith { id ->
                    val episodes = watchState.getEpisodeProgress(id).filter { it.positionMs > 0L }
                    val latest = episodes.filter { !it.isWatchedToEnd() }.maxByOrNull { it.updatedAt }
                        ?: episodes.maxByOrNull { it.updatedAt }
                    ContinueInfo(latest, frames.getFrame(id))
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_continue_title),
            icon = Icons.Outlined.History,
            modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = UiDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { anime ->
                ContinueWatchingFrameCard(
                    anime = anime,
                    info = info[anime.id],
                    sharedFrameModifier = sharedFrameModifier(anime),
                    onClick = { onAnimeClick(anime) },
                    onLongClick = { onAnimeLongClick(anime) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingFrameCard(
    anime: Anime,
    info: ContinueInfo?,
    sharedFrameModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progress = info?.progress
    val frame = info?.frame
    Column(
        modifier = Modifier
            .width(CONTINUE_CARD_WIDTH)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            // The page opens from and closes back into this frame, not the title's poster elsewhere on Home.
            modifier = sharedFrameModifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            // Without a captured frame the poster stands in, so every card keeps the same shape.
            AsyncImage(
                model = frame ?: anime.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentScale = if (frame != null) ContentScale.Crop else ContentScale.Fit,
            )
            AnimeSourceBadge(
                titleId = anime.id,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            val fraction = progress
                ?.takeIf { it.durationMs > 0L }
                ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
            if (fraction != null && fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(androidx.compose.ui.graphics.Color(0xFFE53935)),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                Text(
                    text = stringResource(
                        R.string.details_continue_episode_position,
                        formatEpisodeNumber(progress.episodeNumber),
                        formatPlaybackTime(progress.positionMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val CONTINUE_CARD_WIDTH = 260.dp
