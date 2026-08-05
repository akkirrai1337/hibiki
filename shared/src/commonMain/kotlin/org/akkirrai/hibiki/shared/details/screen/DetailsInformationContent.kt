package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun AppDetailsInformationContent(
    heroInfo: DetailsHeroInfo,
    title: String,
    emptyValue: String,
    statusLabel: String,
    episodesLabel: String,
    typeLabel: String,
    releaseDateLabel: String,
    sourceMaterialLabel: String,
    studioLabel: String,
    sourceMaterial: String?,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val items = listOfNotNull(
        DetailsInformationItem(
            label = statusLabel,
            value = heroInfo.status.ifBlank { emptyValue },
            icon = DetailsInformationIcon.STATUS,
            accent = MaterialTheme.colorScheme.tertiary,
        ),
        DetailsInformationItem(
            label = episodesLabel,
            value = heroInfo.episodes.ifBlank { emptyValue },
            icon = DetailsInformationIcon.EPISODES,
            accent = MaterialTheme.colorScheme.primary,
        ),
        DetailsInformationItem(
            label = typeLabel,
            value = heroInfo.type,
            icon = DetailsInformationIcon.TYPE,
            accent = MaterialTheme.colorScheme.secondary,
        ),
        heroInfo.releaseDate.takeIf(String::isNotBlank)?.let { releaseDate ->
            DetailsInformationItem(
                label = releaseDateLabel,
                value = releaseDate,
                icon = DetailsInformationIcon.RELEASE_DATE,
                accent = MaterialTheme.colorScheme.primary,
            )
        },
        sourceMaterial?.let { value ->
            DetailsInformationItem(
                label = sourceMaterialLabel,
                value = value,
                icon = DetailsInformationIcon.SOURCE_MATERIAL,
                accent = MaterialTheme.colorScheme.tertiary,
            )
        },
        heroInfo.studio.takeIf(String::isNotBlank)?.let { studio ->
            DetailsInformationItem(
                label = studioLabel,
                value = studio,
                icon = DetailsInformationIcon.STUDIO,
                accent = Color(0xFFFF9800),
            )
        },
    )
    DetailsInformationSection(
        title = title,
        items = items,
        horizontalPadding = horizontalPadding,
        modifier = modifier,
    )
}
