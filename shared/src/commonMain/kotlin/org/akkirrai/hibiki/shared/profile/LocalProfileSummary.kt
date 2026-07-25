package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
/** Stateless profile summary shared by Android and Desktop hosts. */
@Composable
fun LocalProfileSummary(
    data: LocalProfileData,
    fallbackName: String,
    libraryLabel: String,
    episodesLabel: String,
    modifier: Modifier = Modifier,
) {
    val stats = data.stats()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.MediumCorner),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = data.profileName.ifBlank { fallbackName },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "$libraryLabel: ${stats.libraryTotal}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$episodesLabel: ${stats.completedEpisodes}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
