package org.akkirrai.hibiki.shared.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage

@Composable
fun AppSourceIconImage(
    url: String?,
    placeholder: Painter,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = url,
        placeholder = placeholder,
        error = placeholder,
        contentDescription = null,
        modifier = modifier,
    )
}
