package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppBackButton

@Composable
fun AppDetailsHeroOverlayBackButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AppBackButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = UiDimens.ScreenPadding, top = DetailsHeroOverlayBackButtonTopPadding),
    )
}
