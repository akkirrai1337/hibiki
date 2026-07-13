package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.animeresolver.model.SearchFilterOption
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeSearchFiltersScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    var pendingFilters by remember(state.searchFilters) { mutableStateOf(state.searchFilters) }
    var yearFromText by remember(state.searchFilters) {
        mutableStateOf(state.searchFilters.yearFrom?.toString().orEmpty())
    }
    var yearToText by remember(state.searchFilters) {
        mutableStateOf(state.searchFilters.yearTo?.toString().orEmpty())
    }
    var genreSelectionMode by remember { mutableStateOf<GenreSelectionMode?>(null) }
    var genreSearchQuery by remember { mutableStateOf("") }
    var openSelector by remember { mutableStateOf<FilterSelector?>(null) }

    BackHandler(enabled = isImeVisible || genreSelectionMode != null) {
        if (isImeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else {
            genreSelectionMode = null
            genreSearchQuery = ""
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FiltersHeader(
                title = stringResource(
                    if (genreSelectionMode == null) {
                        R.string.search_filters_title
                    } else {
                        R.string.search_filters_genres
                    }
                ),
                onBackClick = {
                    if (genreSelectionMode != null) {
                        genreSelectionMode = null
                        genreSearchQuery = ""
                    } else {
                        onBackClick()
                    }
                },
            )
        },
        bottomBar = {
            if (genreSelectionMode == null) {
                FiltersActions(
                    onReset = {
                        pendingFilters = HomeSearchFilters()
                        yearFromText = ""
                        yearToText = ""
                    },
                    onApply = {
                        viewModel.applySearchFilters(
                            pendingFilters.copy(
                                yearFrom = yearFromText.toIntOrNull(),
                                yearTo = yearToText.toIntOrNull(),
                            )
                        )
                        onBackClick()
                    },
                )
            } else {
                GenreSelectionAction(onDone = { genreSelectionMode = null })
            }
        },
    ) { innerPadding ->
        when {
            state.isSearchFilterCatalogLoading && state.searchFilterCatalog == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.searchFilterCatalog == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(UiDimens.ScreenPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_filters_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                val catalog = state.searchFilterCatalog ?: return@Scaffold
                openSelector?.let { selector ->
                    val options = when (selector) {
                        FilterSelector.Sort -> catalog.sortOptions
                        FilterSelector.Type -> catalog.typeOptions
                        FilterSelector.Status -> catalog.statusOptions
                    }
                    val selectedId = when (selector) {
                        FilterSelector.Sort -> pendingFilters.sortAlias
                        FilterSelector.Type -> pendingFilters.typeAlias
                        FilterSelector.Status -> pendingFilters.statusAlias
                    }
                    FilterSelectorSheet(
                        title = stringResource(
                            when (selector) {
                                FilterSelector.Sort -> R.string.search_filters_sort
                                FilterSelector.Type -> R.string.search_filters_type
                                FilterSelector.Status -> R.string.search_filters_status
                            }
                        ),
                        options = options,
                        selectedId = selectedId,
                        allowEmpty = selector != FilterSelector.Sort,
                        onSelect = { selected ->
                            pendingFilters = when (selector) {
                                FilterSelector.Sort -> pendingFilters.copy(
                                    sortAlias = selected ?: "relevance"
                                )
                                FilterSelector.Type -> pendingFilters.copy(typeAlias = selected)
                                FilterSelector.Status -> pendingFilters.copy(statusAlias = selected)
                            }
                            openSelector = null
                        },
                        onDismiss = { openSelector = null },
                    )
                }
                val activeGenreMode = genreSelectionMode
                if (activeGenreMode != null) {
                    val visibleGenres = remember(catalog.genreOptions, genreSearchQuery) {
                        val query = genreSearchQuery.trim()
                        if (query.isBlank()) {
                            catalog.genreOptions
                        } else {
                            catalog.genreOptions.filter {
                                it.title.contains(query, ignoreCase = true)
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = UiDimens.ScreenPadding,
                            top = innerPadding.calculateTopPadding(),
                            end = UiDimens.ScreenPadding,
                            bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedTextField(
                                value = genreSearchQuery,
                                onValueChange = { genreSearchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                placeholder = {
                                    Text(stringResource(R.string.search_filters_genres_search))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            )
                        }
                        items(visibleGenres, key = { it.id }) { genre ->
                            val selected = when (activeGenreMode) {
                                GenreSelectionMode.Include ->
                                    genre.id in pendingFilters.includedGenreAliases
                                GenreSelectionMode.Exclude ->
                                    genre.id in pendingFilters.excludedGenreAliases
                            }
                            GenreSelectionChip(
                                option = genre,
                                selected = selected,
                                mode = activeGenreMode,
                                onClick = {
                                    val included =
                                        pendingFilters.includedGenreAliases.toMutableSet()
                                    val excluded =
                                        pendingFilters.excludedGenreAliases.toMutableSet()
                                    when (activeGenreMode) {
                                        GenreSelectionMode.Include -> {
                                            if (!included.add(genre.id)) included.remove(genre.id)
                                            excluded.remove(genre.id)
                                        }
                                        GenreSelectionMode.Exclude -> {
                                            if (!excluded.add(genre.id)) excluded.remove(genre.id)
                                            included.remove(genre.id)
                                        }
                                    }
                                    pendingFilters = pendingFilters.copy(
                                        includedGenreAliases = included,
                                        excludedGenreAliases = excluded,
                                    )
                                }
                            )
                        }
                    }
                    return@Scaffold
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = UiDimens.ScreenPadding,
                        top = innerPadding.calculateTopPadding(),
                        end = UiDimens.ScreenPadding,
                        bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        CompactFilterGroup {
                            FilterDropdown(
                                label = stringResource(R.string.search_filters_sort),
                                options = catalog.sortOptions,
                                selectedId = pendingFilters.sortAlias,
                                onClick = { openSelector = FilterSelector.Sort },
                            )
                            GroupDivider()
                            FilterDropdown(
                                label = stringResource(R.string.search_filters_type),
                                options = catalog.typeOptions,
                                selectedId = pendingFilters.typeAlias,
                                allowEmpty = true,
                                onClick = { openSelector = FilterSelector.Type },
                            )
                            GroupDivider()
                            FilterDropdown(
                                label = stringResource(R.string.search_filters_status),
                                options = catalog.statusOptions,
                                selectedId = pendingFilters.statusAlias,
                                allowEmpty = true,
                                onClick = { openSelector = FilterSelector.Status },
                            )
                        }
                    }

                    item {
                        YearFilterGroup {
                            Text(
                                text = stringResource(R.string.search_filters_year),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                YearField(
                                    value = yearFromText,
                                    onValueChange = { yearFromText = it },
                                    label = stringResource(R.string.search_filters_year_from),
                                    modifier = Modifier.weight(1f),
                                )
                                YearField(
                                    value = yearToText,
                                    onValueChange = { yearToText = it },
                                    label = stringResource(R.string.search_filters_year_to),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    item {
                        CompactFilterGroup {
                            FilterNavigationRow(
                                title = stringResource(R.string.search_filters_genres),
                                selectionCount = pendingFilters.includedGenreAliases.size,
                                onClick = {
                                    genreSelectionMode = GenreSelectionMode.Include
                                },
                            )
                            GroupDivider()
                            FilterNavigationRow(
                                title = stringResource(R.string.search_filters_excluded_genres),
                                selectionCount = pendingFilters.excludedGenreAliases.size,
                                onClick = {
                                    genreSelectionMode = GenreSelectionMode.Exclude
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersHeader(
    title: String,
    onBackClick: () -> Unit,
) {
    AppFloatingHeader(
        title = title,
        onBackClick = onBackClick,
        includeStatusBarsPadding = false,
        modifier = Modifier,
    )
}

@Composable
private fun GenreSelectionAction(
    onDone: () -> Unit,
) {
    BottomActionSurface {
        Button(
            onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
        ) {
            Text(stringResource(R.string.search_filters_apply))
        }
    }
}

@Composable
private fun FiltersActions(
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    BottomActionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onReset,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(stringResource(R.string.search_filters_reset))
            }
            Button(
                onClick = onApply,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
            ) {
                Text(stringResource(R.string.search_filters_apply))
            }
        }
    }
}

@Composable
private fun BottomActionSurface(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Box(
            modifier = Modifier.padding(
                start = 28.dp,
                top = 6.dp,
                end = 28.dp,
            )
        ) {
            content()
        }
    }
}

@Composable
private fun CompactFilterGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

@Composable
private fun YearFilterGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 24.dp,
                top = 12.dp,
                end = 24.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
    )
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<SearchFilterOption>,
    selectedId: String?,
    allowEmpty: Boolean = false,
    onClick: () -> Unit,
) {
    val emptyLabel = stringResource(R.string.search_filters_not_selected)
    val selectedTitle = options.firstOrNull { it.id == selectedId }?.title
        ?: if (allowEmpty) emptyLabel else options.firstOrNull()?.title.orEmpty()

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 58.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = selectedTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSelectorSheet(
    title: String,
    options: List<SearchFilterOption>,
    selectedId: String?,
    allowEmpty: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val emptyLabel = stringResource(R.string.search_filters_not_selected)
    val choices = buildList {
        if (allowEmpty) add(SelectorChoice(id = null, title = emptyLabel))
        addAll(options.map { SelectorChoice(id = it.id, title = it.title) })
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.search_filters_choose_option),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
                ),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    lazyItems(choices, key = { it.id ?: "empty" }) { choice ->
                        SelectorSheetItem(
                            title = choice.title,
                            selected = choice.id == selectedId,
                            onClick = { onSelect(choice.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorSheetItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        } else {
            Color.Transparent
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.26f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 50.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private data class SelectorChoice(
    val id: String?,
    val title: String,
)

@Composable
private fun YearField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        placeholder = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(17.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun FilterNavigationRow(
    title: String,
    selectionCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (selectionCount > 0) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
                ) {
                    Text(
                        text = stringResource(
                            R.string.search_filters_selected_count,
                            selectionCount,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenreSelectionChip(
    option: SearchFilterOption,
    selected: Boolean,
    mode: GenreSelectionMode,
    onClick: () -> Unit,
) {
    val isExclude = mode == GenreSelectionMode.Exclude
    val selectedColor = if (isExclude) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val selectedContentColor = if (isExclude) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            selectedColor
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.66f)
        },
        contentColor = if (selected) {
            selectedContentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = if (isExclude) Icons.Outlined.Block else Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = option.title,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class GenreSelectionMode {
    Include,
    Exclude,
}

private enum class FilterSelector {
    Sort,
    Type,
    Status,
}
