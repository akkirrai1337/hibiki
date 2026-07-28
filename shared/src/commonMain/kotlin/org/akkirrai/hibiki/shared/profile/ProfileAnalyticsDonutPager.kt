package org.akkirrai.hibiki.shared.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ProfileAnalyticsPage(
    val centerPrimary: String,
    val centerSecondary: String,
    val segments: List<ProfileAnalyticsSegment>,
)

data class ProfileAnalyticsSegment(
    val label: String,
    val valueLabel: String,
    val weight: Float,
    val color: Color,
)

@Composable
fun AppProfileAnalyticsDonutPager(
    pages: List<ProfileAnalyticsPage>,
    title: String,
    episodeStat: String,
    watchStat: String,
    modifier: Modifier = Modifier,
) {
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ProfileAnalyticsPagerVerticalSpacing),
    ) {
        ProfileAnalyticsPagerHeader(
            title = title,
            currentPage = currentPage,
            pageCount = pages.size,
            onPrevious = { currentPage -= 1 },
            onNext = { currentPage += 1 },
        )
        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "AnalyticsPage",
        ) { pageIndex ->
            val displayedPage = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ProfileAnalyticsPageHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(ProfileAnalyticsPageVerticalSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ProfileAnalyticsLegendDonutGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileLegendGrid(
                        items = displayedPage.segments.map { segment ->
                            ProfileLegendGridItem(segment.label, segment.valueLabel, segment.color)
                        },
                        modifier = Modifier.weight(1f),
                        columns = 1,
                    )
                    ProfileSegmentDonut(
                        segments = displayedPage.segments.map { segment ->
                            ProfileDonutSegment(segment.weight, segment.color)
                        },
                        centerPrimary = displayedPage.centerPrimary,
                        centerSecondary = displayedPage.centerSecondary,
                        modifier = Modifier.size(ProfileAnalyticsDonutSize),
                        muted = displayedPage.segments.all { it.weight <= 0f },
                    )
                }
                if (pageIndex == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(ProfileAnalyticsStatsSpacing)) {
                        androidx.compose.material3.Text(
                            text = episodeStat,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.Text(
                            text = watchStat,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
