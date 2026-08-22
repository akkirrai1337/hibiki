package org.akkirrai.hibiki.player

import androidx.compose.ui.unit.dp

val EpisodeRowHorizontalPadding = 16.dp
val EpisodeRowVerticalPadding = 12.dp
val EpisodeRowContentGap = 12.dp
val EpisodeRowProgressTextGap = 4.dp
val EpisodeRowTextGap = 6.dp
// EpisodeDownloadActionSize is an invisible click box (no background chip -- see DownloadUi.kt),
// a bit larger than the glyph itself for a comfortable tap target. Both stay small enough to fit
// inside a row's natural (text-only) height so a persistently visible download action doesn't
// force every row taller than one without it -- see EpisodeRow's verticalAlignment =
// CenterVertically, which stretches to fit its tallest child.
val EpisodeDownloadActionSize = 32.dp
val EpisodeDownloadIconSize = 22.dp
val EpisodeDownloadProgressStrokeWidth = 2.dp
val EpisodesListHorizontalPadding = 12.dp
val EpisodesListTopPadding = 56.dp
val EpisodesListBottomPadding = 12.dp
val EpisodesListItemGap = 4.dp
val EpisodeDownloadActionGap = 6.dp
val WatchEmptyStateHorizontalPadding = 24.dp
val EpisodeRowDefaultCornerRadius = 0.dp
const val EpisodeRowSizeAnimationDurationMillis = 220
// Deliberately smaller than DetailsNextEpisodeChip's own sizing (DetailsLayoutMetrics) -- that
// chip is sized for the large details hero, and read as too wide/heavy in a compact list row.
val UpcomingEpisodeChipHorizontalPadding = 6.dp
val UpcomingEpisodeChipVerticalPadding = 2.dp
val UpcomingEpisodeChipContentGap = 3.dp
val UpcomingEpisodeChipIconSize = 12.dp
