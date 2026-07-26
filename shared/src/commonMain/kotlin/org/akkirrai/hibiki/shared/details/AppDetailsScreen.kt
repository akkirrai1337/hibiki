package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared production Details shell. Media, image loading and sheets remain
 * explicit host slots, while scrolling, layering and content ordering are
 * shared for every platform.
 */
@Composable
fun AppDetailsScreen(
    listState: LazyListState,
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
    statusBarScrimContent: @Composable BoxScope.() -> Unit,
    backContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 100.dp,
                ),
                content = content,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                statusBarScrimContent()
                backContent()
            }
            overlayContent()
        }
    }
}
