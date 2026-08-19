package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.navigation.AppBackButton
import org.akkirrai.hibiki.layout.LocalAppLayoutEnvironment

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
        iconContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        },
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
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.58f)),
    )
}
