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
import androidx.compose.ui.unit.dp

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
                .height(412.dp),
        ) {
            mediaContent(
                Modifier
                    .fillMaxWidth()
                    .height(224.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 144.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, androidx.compose.material3.MaterialTheme.colorScheme.background),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
                    .align(Alignment.BottomCenter)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            )
            DetailsPosterCard(
                height = 200.dp - posterHeightOffset,
                onClick = onPosterClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = 212.dp + posterHeightOffset)
                    .padding(start = 16.dp),
                poster = posterContent,
            )
            textContent(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .offset(y = 224.dp)
                    .padding(start = 172.dp, end = 16.dp)
                    .height(180.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        actionsContent()
        Spacer(modifier = Modifier.height(16.dp))
    }
}
