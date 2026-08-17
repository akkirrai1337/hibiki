package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppProfileAnalyticsContent(
    snapshot: LocalProfileSnapshot,
    watchTimeTitle: String,
    totalLabel: String,
    genresTitle: String,
    genresLabel: String,
    analyticsTitle: String,
    episodeStat: String,
    watchStat: String,
    activityTitle: String,
    activeColor: Color,
    inactiveColor: Color,
) {
    val pages = remember(
        snapshot.libraryStatusSegments,
        snapshot.genreSegments,
        snapshot.libraryTotal,
        watchTimeTitle,
        totalLabel,
        genresTitle,
        genresLabel,
    ) {
        buildProfileAnalyticsPages(
            snapshot = snapshot,
            watchTimeTitle = watchTimeTitle,
            totalLabel = totalLabel,
            genresTitle = genresTitle,
            genresLabel = genresLabel,
        )
    }
    val firstVisibleActivityDay = remember(snapshot.activityDays.size) {
        (snapshot.activityDays.size - PROFILE_ACTIVITY_VISIBLE_DAYS).coerceAtLeast(0)
    }
    val activityBarItems = remember(snapshot.activityDays) { snapshot.activityDays.toProfileActivityBarItems() }
    val activityListState = rememberLazyListState(
        initialFirstVisibleItemIndex = firstVisibleActivityDay,
    )
    LaunchedEffect(snapshot.activityDays) {
        activityListState.scrollToItem(firstVisibleActivityDay)
    }
    AppProfileAnalyticsCard(
        donutContent = {
            AppProfileAnalyticsDonutPager(
                pages = pages,
                title = analyticsTitle,
                episodeStat = episodeStat,
                watchStat = watchStat,
            )
        },
        activityContent = {
            AppProfileActivitySection(
                title = activityTitle,
                days = activityBarItems,
                listState = activityListState,
                visibleDays = PROFILE_ACTIVITY_VISIBLE_DAYS,
                dayGap = PROFILE_ACTIVITY_DAY_GAP,
                minScaleEpisodes = PROFILE_ACTIVITY_MIN_SCALE_EPISODES,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
            )
        },
    )
}

private const val PROFILE_ACTIVITY_MIN_SCALE_EPISODES = 8
private val PROFILE_ACTIVITY_DAY_GAP = 4.dp
private const val PROFILE_ACTIVITY_VISIBLE_DAYS = 7
