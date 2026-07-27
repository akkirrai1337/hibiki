package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun DetailsGenresSection(
    genres: List<String>,
    title: String,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(DetailsGenresTitleTopSpacing))
        DetailsSectionTitle(text = title, modifier = Modifier.padding(horizontal = horizontalPadding))
        Spacer(modifier = Modifier.height(DetailsGenresTitleContentSpacing))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(DetailsGenresItemGap),
        ) {
            items(genres.distinct(), key = { it }) { genre ->
                Surface(
                    modifier = Modifier.height(DetailsGenresItemHeight),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(DetailsGenresItemBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                ) {
                    Box(modifier = Modifier.padding(horizontal = DetailsGenresItemHorizontalPadding), contentAlignment = Alignment.Center) {
                        Text(text = genre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
