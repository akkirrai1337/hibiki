package org.akkirrai.hibiki.shared.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
fun AppProfileOverviewContent(
    snapshot: LocalProfileSnapshot,
    bottomContentPadding: Dp,
    totalLabel: String,
    daysLabel: String,
    timeLabel: String,
    recentContent: @Composable () -> Unit,
) {
    ProfileScrollableTab(
        bottomContentPadding = bottomContentPadding,
        verticalSpacing = ProfileMediumPadding,
    ) {
        ProfileStatsRow(
            items = listOf(
                ProfileStatItem(totalLabel, snapshot.libraryTotal.toString()),
                ProfileStatItem(daysLabel, snapshot.activeDaysCount.toString()),
                ProfileStatItem(timeLabel, snapshot.watchTimeLabel),
            ),
        )
        ProfileGenreBars(items = snapshot.genreSegments.toProfileGenreBarItems())
        recentContent()
    }
}
