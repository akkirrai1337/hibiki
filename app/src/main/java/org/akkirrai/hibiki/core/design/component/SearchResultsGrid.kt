package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.model.Anime

fun LazyGridScope.animeSearchResultsContent(
    items: List<Anime>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onAnimeClick: (Anime) -> Unit,
    metaText: (Anime) -> String,
    onLoadMore: () -> Unit,
    loadMoreLabel: String,
    loadMoreErrorMessage: String? = null,
    loadMoreLoadingLabel: String? = null,
    loadMoreModifier: Modifier = Modifier,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = pluralStringResource(
                R.plurals.search_results_count,
                items.size,
                items.size
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }

    items(
        items = items,
        key = { it.id }
    ) { anime ->
        PosterCard(
            anime = anime,
            metaText = metaText(anime),
            onClick = { onAnimeClick(anime) },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (canLoadMore || isLoadingMore || loadMoreErrorMessage != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            AppLoadMoreBlock(
                label = loadMoreLabel,
                onClick = onLoadMore,
                isLoading = isLoadingMore,
                errorMessage = loadMoreErrorMessage,
                loadingLabel = loadMoreLoadingLabel,
                modifier = loadMoreModifier,
            )
        }
    }
}
