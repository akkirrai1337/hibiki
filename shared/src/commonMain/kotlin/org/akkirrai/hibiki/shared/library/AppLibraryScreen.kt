package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Production library screen boundary shared by every host.
 *
 * The host owns persistence, lifecycle synchronization and localization. This
 * composable owns the screen composition and keeps the optional filter surface
 * as a host-provided platform slot.
 */
@Composable
fun AppLibraryScreen(
    state: LibraryUiState,
    bottomContentPadding: Dp,
    onEntryClick: (LibraryEntry) -> Unit,
    headerContent: @Composable () -> Unit,
    emptyContent: @Composable (filtered: Boolean) -> Unit,
    entryContent: @Composable (LibraryEntry, Modifier) -> Unit,
    filterContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AppLibraryEntriesContent(
            state = state,
            bottomContentPadding = bottomContentPadding,
            onEntryClick = onEntryClick,
            emptyContent = emptyContent,
            entryContent = entryContent,
            headerContent = headerContent,
            modifier = Modifier.fillMaxSize(),
        )
        filterContent?.invoke()
    }
}
