package org.akkirrai.hibiki.feature.profile

import org.akkirrai.hibiki.shared.profile.normalizePosterUrl
import org.akkirrai.hibiki.shared.profile.ProfileRecentPosterMarker
import org.akkirrai.hibiki.shared.profile.RecentLibraryItem
import org.akkirrai.hibiki.shared.profile.AppProfileRecentLibraryContent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R
import androidx.compose.ui.layout.ContentScale
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.AppPosterLoadingPlaceholder
import org.akkirrai.hibiki.shared.design.component.AppPosterPlaceholder

@Composable
internal fun RecentLibraryCard(
    items: List<RecentLibraryItem>,
    showTitle: Boolean = true,
) {
    AppProfileRecentLibraryContent(
        items = items,
        title = if (showTitle) stringResource(R.string.yummy_account_recent_additions_title) else null,
        emptyText = stringResource(R.string.yummy_account_recent_library_empty),
    )
}
