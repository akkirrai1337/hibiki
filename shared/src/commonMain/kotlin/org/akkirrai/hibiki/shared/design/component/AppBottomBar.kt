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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Platform-neutral item description for the app's compact navigation shell. */
data class AppBottomBarItem(
    val id: String,
    val label: String,
    val icon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector,
)

/**
 * Shared bottom navigation shell.
 *
 * Hosts provide labels and icons because localization and icon packs belong to
 * the platform/application layer. Layout, selected state, insets and motion
 * surface remain identical on every Compose host.
 */
@Composable
fun AppBottomBar(
    items: List<AppBottomBarItem>,
    selectedId: String,
    onItemClick: (AppBottomBarItem) -> Unit,
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
                    .height(BottomBarHeight)
                    .padding(horizontal = BottomBarHorizontalPadding, vertical = BottomBarVerticalPadding),
            ) {
                val itemGap = 4.dp
                val itemWidth = (maxWidth - itemGap * (items.size - 1)).coerceAtLeast(0.dp) / items.size.coerceAtLeast(1)
                val activePillWidth = if (itemWidth < BottomBarActivePillMaxWidth) {
                    (itemWidth - 4.dp).coerceAtLeast(48.dp)
                } else {
                    BottomBarActivePillMaxWidth
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(itemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        AppBottomBarItem(
                            item = item,
                            selected = item.id == selectedId,
                            onClick = { onItemClick(item) },
                            activePillWidth = activePillWidth,
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
    item: AppBottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    activePillWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pillShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .height(BottomBarItemHeight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(width = activePillWidth, height = BottomBarActivePillHeight),
            shape = pillShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = contentColor,
            tonalElevation = if (selected) 2.dp else 0.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon(),
                    contentDescription = item.label,
                    modifier = Modifier.size(BottomBarIconSize),
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.material3.Text(
            text = item.label,
            fontSize = BottomBarLabelSize,
            lineHeight = BottomBarLabelSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else contentColor,
            maxLines = 1,
        )
    }
}

private val BottomBarHeight = 64.dp
private val BottomBarHorizontalPadding = 14.dp
private val BottomBarVerticalPadding = 6.dp
private val BottomBarItemHeight = 48.dp
private val BottomBarActivePillMaxWidth = 68.dp
private val BottomBarActivePillHeight = 30.dp
private val BottomBarIconSize = 22.dp
private val BottomBarLabelSize = 11.sp
