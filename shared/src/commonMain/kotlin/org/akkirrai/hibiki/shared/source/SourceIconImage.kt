package org.akkirrai.hibiki.shared.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.source_ani_liberty
import hibiki.shared.generated.resources.source_yummy_anime
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppSourceIconImage(
    url: String?,
    placeholder: Painter? = null,
    sourceId: String? = null,
    modifier: Modifier = Modifier,
) {
    val effectivePlaceholder = placeholder ?: when (sourceId) {
        "yummy-anime" -> painterResource(Res.drawable.source_yummy_anime)
        "ani-liberty" -> painterResource(Res.drawable.source_ani_liberty)
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
