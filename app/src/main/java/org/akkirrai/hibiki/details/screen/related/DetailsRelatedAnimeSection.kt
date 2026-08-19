package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

data class DetailsRelatedAnimeItem(
    val id: String,
    val title: String,
    val metadata: String,
)

@Composable
fun DetailsRelatedAnimeSection(
    items: List<DetailsRelatedAnimeItem>,
    title: String,
    horizontalPadding: Dp,
    onItemClick: (DetailsRelatedAnimeItem) -> Unit,
    poster: @Composable (DetailsRelatedAnimeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(DetailsRelatedTitleTopSpacing))
        DetailsSectionTitle(title, modifier = Modifier.padding(horizontal = horizontalPadding))
        Spacer(modifier = Modifier.height(DetailsRelatedTitleContentSpacing))
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(DetailsRelatedCarouselHeight),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(DetailsRelatedItemGap),
        ) {
            items(items, key = DetailsRelatedAnimeItem::id) { item ->
                Column(
                    modifier = Modifier
                        .width(DetailsRelatedItemWidth)
                        .clip(RoundedCornerShape(DetailsRelatedItemCornerRadius))
                        .clickable { onItemClick(item) }
                        .padding(bottom = DetailsRelatedItemBottomPadding),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DetailsRelatedPosterHeight)
                            .clip(RoundedCornerShape(DetailsRelatedItemCornerRadius))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        poster(item)
                    }
                    Spacer(modifier = Modifier.height(DetailsRelatedMetadataTitleGap))
                    Text(
                        text = item.metadata,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
