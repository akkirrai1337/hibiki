package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.model.Anime

@Composable
fun AppFeaturedCard(
    anime: Anime,
    featuredLabel: String,
    meta: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageContent: @Composable BoxScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            imageContent()

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.12f),
                                0.35f to Color.Black.copy(alpha = 0.30f),
                                0.70f to Color.Black.copy(alpha = 0.60f),
                                1f to Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.30f),
                                0.45f to Color.Black.copy(alpha = 0.10f),
                                1f to Color.Black.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 42.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = featuredLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 8f,
                        ),
                    ),
                    color = Color.White,
                )

                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 10f,
                        ),
                    ),
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.50f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                blurRadius = 6f,
                            ),
                        ),
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
