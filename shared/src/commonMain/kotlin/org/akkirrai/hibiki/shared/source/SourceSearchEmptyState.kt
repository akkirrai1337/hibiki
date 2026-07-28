package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.AppMessageState

@Composable
fun AppSourceSearchEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SourceSearchEmptyHorizontalPadding),
    )
}
