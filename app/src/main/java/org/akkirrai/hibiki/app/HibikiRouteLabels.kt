package org.akkirrai.hibiki.app

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

@Composable
internal fun LibraryCategory.libraryText(): String = when (this) {
    LibraryCategory.Watching -> appText(AppTextKey.LibraryWatching)
    LibraryCategory.Planned -> appText(AppTextKey.LibraryPlanned)
    LibraryCategory.Completed -> appText(AppTextKey.LibraryCompleted)
    LibraryCategory.Dropped -> appText(AppTextKey.LibraryDropped)
    LibraryCategory.OnHold -> appText(AppTextKey.LibraryOnHold)
    LibraryCategory.Favorite -> appText(AppTextKey.LibraryFavorite)
    LibraryCategory.Saved -> appText(AppTextKey.LibrarySaved)
    // Never actually rendered -- Recent is filtered out of every category list/tab/picker
    // before a label lookup happens. Falls back to the closest real label just in case.
    LibraryCategory.Recent -> appText(AppTextKey.LibraryWatching)
}

internal fun NotificationPermissionState.textKey(): AppTextKey = when (this) {
    NotificationPermissionState.NOT_ASKED -> AppTextKey.SettingsNotificationsStatus
    NotificationPermissionState.GRANTED -> AppTextKey.SettingsNotificationsGranted
    NotificationPermissionState.DENIED -> AppTextKey.SettingsNotificationsDenied
}
