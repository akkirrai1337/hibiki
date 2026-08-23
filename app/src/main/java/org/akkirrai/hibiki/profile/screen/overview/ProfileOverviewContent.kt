package org.akkirrai.hibiki.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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

data class ProfileStatItem(val label: String, val value: String)

@Composable
private fun ProfileStatsRow(items: List<ProfileStatItem>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { item -> ProfileStat(item.label, item.value) }
    }
}

@Composable
private fun ProfileStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(value, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    }
}

data class ProfileGenreBarItem(val label: String, val count: Int, val color: Color)

@Composable
private fun ProfileGenreBars(items: List<ProfileGenreBarItem>) {
    if (items.isEmpty()) return
    Row(Modifier.height(IntrinsicSize.Max)) {
        Column(horizontalAlignment = Alignment.End) {
            items.forEach { item ->
                Text(item.label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Column(
            modifier = Modifier.fillMaxHeight().padding(start = ProfileGenreBarsLabelBarGap, end = ProfileGenreBarsEndPadding).widthIn(max = ProfileGenreBarsMaxWidth),
            verticalArrangement = Arrangement.spacedBy(ProfileGenreBarsRowGap),
        ) {
            val highest = items.maxOf { it.count }.coerceAtLeast(1)
            items.forEach { item ->
                Box(
                    modifier = Modifier.fillMaxWidth(item.count / highest.toFloat()).weight(1f).drawBehind {
                        drawRoundRect(color = item.color, size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height))
                    },
                )
            }
        }
    }
}
