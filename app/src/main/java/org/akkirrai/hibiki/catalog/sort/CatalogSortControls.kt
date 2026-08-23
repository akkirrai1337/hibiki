package org.akkirrai.hibiki.catalog.sort

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.collect
import org.akkirrai.hibiki.catalog.screen.*

@Composable
fun AppCatalogSortControl(
    sortKey: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    orderContent: @Composable (Modifier) -> Unit,
    menuContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CatalogSortControlHeight),
    ) {
        AppCatalogSortPill(
            sortKey = sortKey,
            icon = icon,
            label = label,
            onClick = { onExpandedChange(!expanded) },
            orderContent = orderContent,
            modifier = Modifier.align(Alignment.Center),
        )
        menuContent()
    }
}

@Composable
fun AppCatalogSortMenuContent(
    title: String,
    sorts: List<CatalogSort>,
    selectedSort: CatalogSort,
    label: (CatalogSort) -> String,
    expanded: Boolean,
    onSortSelected: (CatalogSort) -> Unit,
    orderContent: @Composable (Boolean, Modifier) -> Unit,
) {
    Text(
        text = title,
        fontSize = CatalogSortMenuTitleFontSize,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .padding(top = CatalogSortMenuTitleTopPadding)
            .fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    sorts.forEach { sort ->
        AppCatalogSortMenuItem(
            icon = sort.icon(),
            label = label(sort),
            selected = sort == selectedSort,
            onClick = { onSortSelected(sort) },
            orderContent = { orderModifier -> orderContent(expanded, orderModifier) },
        )
    }
}

@Composable
fun AppCatalogSortVisibilityEffect(
    listState: LazyListState,
    onVisibilityChange: (Boolean) -> Unit,
) {
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (currentIndex, currentOffset) ->
            val isScrollingDown = currentIndex > previousIndex ||
                (currentIndex == previousIndex && currentOffset > previousOffset)
            val isScrollingUp = currentIndex < previousIndex ||
                (currentIndex == previousIndex && currentOffset < previousOffset)
            when {
                isScrollingDown -> onVisibilityChange(false)
                isScrollingUp -> onVisibilityChange(true)
            }
            previousIndex = currentIndex
            previousOffset = currentOffset
        }
    }
}
