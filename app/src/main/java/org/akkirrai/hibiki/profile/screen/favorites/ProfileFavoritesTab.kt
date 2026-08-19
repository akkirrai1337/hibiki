package org.akkirrai.hibiki.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun AppProfileFavoritesTab(
    isEmpty: Boolean,
    bottomContentPadding: Dp,
    emptyContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(ProfileLargePadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            emptyContent()
        }
    } else {
        ProfileScrollableTab(bottomContentPadding = bottomContentPadding) {
            content()
        }
    }
}
