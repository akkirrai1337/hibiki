package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFilterClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    org.akkirrai.hibiki.shared.design.component.AppSearchTopBar(
        query = query,
        onQueryChange = onQueryChange,
        onClear = onClear,
        placeholder = stringResource(R.string.search_placeholder),
        filterContentDescription = stringResource(R.string.search_filters),
        clearContentDescription = stringResource(R.string.home_search_clear),
        searchIcon = Icons.Outlined.Search,
        filterIcon = Icons.Outlined.FilterList,
        clearIcon = Icons.Outlined.Close,
        onFilterClick = onFilterClick,
        modifier = modifier,
    )
}
