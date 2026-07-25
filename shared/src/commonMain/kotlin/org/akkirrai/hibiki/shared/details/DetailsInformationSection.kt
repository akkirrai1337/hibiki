package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DetailsInformationItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
fun DetailsInformationSection(
    title: String,
    items: List<DetailsInformationItem>,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        DetailsSectionTitle(title, modifier = Modifier.padding(horizontal = horizontalPadding))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { item ->
                DetailsInfoPill(
                    label = item.label,
                    value = item.value,
                    icon = item.icon,
                    accent = item.accent,
                )
            }
        }
    }
}
