package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun AnimeDetailsPanel(anime: Anime, isLoading: Boolean, error: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← ${appText(AppTextKey.Home)}") }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(UiDimens.MediumCorner),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(anime.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(anime.subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${anime.status} · ${anime.episodesLabel}", color = MaterialTheme.colorScheme.primary)
                if (isLoading) {
                    CircularProgressIndicator()
                }
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                anime.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyLarge)
                }
                if (anime.genres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(anime.genres) { genre ->
                            FilterChip(selected = false, onClick = { }, label = { Text(genre) })
                        }
                    }
                }
                Button(onClick = { }) { Text(appText(AppTextKey.ExploreCatalog)) }
            }
        }
    }
}
