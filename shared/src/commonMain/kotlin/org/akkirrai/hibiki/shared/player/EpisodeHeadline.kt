package org.akkirrai.hibiki.shared.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.akkirrai.hibiki.shared.model.EpisodeProgressStatus
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.model.WatchEpisode

@Composable
fun buildEpisodeHeadline(
    headline: String,
    trailingLabel: String?,
): AnnotatedString {
    if (trailingLabel.isNullOrBlank()) return AnnotatedString(headline)

    return buildAnnotatedString {
        append(headline)
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        ) {
            append("\u2022 $trailingLabel")
        }
    }
}

@Composable
fun buildEpisodeRowHeadline(
    episode: WatchEpisode,
    progress: EpisodeWatchProgress?,
    status: EpisodeProgressStatus,
    watchedHeadline: @Composable (number: String) -> String,
    defaultHeadline: @Composable (number: String) -> String,
    watchedLabel: String,
): AnnotatedString {
    val number = formatEpisodeNumber(episode.number)
    val headline = when (status) {
        EpisodeProgressStatus.Watched -> watchedHeadline(number)
        else -> defaultHeadline(number)
    }
    val trailingLabel = if (
        status == EpisodeProgressStatus.InProgress &&
        progress != null &&
        progress.durationMs > 0L
    ) {
        "${formatEpisodeDuration(progress.positionMs)} / ${formatEpisodeDuration(progress.durationMs)}"
    } else if (status == EpisodeProgressStatus.Watched) {
        watchedLabel
    } else {
        null
    }
    return buildEpisodeHeadline(headline, trailingLabel)
}
