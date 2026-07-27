package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.text.appText

val AppBottomBarHeight = 64.dp
val AppBottomBarContentExtraPadding = 12.dp

/** Shared bottom navigation geometry and interaction used by platform hosts. */
@Composable
fun AppBottomBar(
    currentDestination: AppTopLevelDestination,
    onDestinationClick: (AppTopLevelDestination) -> Unit,
    iconContent: @Composable (AppTopLevelDestination) -> Unit,
    label: @Composable (AppTopLevelDestination) -> String = { appText(it.labelKey) },
    destinations: List<AppTopLevelDestination> = AppTopLevelDestination.entries,
    modifier: Modifier = Modifier,
) {
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navigationBarBottomPadding),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                val itemGap = 4.dp
                val itemWidth = (maxWidth - itemGap * (destinations.size - 1)) / destinations.size
                val activePillWidth = if (itemWidth < 68.dp) {
                    (itemWidth - 4.dp).coerceAtLeast(48.dp)
                } else {
                    68.dp
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(itemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEach { destination ->
                        AppBottomBarItem(
                            destination = destination,
                            selected = currentDestination == destination,
                            onClick = { onDestinationClick(destination) },
                            activePillWidth = activePillWidth,
                            iconContent = { iconContent(destination) },
                            label = label(destination),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBottomBarItem(
    destination: AppTopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    activePillWidth: Dp,
    iconContent: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pillShape: Shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(width = activePillWidth, height = 30.dp)
                .clip(pillShape),
            shape = pillShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = contentColor,
            tonalElevation = if (selected) 2.dp else 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                iconContent()
            }
        }

        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else contentColor,
            maxLines = 1,
        )
    }
}
