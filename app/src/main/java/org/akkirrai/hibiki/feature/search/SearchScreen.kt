package org.akkirrai.hibiki.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.searchStateGridContent
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.core.model.buildCardMeta

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(LocalContext.current)),
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val loadMoreLabel = stringResource(R.string.action_more)
    val idleTitle = stringResource(R.string.search_start_title)
    val idleMessage = stringResource(R.string.search_idle)
    val emptyTitle = stringResource(R.string.home_search_empty_title)
    val emptyMessage = stringResource(R.string.search_empty)
    val retryLabel = stringResource(R.string.search_retry)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            top = UiDimens.ScreenPadding,
            end = UiDimens.ScreenPadding,
            bottom = UiDimens.ScreenPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::search
            )
        }

        searchStateGridContent(
            state = state.result,
            onAnimeClick = onAnimeClick,
            metaText = { anime -> buildSearchMeta(anime, announcementLabel) },
            onLoadMore = viewModel::loadMore,
            loadMoreLabel = loadMoreLabel,
            idleTitle = idleTitle,
            idleMessage = idleMessage,
            idleIcon = Icons.Outlined.Search,
            idleTopPadding = 64.dp,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            emptyIcon = Icons.Outlined.SearchOff,
            errorModifier = Modifier.padding(top = 24.dp),
            errorActionLabel = retryLabel,
            onErrorActionClick = viewModel::search,
            loadMoreModifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(UiDimens.MediumCorner)),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(UiDimens.MediumCorner),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledIndicatorColor = MaterialTheme.colorScheme.surfaceContainer,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

private fun buildSearchMeta(
    anime: Anime,
    announcementLabel: String,
): String {
    return anime.buildCardMeta(
        announcementLabel = announcementLabel,
    )
}
