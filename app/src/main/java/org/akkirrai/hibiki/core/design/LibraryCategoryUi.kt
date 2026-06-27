package org.akkirrai.hibiki.core.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.core.source.LibraryCategory

fun LibraryCategory.icon(): ImageVector {
    return when (this) {
        LibraryCategory.Watching -> Icons.Filled.PlayArrow
        LibraryCategory.Planned -> Icons.Outlined.BookmarkBorder
        LibraryCategory.Completed -> Icons.Outlined.Check
        LibraryCategory.Dropped -> Icons.Outlined.Close
        LibraryCategory.OnHold -> Icons.Outlined.Pause
        LibraryCategory.Favorite -> Icons.Filled.Bookmark
        LibraryCategory.Saved -> Icons.Outlined.Download
    }
}

fun LibraryCategory?.iconOrDefault(): ImageVector {
    return this?.icon() ?: Icons.Outlined.BookmarkBorder
}
