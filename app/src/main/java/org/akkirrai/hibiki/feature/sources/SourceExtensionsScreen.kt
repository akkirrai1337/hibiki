package org.akkirrai.hibiki.feature.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

/**
 * Sources tab: browsing/installing extensions from a repository isn't built yet, so this only
 * lists the sources actually available today -- the ones compiled into the app -- and lets you
 * pick which one is active, the same job the old Settings > Anime source picker did. The Sources
 * (repository) tab shows where installable sources will come from once that exists.
 *
 * The toolbar/search/filter/refresh chrome and the per-source "Manage" info screen match the
 * original Mihon-style design visually, even though refresh has nothing to fetch and the info
 * screen has nothing to uninstall/update -- there's no fake destructive/network action behind
 * them, just the real "select this source" action and a read-only detail view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceExtensionsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var languageFilterOpen by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf(emptySet<String>()) }
    var infoScreenSourceId by rememberSaveable { mutableStateOf<String?>(null) }

    val preferences = LocalAppPreferences.current
    val selectedSource = LocalAppPreferencesState.current.animeSource
    val haptic = LocalHapticFeedback.current

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val tabScope = rememberCoroutineScope()
    val extensionsTabSelected = pagerState.currentPage == 0

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { selectedTab = it }
    }
    BackHandler(enabled = searchOpen) {
        query = ""
        searchOpen = false
    }

    val infoSourceId = infoScreenSourceId
    if (infoSourceId != null) {
        val source = AnimeSourceRegistry.sources.firstOrNull { it.id.value == infoSourceId }
        if (source != null) {
            SourceInfoScreen(
                source = source,
                isSelected = source.id == selectedSource,
                onBack = { infoScreenSourceId = null },
                onSelect = {
                    preferences.setAnimeSource(source.id)
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        SourceExtensionsToolbar(
            searchOpen = searchOpen,
            showFilter = extensionsTabSelected,
            query = query,
            onQueryChange = { query = it },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                query = ""
                searchOpen = false
            },
            onFilterClick = { languageFilterOpen = true },
            onRefresh = {},
        )
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(0) } },
                text = {
                    Text(
                        text = stringResource(R.string.source_extensions_tab_extensions),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Text(
                        text = stringResource(R.string.source_extensions_tab_sources),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            if (page == 0) {
                val visibleSources = AnimeSourceRegistry.sources.filter { source ->
                    val matchesQuery = query.isBlank() || source.name.contains(query, ignoreCase = true)
                    val matchesLanguage = selectedLanguages.isEmpty() || source.language.tag in selectedLanguages
                    matchesQuery && matchesLanguage
                }
                BuiltInSourcesList(
                    sources = visibleSources,
                    selectedSource = selectedSource,
                    bottomContentPadding = bottomContentPadding,
                    onSourceSelected = { source ->
                        preferences.setAnimeSource(source.id)
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                    onManageClick = { source -> infoScreenSourceId = source.id.value },
                )
            } else {
                SourceRepositoryPlaceholder(bottomContentPadding = bottomContentPadding)
            }
        }
    }

    if (languageFilterOpen) {
        SourceLanguageFilterDialog(
            languages = AnimeSourceRegistry.sources.map { it.language.tag }.distinct(),
            selectedLanguages = selectedLanguages,
            onLanguageToggle = { language ->
                selectedLanguages = if (language in selectedLanguages) {
                    selectedLanguages - language
                } else {
                    selectedLanguages + language
                }
            },
            onDismiss = { languageFilterOpen = false },
        )
    }
}

@Composable
private fun SourceExtensionsToolbar(
    searchOpen: Boolean,
    showFilter: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onFilterClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchOpen) {
            IconButton(onClick = onCloseSearch) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        } else {
            Text(
                text = stringResource(R.string.nav_sources),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.source_extensions_search),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (showFilter) {
            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.source_extensions_filter_languages),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.source_extensions_refresh),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun BuiltInSourcesList(
    sources: List<AnimeSourceDescriptor>,
    selectedSource: SourceId,
    bottomContentPadding: Dp,
    onSourceSelected: (AnimeSourceDescriptor) -> Unit,
    onManageClick: (AnimeSourceDescriptor) -> Unit,
) {
    if (sources.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.source_extensions_empty),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = bottomContentPadding + 16.dp,
        ),
    ) {
        items(sources, key = { it.id.value }) { source ->
            BuiltInSourceRow(
                source = source,
                selected = source.id == selectedSource,
                onClick = { onSourceSelected(source) },
                onManageClick = { onManageClick(source) },
            )
        }
    }
}

@Composable
private fun BuiltInSourceRow(
    source: AnimeSourceDescriptor,
    selected: Boolean,
    onClick: () -> Unit,
    onManageClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = source.iconUrl,
            placeholder = painterResource(source.iconRes),
            error = painterResource(source.iconRes),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.language.tag.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        FilledTonalButton(onClick = onManageClick) {
            Text(stringResource(R.string.source_extensions_manage))
        }
    }
}

@Composable
private fun SourceInfoScreen(
    source: AnimeSourceDescriptor,
    isSelected: Boolean,
    onBack: () -> Unit,
    onSelect: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.source_extensions_package_info),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = source.iconUrl,
                    placeholder = painterResource(source.iconRes),
                    error = painterResource(source.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp).clip(CircleShape),
                )
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = source.id.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                SourcePackageInfoValue(
                    value = source.language.tag.uppercase(),
                    label = stringResource(R.string.source_extensions_language),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
            ) {
                Button(
                    onClick = onSelect,
                    enabled = !isSelected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (isSelected) R.string.source_extensions_selected else R.string.source_extensions_select,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePackageInfoValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SourceLanguageFilterDialog(
    languages: List<String>,
    selectedLanguages: Set<String>,
    onLanguageToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_extensions_filter_languages)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                languages.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageToggle(language) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(language.uppercase(), style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = language in selectedLanguages,
                            onCheckedChange = { onLanguageToggle(language) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SourceRepositoryPlaceholder(bottomContentPadding: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = bottomContentPadding + 16.dp),
    ) {
        AppTonalSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = SOURCE_REPOSITORY_DISPLAY_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.source_extensions_repository_coming_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val SOURCE_REPOSITORY_DISPLAY_NAME = "akkirrai1337/hibiki-sources"
