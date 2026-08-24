package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.catalog.model.AniListCharacter
import org.akkirrai.hibiki.design.component.poster.AppPosterImage

private val DetailsCharacterCardWidth = 96.dp
private val DetailsCharacterImageShape = RoundedCornerShape(12.dp)

@Composable
fun DetailsCharactersSection(
    characters: List<AniListCharacter>,
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
            items(characters, key = { it.name + it.role }) { character ->
                Column(modifier = Modifier.width(DetailsCharacterCardWidth)) {
                    AppPosterImage(
                        primaryUrl = character.imageUrl,
                        contentDescription = character.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clip(DetailsCharacterImageShape),
                        contentScale = ContentScale.Crop,
                        placeholder = { AppDetailsImagePlaceholder(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)) },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    character.voiceActorName?.let { voiceActorName ->
                        Text(
                            text = voiceActorName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
