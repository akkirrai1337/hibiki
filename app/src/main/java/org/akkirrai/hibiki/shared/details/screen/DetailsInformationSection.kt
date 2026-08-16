package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

enum class DetailsInformationIcon {
    STATUS,
    EPISODES,
    TYPE,
    RELEASE_DATE,
    SOURCE_MATERIAL,
    STUDIO,
}

data class DetailsInformationItem(
    val label: String,
    val value: String,
    val icon: DetailsInformationIcon,
    val accent: Color,
)

@Composable
fun DetailsInformationSection(
    title: String,
    items: List<DetailsInformationItem>,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DetailsInformationSectionSpacing)) {
        Spacer(modifier = Modifier.height(DetailsInformationTitleTopSpacing))
        DetailsSectionTitle(title, modifier = Modifier.padding(horizontal = horizontalPadding))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(DetailsInformationItemGap),
        ) {
            items(items) { item ->
                DetailsInfoPill(
                    label = item.label,
                    value = item.value,
                    icon = item.icon.toImageVector(),
                    accent = item.accent,
                )
            }
        }
    }
}

private fun DetailsInformationIcon.toImageVector() = when (this) {
    DetailsInformationIcon.STATUS -> Icons.Outlined.Check
    DetailsInformationIcon.EPISODES -> Icons.Outlined.FormatListNumbered
    DetailsInformationIcon.TYPE -> Icons.Outlined.BookmarkBorder
    DetailsInformationIcon.RELEASE_DATE -> Icons.Filled.DateRange
    DetailsInformationIcon.SOURCE_MATERIAL -> Icons.AutoMirrored.Filled.MenuBook
    DetailsInformationIcon.STUDIO -> Icons.Filled.Business
}
