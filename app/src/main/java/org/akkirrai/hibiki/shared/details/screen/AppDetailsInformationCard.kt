package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DetailsInformationLabels(
    val title: String,
    val status: String,
    val episodes: String,
    val type: String,
    val releaseDate: String,
    val sourceMaterial: String,
    val studio: String,
    val emptyValue: String,
)

data class DetailsInformationIcons(
    val status: DetailsInformationIcon = DetailsInformationIcon.STATUS,
    val episodes: DetailsInformationIcon = DetailsInformationIcon.EPISODES,
    val type: DetailsInformationIcon = DetailsInformationIcon.TYPE,
    val releaseDate: DetailsInformationIcon = DetailsInformationIcon.RELEASE_DATE,
    val sourceMaterial: DetailsInformationIcon = DetailsInformationIcon.SOURCE_MATERIAL,
    val studio: DetailsInformationIcon = DetailsInformationIcon.STUDIO,
)

data class DetailsInformationAccents(
    val status: Color,
    val episodes: Color,
    val type: Color,
    val releaseDate: Color,
    val sourceMaterial: Color,
    val studio: Color,
)

/** Common Details information section; hosts provide localization and iconography. */
@Composable
fun AppDetailsInformationCard(
    heroInfo: DetailsHeroInfo,
    sourceMaterial: String?,
    labels: DetailsInformationLabels,
    icons: DetailsInformationIcons,
    accents: DetailsInformationAccents,
    horizontalPadding: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        add(
            DetailsInformationItem(
                label = labels.status,
                value = heroInfo.status.ifBlank { labels.emptyValue },
                icon = icons.status,
                accent = accents.status,
            )
        )
        add(
            DetailsInformationItem(
                label = labels.episodes,
                value = heroInfo.episodes.ifBlank { labels.emptyValue },
                icon = icons.episodes,
                accent = accents.episodes,
            )
        )
        add(
            DetailsInformationItem(
                label = labels.type,
                value = heroInfo.type,
                icon = icons.type,
                accent = accents.type,
            )
        )
        heroInfo.releaseDate.takeIf(String::isNotBlank)?.let { releaseDate ->
            add(
                DetailsInformationItem(
                    label = labels.releaseDate,
                    value = releaseDate,
                    icon = icons.releaseDate,
                    accent = accents.releaseDate,
                )
            )
        }
        sourceMaterial?.takeIf(String::isNotBlank)?.let { source ->
            add(
                DetailsInformationItem(
                    label = labels.sourceMaterial,
                    value = source,
                    icon = icons.sourceMaterial,
                    accent = accents.sourceMaterial,
                )
            )
        }
        heroInfo.studio.takeIf(String::isNotBlank)?.let { studio ->
            add(
                DetailsInformationItem(
                    label = labels.studio,
                    value = studio,
                    icon = icons.studio,
                    accent = accents.studio,
                )
            )
        }
    }
    DetailsInformationSection(
        title = labels.title,
        items = items,
        horizontalPadding = horizontalPadding,
        modifier = modifier,
    )
}

