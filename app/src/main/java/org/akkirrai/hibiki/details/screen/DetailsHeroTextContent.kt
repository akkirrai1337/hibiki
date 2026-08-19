package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun AppDetailsHeroTextContent(
    title: String,
    description: String,
    backgroundColor: Color,
    onTitleClick: () -> Unit,
    ratingsContent: (@Composable () -> Unit)?,
    nextEpisodeContent: (@Composable () -> Unit)?,
    expandIconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DetailsHeroTextCornerRadius))
            .clickable(onClick = onTitleClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = DetailsHeroTextEndPadding),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ratingsContent?.invoke()
            nextEpisodeContent?.let { content ->
                Box(modifier = Modifier.padding(top = DetailsHeroTextNextEpisodeTopPadding)) {
                    content()
                }
            }
            if (description.isNotBlank()) {
                DetailsNestedScrollableContent(
                    modifier = Modifier.weight(1f),
                    gradientColor = backgroundColor,
                ) { contentModifier ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
                        modifier = contentModifier,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = DetailsHeroTextExpandIconTopPadding)
                .size(DetailsHeroTextExpandIconSize),
            contentAlignment = Alignment.Center,
        ) {
            expandIconContent()
        }
    }
}
