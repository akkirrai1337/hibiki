package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.component.search.AppSearchTopBar

@Composable
fun AppMihonSourcesToolbar(
    title: String,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onFilterClick: () -> Unit,
    searchPlaceholder: String,
    filterContentDescription: String,
    showFilter: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    onRefresh: (() -> Unit)? = null,
    onAddClick: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSearchFocusChanged: (Boolean) -> Unit = {},
    tabContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchOpen) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
                AppSearchTopBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClear = onClearSearch,
                    placeholder = searchPlaceholder,
                    filterContentDescription = filterContentDescription,
                    clearContentDescription = searchPlaceholder,
                    searchIcon = Icons.Outlined.Search,
                    filterIcon = Icons.Outlined.FilterList,
                    clearIcon = Icons.Outlined.Close,
                    showFilterButton = false,
                    barHeight = 48.dp,
                    innerHeight = 46.dp,
                    onFocusChanged = onSearchFocusChanged,
                    modifier = Modifier.weight(1f),
                )
                if (showFilter) {
                    IconButton(onClick = onFilterClick) {
                        Icon(Icons.Outlined.FilterList, contentDescription = filterContentDescription)
                    }
                }
                onRefresh?.let { refresh ->
                    IconButton(onClick = refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                }
                onAddClick?.let { add ->
                    IconButton(onClick = add) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                }
            } else {
                onBack?.let { back ->
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
                Text(
                    text = title,
                    style = titleStyle,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = searchPlaceholder)
                }
                if (showFilter) {
                    IconButton(onClick = onFilterClick) {
                        Icon(Icons.Outlined.FilterList, contentDescription = filterContentDescription)
                    }
                }
                onRefresh?.let { refresh ->
                    IconButton(onClick = refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                }
                onAddClick?.let { add ->
                    IconButton(onClick = add) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                }
            }
        }
        tabContent()
    }
}

@Composable
fun AppSourcesTabs(
    selectedTab: Int,
    sourcesLabel: String,
    extensionsLabel: String,
    onSourcesSelected: () -> Unit,
    onExtensionsSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.background,
        divider = {},
    ) {
        Tab(
            onClick = onExtensionsSelected,
            selected = selectedTab == 0,
            text = {
                Text(
                    text = extensionsLabel,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selectedTab == 0) 1f else 0.5f),
                    maxLines = 1,
                )
            },
            modifier = Modifier.padding(horizontal = 1.dp, vertical = 4.dp).clip(CircleShape),
        )
        Tab(
            selected = selectedTab == 1,
            onClick = onSourcesSelected,
            text = {
                Text(
                    text = sourcesLabel,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selectedTab == 1) 1f else 0.5f),
                    maxLines = 1,
                )
            },
            modifier = Modifier.padding(horizontal = 1.dp, vertical = 4.dp).clip(CircleShape),
        )
    }
}

