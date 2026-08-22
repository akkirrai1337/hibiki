package org.akkirrai.hibiki.player

import androidx.compose.ui.unit.dp

val EpisodeRowHorizontalPadding = 16.dp
val EpisodeRowVerticalPadding = 12.dp
val EpisodeRowContentGap = 12.dp
val EpisodeRowProgressTextGap = 4.dp
val EpisodeRowTextGap = 6.dp
// Small enough to fit inside a row's natural (text-only) height so a persistently visible
// download action no longer forces every row taller than one without it -- see EpisodeRow's
// verticalAlignment = CenterVertically, which stretches to fit its tallest child.
val EpisodeDownloadActionSize = 28.dp
val EpisodeDownloadIconSize = 16.dp
val EpisodeDownloadProgressStrokeWidth = 2.dp
val EpisodesListHorizontalPadding = 12.dp
val EpisodesListTopPadding = 56.dp
val EpisodesListBottomPadding = 12.dp
val EpisodesListItemGap = 4.dp
val EpisodeDownloadActionGap = 6.dp
val WatchEmptyStateHorizontalPadding = 24.dp
val EpisodeRowDefaultCornerRadius = 0.dp
const val EpisodeRowSizeAnimationDurationMillis = 220
