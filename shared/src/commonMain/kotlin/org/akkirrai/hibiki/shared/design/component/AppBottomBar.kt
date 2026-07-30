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
val AppBottomBarContentHeight = 64.dp
val AppBottomBarHorizontalPadding = 14.dp
val AppBottomBarVerticalPadding = 6.dp
val AppBottomBarItemGap = 4.dp
val AppBottomBarActivePillMaxWidth = 68.dp
val AppBottomBarActivePillMinWidth = 48.dp
val AppBottomBarIconSize = 22.dp
val AppBottomBarItemHeight = 48.dp
val AppBottomBarPillHeight = 30.dp
val AppBottomBarPillCornerRadius = 18.dp
val AppBottomBarLabelTopSpacing = 3.dp
val AppBottomBarLabelFontSize = 11.sp

/** Shared bottom navigation geometry and interaction used by platform hosts. */
@Composable
fun AppBottomBar(
    currentDestination: AppTopLevelDestination,
    onDestinationClick: (AppTopLevelDestination) -> Unit,
    iconContent: @Composable (AppTopLevelDestination, Modifier) -> Unit,
    label: @Composable (AppTopLevelDestination) -> String = { appText(it.labelKey) },
    destinations: List<AppTopLevelDestination> = AppTopLevelDestination.entries,
    includeNavigationBarPadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val navigationBarBottomPadding = if (includeNavigationBarPadding) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }

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
                    .height(AppBottomBarContentHeight)
                    .padding(horizontal = AppBottomBarHorizontalPadding, vertical = AppBottomBarVerticalPadding),
            ) {
                val itemGap = AppBottomBarItemGap
                val itemWidth = (maxWidth - itemGap * (destinations.size - 1)) / destinations.size
                val activePillWidth = if (itemWidth < AppBottomBarActivePillMaxWidth) {
                    (itemWidth - itemGap).coerceAtLeast(AppBottomBarActivePillMinWidth)
                } else {
                    AppBottomBarActivePillMaxWidth
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
                            iconContent = { iconContent(destination, Modifier.size(AppBottomBarIconSize)) },
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
    val pillShape: Shape = RoundedCornerShape(AppBottomBarPillCornerRadius)

    Column(
        modifier = modifier
            .height(AppBottomBarItemHeight)
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
                .size(width = activePillWidth, height = AppBottomBarPillHeight)
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

        Spacer(modifier = Modifier.height(AppBottomBarLabelTopSpacing))
        Text(
            text = label,
            fontSize = AppBottomBarLabelFontSize,
            lineHeight = AppBottomBarLabelFontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else contentColor,
            maxLines = 1,
        )
    }
}
