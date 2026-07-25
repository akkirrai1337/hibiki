package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ProfileActivityBarItem(val dateLabel: String, val episodeCount: Int)

@Composable
fun ProfileActivityBarChart(
    days: List<ProfileActivityBarItem>,
    dayWidth: Dp,
    listState: LazyListState,
    dayGap: Dp,
    minScaleEpisodes: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxEpisodes = days.maxOfOrNull(ProfileActivityBarItem::episodeCount)?.coerceAtLeast(minScaleEpisodes) ?: minScaleEpisodes
    LazyRow(state = listState, modifier = modifier.height(142.dp), horizontalArrangement = Arrangement.spacedBy(dayGap), verticalAlignment = Alignment.Bottom) {
        items(days, key = ProfileActivityBarItem::dateLabel) { day ->
            val barHeight = if (day.episodeCount > 0) (18 + (66 * day.episodeCount / maxEpisodes)).dp else 10.dp
            Column(Modifier.width(dayWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(114.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.episodeCount.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.width(18.dp).height(barHeight).clip(RoundedCornerShape(7.dp)).background(if (day.episodeCount > 0) activeColor else inactiveColor))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(day.dateLabel, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
    }
}
