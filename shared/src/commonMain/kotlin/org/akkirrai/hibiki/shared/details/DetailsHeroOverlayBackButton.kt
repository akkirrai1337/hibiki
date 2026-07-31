package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppBackButton
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment

fun detailsHeroOverlayBackButtonPadding(topSystemInset: Dp): PaddingValues = PaddingValues(
    start = UiDimens.ScreenPadding,
    top = topSystemInset + DetailsHeroOverlayBackButtonTopPadding,
)

@Composable
fun AppDetailsHeroOverlayBackButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    AppBackButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier
            .then(
                if (layoutEnvironment.isProvided) {
                    Modifier.padding(detailsHeroOverlayBackButtonPadding(layoutEnvironment.topSystemInset))
                } else {
                    Modifier.statusBarsPadding().padding(
                        start = UiDimens.ScreenPadding,
                        top = DetailsHeroOverlayBackButtonTopPadding,
                    )
                },
            )
    )
}
