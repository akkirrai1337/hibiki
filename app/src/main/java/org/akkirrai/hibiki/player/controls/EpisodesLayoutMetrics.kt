package org.akkirrai.hibiki.player

import androidx.compose.ui.unit.dp

val EpisodeRowHorizontalPadding = 12.dp
val EpisodeRowVerticalPadding = 10.dp
val EpisodeRowContentGap = 12.dp
val EpisodeRowProgressTextGap = 4.dp
val EpisodeRowTextGap = 6.dp
val EpisodeNumberTileSize = 52.dp
val EpisodeNumberTileCornerRadius = 15.dp
val EpisodeWatchedBadgeSize = 18.dp
val EpisodeWatchedBadgeIconSize = 12.dp
val EpisodeProgressBarHeight = 3.dp
val EpisodesHeaderSectionGap = 20.dp
val EpisodesHeaderTitleHorizontalPadding = 4.dp
val EpisodeResumeCardCornerRadius = 20.dp
val EpisodeResumeCardPadding = 14.dp
val EpisodeResumeCardContentGap = 12.dp
val EpisodeResumePlayTileSize = 46.dp
val EpisodeResumePlayTileCornerRadius = 15.dp
val EpisodeResumeChevronSize = 22.dp
// EpisodeDownloadActionSize is an invisible click box (no background chip -- see DownloadUi.kt),
// a bit larger than the glyph itself for a comfortable tap target. Both stay small enough to fit
// inside a row's natural (text-only) height so a persistently visible download action doesn't
// force every row taller than one without it -- see EpisodeRow's verticalAlignment =
// CenterVertically, which stretches to fit its tallest child.
val EpisodeDownloadActionSize = 40.dp
val EpisodeDownloadIconSize = 22.dp
val EpisodeDownloadProgressStrokeWidth = 2.dp
val EpisodesListHorizontalPadding = 12.dp
val EpisodesListTopPadding = 56.dp
val EpisodesListBottomPadding = 12.dp
val EpisodesListItemGap = 6.dp
val EpisodeDownloadActionGap = 6.dp
val WatchEmptyStateHorizontalPadding = 24.dp
val EpisodeRowDefaultCornerRadius = 17.dp
const val EpisodeRowSizeAnimationDurationMillis = 220
const val EpisodesPageSize = 24
// Deliberately smaller than DetailsNextEpisodeChip's own sizing (DetailsLayoutMetrics) -- that
// chip is sized for the large details hero, and read as too wide/heavy in a compact list row.
val UpcomingEpisodeChipHorizontalPadding = 6.dp
val UpcomingEpisodeChipVerticalPadding = 2.dp
val UpcomingEpisodeChipContentGap = 3.dp
val UpcomingEpisodeChipIconSize = 12.dp
