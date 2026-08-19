package org.akkirrai.hibiki.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppProfileActivitySection(
    title: String,
    days: List<ProfileActivityBarItem>,
    listState: LazyListState,
    visibleDays: Int,
    dayGap: Dp,
    minScaleEpisodes: Int,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ProfileActivitySectionVerticalSpacing),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val dayWidth = (maxWidth - (dayGap * (visibleDays - 1))) / visibleDays
            ProfileActivityBarChart(
                days = days,
                dayWidth = dayWidth,
                listState = listState,
                dayGap = dayGap,
                minScaleEpisodes = minScaleEpisodes,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
