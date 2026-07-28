package org.akkirrai.hibiki.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

@Composable
fun AnimeSourceBadge(
    titleId: String,
    modifier: Modifier = Modifier,
) {
    val source = remember(titleId) {
        AnimeSourceRegistry.descriptorForStoredTitle(titleId)
    }
    org.akkirrai.hibiki.shared.design.component.AppSourceBadge(
        title = source.name,
        modifier = modifier,
        iconContent = { iconModifier ->
            AsyncImage(
                model = source.iconUrl,
                placeholder = painterResource(source.iconRes),
                error = painterResource(source.iconRes),
                contentDescription = null,
                modifier = iconModifier,
            )
        },
    )
}
