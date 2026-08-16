package org.akkirrai.hibiki.shared.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import org.akkirrai.hibiki.R

@Composable
fun AppSourceIconImage(
    url: String?,
    placeholder: Painter? = null,
    sourceId: String? = null,
    modifier: Modifier = Modifier,
) {
    val effectivePlaceholder = placeholder ?: when (sourceId) {
        "yummy-anime" -> painterResource(R.drawable.source_yummy_anime)
        "ani-liberty" -> painterResource(R.drawable.source_ani_liberty)
        else -> null
    }
    AsyncImage(
        model = url,
        placeholder = effectivePlaceholder,
        error = effectivePlaceholder,
        contentDescription = null,
        modifier = modifier,
    )
}
