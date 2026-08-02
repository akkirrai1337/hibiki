package org.akkirrai.hibiki.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.source.AppSourceIconImage

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
            AppSourceIconImage(
                url = source.iconUrl,
                placeholder = painterResource(source.iconRes),
                modifier = iconModifier,
            )
        },
    )
}
