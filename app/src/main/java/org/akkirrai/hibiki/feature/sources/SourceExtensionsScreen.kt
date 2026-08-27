package org.akkirrai.hibiki.feature.sources

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppTonalSurface

/**
 * Visual-only port of the Mihon-style extension marketplace screen: browsing/installing is not
 * wired to a real repository yet, this only reproduces the Extensions/Sources tabs UI.
 */
@Composable
fun SourceExtensionsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var extensionsQuery by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var languageFilterOpen by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf(emptySet<String>()) }

    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.source_extensions_repository_copied)

    Column(modifier = modifier.fillMaxSize()) {
        SourceExtensionsTopBar(
            searchOpen = searchOpen,
            showFilter = selectedTab == 0,
            query = extensionsQuery,
            onQueryChange = { extensionsQuery = it },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                searchOpen = false
                extensionsQuery = ""
            },
            onFilterClick = { languageFilterOpen = true },
            onRefresh = {},
        )
        SourceExtensionsTabRow(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                searchOpen = false
                extensionsQuery = ""
            },
        )
        when (selectedTab) {
            0 -> {
                val visibleListings = SOURCE_EXTENSION_LISTINGS.filter { listing ->
                    val matchesQuery = extensionsQuery.isBlank() ||
                        listing.name.contains(extensionsQuery, ignoreCase = true)
                    val matchesLanguage = selectedLanguages.isEmpty() || listing.language in selectedLanguages
                    matchesQuery && matchesLanguage
                }
                SourceExtensionsList(
                    listings = visibleListings,
                    bottomContentPadding = bottomContentPadding,
                )
            }
            else -> SourceRepositoryList(
                onOpenRepository = { uriHandler.openUri(SOURCE_REPOSITORY_BROWSE_URL) },
                onCopyRepository = {
                    clipboardManager.setText(AnnotatedString(SOURCE_REPOSITORY_INDEX_URL))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                },
                bottomContentPadding = bottomContentPadding,
            )
        }
    }

    if (languageFilterOpen) {
        SourceLanguageFilterDialog(
            languages = SOURCE_EXTENSION_LISTINGS.map(SourceExtensionListing::language).distinct(),
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
private fun SourceExtensionsTopBar(
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
            .padding(horizontal = 20.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchOpen) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        } else {
            Text(
                text = stringResource(R.string.nav_sources),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = if (searchOpen) onCloseSearch else onOpenSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.source_extensions_search),
            )
        }
        if (showFilter) {
            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.source_extensions_filter_languages),
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.source_extensions_refresh),
            )
        }
    }
}

@Composable
private fun SourceExtensionsTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        SourceExtensionsTab(
            label = stringResource(R.string.source_extensions_tab_extensions),
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            modifier = Modifier.weight(1f),
        )
        SourceExtensionsTab(
            label = stringResource(R.string.source_extensions_tab_sources),
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourceExtensionsTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .height(2.dp)
                .fillMaxWidth(0.6f)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
        )
    }
}

@Composable
private fun SourceExtensionsList(
    listings: List<SourceExtensionListing>,
    bottomContentPadding: Dp,
) {
    if (listings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.source_extensions_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = bottomContentPadding + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(listings, key = SourceExtensionListing::id) { listing ->
            SourceExtensionRow(listing = listing)
        }
    }
}

@Composable
private fun SourceExtensionRow(listing: SourceExtensionListing) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Extension,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listing.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${listing.language} · ${listing.version}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = {}) {
            Text(stringResource(R.string.source_extensions_install))
        }
    }
}

@Composable
private fun SourceRepositoryList(
    onOpenRepository: () -> Unit,
    onCopyRepository: () -> Unit,
    bottomContentPadding: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = bottomContentPadding + 16.dp),
    ) {
        AppTonalSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                    Text(
                        text = SOURCE_REPOSITORY_DISPLAY_NAME,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onOpenRepository) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = stringResource(R.string.source_extensions_open_repository),
                        )
                    }
                    IconButton(onClick = onCopyRepository) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.source_extensions_copy_repository),
                        )
                    }
                }
            }
        }
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
                        Text(language, style = MaterialTheme.typography.titleMedium)
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

private data class SourceExtensionListing(
    val id: String,
    val name: String,
    val language: String,
    val version: String,
)

private val SOURCE_EXTENSION_LISTINGS = listOf(
    SourceExtensionListing("aniliberty", "AniLiberty", "RU", "1.4"),
    SourceExtensionListing("animego", "AnimeGo", "RU", "1.1"),
    SourceExtensionListing("animepahe", "AnimePahe", "RU", "1.1"),
    SourceExtensionListing("kickassanime", "KickAssAnime", "RU", "1.5"),
    SourceExtensionListing("yummyanime", "YummyAnime", "RU", "1.2"),
)

private const val SOURCE_REPOSITORY_DISPLAY_NAME = "akkirrai1337/hibiki-sources"
private const val SOURCE_REPOSITORY_INDEX_URL =
    "https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json"
private const val SOURCE_REPOSITORY_BROWSE_URL = "https://github.com/akkirrai1337/hibiki-sources"
