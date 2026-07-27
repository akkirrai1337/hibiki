package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun AppDetailsHeroSection(
    posterHeightOffset: Dp,
    onPosterClick: () -> Unit,
    posterContent: @Composable () -> Unit,
    mediaContent: @Composable (Modifier) -> Unit,
    textContent: @Composable (Modifier) -> Unit,
    actionsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DetailsHeroSectionHeight),
        ) {
            mediaContent(
                Modifier
                    .fillMaxWidth()
                    .height(DetailsHeroMediaHeight),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DetailsHeroGradientHeight)
                    .align(Alignment.TopCenter)
                    .offset(y = DetailsHeroGradientOffset)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, androidx.compose.material3.MaterialTheme.colorScheme.background),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DetailsHeroBackgroundHeight)
                    .align(Alignment.BottomCenter)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            )
            DetailsPosterCard(
                height = DetailsHeroPosterBaseHeight - posterHeightOffset,
                onClick = onPosterClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = DetailsHeroPosterOffset + posterHeightOffset)
                    .padding(start = DetailsHeroPosterStartPadding),
                poster = posterContent,
            )
            textContent(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .offset(y = DetailsHeroTextOffset)
                    .padding(start = DetailsHeroTextStartPadding, end = DetailsHeroTextEndPaddingInSection)
                    .height(DetailsHeroTextHeight),
            )
        }
        Spacer(modifier = Modifier.height(DetailsHeroActionsTopSpacing))
        actionsContent()
        Spacer(modifier = Modifier.height(DetailsHeroActionsBottomSpacing))
    }
}
