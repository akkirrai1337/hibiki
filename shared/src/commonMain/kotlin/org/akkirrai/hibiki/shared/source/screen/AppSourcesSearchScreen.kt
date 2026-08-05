package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.state.AppMessageState
import org.akkirrai.hibiki.shared.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.catalog.model.Anime

data class AppSourceSearchSection(
    val id: String,
    val name: String,
    val items: List<Anime> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

/** Shared search-results shell; source search orchestration remains platform-owned. */
@Composable
fun AppSourcesSearchScreen(
    sections: List<AppSourceSearchSection>,
    isSearching: Boolean,
    searchBarContent: @Composable () -> Unit,
    sourceIconContent: @Composable (AppSourceSearchSection) -> Unit,
    metaText: (Anime) -> String,
    posterContent: @Composable androidx.compose.foundation.layout.BoxScope.(Anime) -> Unit,
    onRetry: (String) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    loadingLabel: String,
    errorLabel: String,
    retryLabel: String,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val visibleSections = sections.filter { it.isLoading || it.hasError || it.items.isNotEmpty() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 84.dp,
                end = 12.dp,
                bottom = bottomContentPadding + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            visibleSections.forEach { section ->
                item(key = "search_${section.id}") {
                    AppSourceSearchSectionContent(
                        section = section,
                        sourceIconContent = sourceIconContent,
                        metaText = metaText,
                        posterContent = posterContent,
                        loadingLabel = loadingLabel,
                        errorLabel = errorLabel,
                        retryLabel = retryLabel,
                        onRetry = { onRetry(section.id) },
                        onAnimeClick = onAnimeClick,
                    )
                }
            }
            if (!isSearching && visibleSections.isEmpty()) {
                item(key = "search_empty") {
                    AppMessageState(
                        title = emptyTitle,
                        message = emptyMessage,
                        icon = emptyIcon,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.TopCenter)) { searchBarContent() }
    }
}

@Composable
private fun AppSourceSearchSectionContent(
    section: AppSourceSearchSection,
    sourceIconContent: @Composable (AppSourceSearchSection) -> Unit,
    metaText: (Anime) -> String,
    posterContent: @Composable androidx.compose.foundation.layout.BoxScope.(Anime) -> Unit,
    loadingLabel: String,
    errorLabel: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                sourceIconContent(section)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = section.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        when {
            section.isLoading -> Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(loadingLabel, style = MaterialTheme.typography.bodyMedium)
            }
            section.hasError -> Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(errorLabel, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text(retryLabel) }
            }
            section.items.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.items, key = { it.id }) { anime ->
                    AppPosterAnimeCard(
                        anime = anime,
                        metaText = metaText(anime),
                        onClick = { onAnimeClick(anime) },
                        modifier = Modifier.width(154.dp),
                        posterContent = { posterContent(anime) },
                    )
                }
            }
        }
    }
}
