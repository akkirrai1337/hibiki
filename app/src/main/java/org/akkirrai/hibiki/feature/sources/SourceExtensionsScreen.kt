package org.akkirrai.hibiki.feature.sources

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppSourceIconImage
import org.akkirrai.hibiki.core.design.component.AppSplitActionButton
import org.akkirrai.hibiki.core.design.component.AppTonalSurface

/**
 * Visual-only port of the Mihon-style extension marketplace screen: browsing/installing is not
 * wired to a real repository yet (see the model constants at the bottom), this reproduces the
 * full screen -- tabs, search, language filter, package info drill-in, split update button, and
 * the (currently unreachable, since [CUSTOM_REPOSITORIES_SUPPORTED] is false) add/remove
 * repository flow -- rather than a stripped-down shell.
 */
private const val CUSTOM_REPOSITORIES_SUPPORTED = false

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceExtensionsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var extensionsQuery by rememberSaveable { mutableStateOf("") }
    var extensionsSearchOpen by rememberSaveable { mutableStateOf(false) }
    var sourcesQuery by rememberSaveable { mutableStateOf("") }
    var sourcesSearchOpen by rememberSaveable { mutableStateOf(false) }
    var languageFilterOpen by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf(emptySet<String>()) }
    var addRepositoryDialogOpen by remember { mutableStateOf(false) }
    var selectedExtensionId by rememberSaveable { mutableStateOf<String?>(null) }
    var infoScreenPackageId by rememberSaveable { mutableStateOf<String?>(null) }
    // Not persisted -- there's no real package manager backing this yet, so a process death would
    // have nothing to restore install state from anyway.
    val installedVersions = remember { mutableStateMapOf<String, String>() }

    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.source_extensions_repository_copied)

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val tabScope = rememberCoroutineScope()
    val extensionsTabSelected = pagerState.currentPage == 0
    val activeQuery = if (extensionsTabSelected) extensionsQuery else sourcesQuery
    val activeSearchOpen = if (extensionsTabSelected) extensionsSearchOpen else sourcesSearchOpen

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
    BackHandler(enabled = activeSearchOpen) {
        if (extensionsTabSelected) {
            extensionsQuery = ""
            extensionsSearchOpen = false
        } else {
            sourcesQuery = ""
            sourcesSearchOpen = false
        }
    }

    val infoPackageId = infoScreenPackageId
    if (infoPackageId != null) {
        val listing = SOURCE_EXTENSION_LISTINGS.firstOrNull { it.id == infoPackageId }
        val installedVersion = installedVersions[infoPackageId]
        if (listing != null) {
            SourcePackageInfoScreen(
                listing = listing,
                installedVersion = installedVersion,
                onBack = { infoScreenPackageId = null },
                onUninstall = {
                    installedVersions.remove(infoPackageId)
                    if (selectedExtensionId == infoPackageId) selectedExtensionId = null
                    infoScreenPackageId = null
                },
                onUpdate = {
                    listing.updateVersion?.let { installedVersions[infoPackageId] = it }
                },
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        SourceExtensionsToolbar(
            searchOpen = activeSearchOpen,
            showFilter = extensionsTabSelected,
            query = activeQuery,
            onQueryChange = { query ->
                if (extensionsTabSelected) extensionsQuery = query else sourcesQuery = query
            },
            onOpenSearch = {
                if (extensionsTabSelected) extensionsSearchOpen = true else sourcesSearchOpen = true
            },
            onCloseSearch = {
                if (extensionsTabSelected) {
                    extensionsQuery = ""
                    extensionsSearchOpen = false
                } else {
                    sourcesQuery = ""
                    sourcesSearchOpen = false
                }
            },
            onFilterClick = { languageFilterOpen = true },
            onRefresh = {},
            onAddClick = if (!extensionsTabSelected && CUSTOM_REPOSITORIES_SUPPORTED) {
                { addRepositoryDialogOpen = true }
            } else {
                null
            },
        )
        SourceExtensionsTabRow(
            selectedTab = pagerState.currentPage,
            onExtensionsSelected = { tabScope.launch { pagerState.animateScrollToPage(0) } },
            onSourcesSelected = { tabScope.launch { pagerState.animateScrollToPage(1) } },
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            if (page == 0) {
                val visibleListings = SOURCE_EXTENSION_LISTINGS.filter { listing ->
                    val matchesQuery = extensionsQuery.isBlank() ||
                        listing.name.contains(extensionsQuery, ignoreCase = true)
                    val matchesLanguage = selectedLanguages.isEmpty() || listing.language in selectedLanguages
                    matchesQuery && matchesLanguage
                }
                SourceExtensionsList(
                    listings = visibleListings,
                    installedVersions = installedVersions,
                    selectedExtensionId = selectedExtensionId,
                    bottomContentPadding = bottomContentPadding,
                    onInstall = { listing -> installedVersions[listing.id] = listing.version },
                    onPackageClick = { listing ->
                        if (installedVersions.containsKey(listing.id)) selectedExtensionId = listing.id
                    },
                    onManageClick = { listing -> infoScreenPackageId = listing.id },
                    onUpdateClick = { listing -> listing.updateVersion?.let { installedVersions[listing.id] = it } },
                )
            } else {
                val visibleRepositories = SOURCE_REPOSITORIES.filter { repository ->
                    sourcesQuery.isBlank() || repository.displayName.contains(sourcesQuery, ignoreCase = true)
                }
                SourceRepositoriesList(
                    repositories = visibleRepositories,
                    customRepositoriesSupported = CUSTOM_REPOSITORIES_SUPPORTED,
                    bottomContentPadding = bottomContentPadding,
                    onOpenRepository = { repository -> uriHandler.openUri(repository.browseUrl) },
                    onCopyRepository = { repository ->
                        clipboardManager.setText(AnnotatedString(repository.indexUrl))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    },
                    onAddRepository = { addRepositoryDialogOpen = true },
                )
            }
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

    if (addRepositoryDialogOpen) {
        AppAddSourceRepositoryDialog(
            onDismiss = { addRepositoryDialogOpen = false },
            onAdd = { addRepositoryDialogOpen = false },
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
    onAddClick: (() -> Unit)?,
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
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
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
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.source_extensions_search))
            }
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
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.source_extensions_refresh))
        }
        onAddClick?.let { addClick ->
            IconButton(onClick = addClick) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.source_extensions_add_repository))
            }
        }
    }
}

@Composable
private fun SourceExtensionsTabRow(
    selectedTab: Int,
    onExtensionsSelected: () -> Unit,
    onSourcesSelected: () -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = onExtensionsSelected,
            text = {
                Text(
                    text = stringResource(R.string.source_extensions_tab_extensions),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        Tab(
            selected = selectedTab == 1,
            onClick = onSourcesSelected,
            text = {
                Text(
                    text = stringResource(R.string.source_extensions_tab_sources),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

@Composable
private fun SourceExtensionsList(
    listings: List<SourceExtensionListing>,
    installedVersions: Map<String, String>,
    selectedExtensionId: String?,
    bottomContentPadding: Dp,
    onInstall: (SourceExtensionListing) -> Unit,
    onPackageClick: (SourceExtensionListing) -> Unit,
    onManageClick: (SourceExtensionListing) -> Unit,
    onUpdateClick: (SourceExtensionListing) -> Unit,
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
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = bottomContentPadding + 16.dp,
        ),
    ) {
        items(listings, key = SourceExtensionListing::id) { listing ->
            SourceExtensionRow(
                listing = listing,
                installedVersion = installedVersions[listing.id],
                selected = selectedExtensionId == listing.id,
                onInstall = { onInstall(listing) },
                onPackageClick = { onPackageClick(listing) },
                onManageClick = { onManageClick(listing) },
                onUpdateClick = { onUpdateClick(listing) },
            )
        }
    }
}

@Composable
private fun SourceExtensionRow(
    listing: SourceExtensionListing,
    installedVersion: String?,
    selected: Boolean,
    onInstall: () -> Unit,
    onPackageClick: () -> Unit,
    onManageClick: () -> Unit,
    onUpdateClick: () -> Unit,
) {
    val updateAvailable = installedVersion != null &&
        listing.updateVersion != null &&
        installedVersion != listing.updateVersion
    val versionLabel = if (updateAvailable) {
        "$installedVersion → ${listing.updateVersion}"
    } else {
        installedVersion ?: listing.version
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = installedVersion != null, onClick = onPackageClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppSourceIconImage(
            url = null,
            modifier = Modifier.size(48.dp),
            debugTag = listing.id,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = listing.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${listing.language} · $versionLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        when {
            installedVersion == null -> FilledTonalButton(onClick = onInstall) {
                Text(stringResource(R.string.source_extensions_install))
            }
            updateAvailable -> AppSplitActionButton(
                primaryLabel = stringResource(R.string.source_extensions_manage),
                secondaryLabel = stringResource(R.string.source_extensions_update),
                onPrimaryClick = onManageClick,
                onSecondaryClick = onUpdateClick,
            )
            else -> FilledTonalButton(onClick = onManageClick) {
                Text(stringResource(R.string.source_extensions_manage))
            }
        }
    }
}

@Composable
private fun SourceRepositoriesList(
    repositories: List<SourceRepositoryListing>,
    customRepositoriesSupported: Boolean,
    bottomContentPadding: Dp,
    onOpenRepository: (SourceRepositoryListing) -> Unit,
    onCopyRepository: (SourceRepositoryListing) -> Unit,
    onAddRepository: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (repositories.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.source_extensions_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = bottomContentPadding + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(repositories, key = SourceRepositoryListing::indexUrl) { repository ->
                    SourceRepositoryCard(
                        repository = repository,
                        showRemove = customRepositoriesSupported,
                        onOpenRepository = { onOpenRepository(repository) },
                        onCopyRepository = { onCopyRepository(repository) },
                        onRemove = {},
                    )
                }
            }
        }
        if (customRepositoriesSupported) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomContentPadding + 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onAddRepository) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.source_extensions_add_repository))
                }
            }
        }
    }
}

@Composable
private fun SourceRepositoryCard(
    repository: SourceRepositoryListing,
    showRemove: Boolean,
    onOpenRepository: () -> Unit,
    onCopyRepository: () -> Unit,
    onRemove: () -> Unit,
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
                    text = repository.displayName,
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
                if (showRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.source_extensions_remove_repository),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePackageInfoScreen(
    listing: SourceExtensionListing,
    installedVersion: String?,
    onBack: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
) {
    val updateAvailable = installedVersion != null &&
        listing.updateVersion != null &&
        installedVersion != listing.updateVersion
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                text = stringResource(R.string.source_extensions_package_info),
                style = MaterialTheme.typography.titleLarge,
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
                AppSourceIconImage(url = null, modifier = Modifier.size(112.dp))
                Text(
                    text = listing.name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(text = listing.id, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourcePackageInfoValue(
                    value = installedVersion ?: listing.version,
                    label = stringResource(R.string.source_extensions_version),
                )
                VerticalDivider(modifier = Modifier.height(20.dp))
                SourcePackageInfoValue(
                    value = listing.language,
                    label = stringResource(R.string.source_extensions_language),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = onUninstall,
                    enabled = installedVersion != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.source_extensions_uninstall))
                }
                if (updateAvailable) {
                    Button(onClick = onUpdate, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.source_extensions_update))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePackageInfoValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Text(
            label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun AppAddSourceRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_extensions_add_repository)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(stringResource(R.string.source_extensions_repository_url_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.source_extensions_add_repository))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
    val updateVersion: String? = null,
)

private data class SourceRepositoryListing(
    val displayName: String,
    val browseUrl: String,
    val indexUrl: String,
)

private val SOURCE_EXTENSION_LISTINGS = listOf(
    SourceExtensionListing("aniliberty", "AniLiberty", "RU", "1.4"),
    SourceExtensionListing("animego", "AnimeGo", "RU", "1.1"),
    SourceExtensionListing("animepahe", "AnimePahe", "RU", "1.1"),
    SourceExtensionListing("kickassanime", "KickAssAnime", "RU", "1.5"),
    SourceExtensionListing("yummyanime", "YummyAnime", "RU", "1.2", updateVersion = "1.3"),
)

private val SOURCE_REPOSITORIES = listOf(
    SourceRepositoryListing(
        displayName = "akkirrai1337/hibiki-sources",
        browseUrl = "https://github.com/akkirrai1337/hibiki-sources",
        indexUrl = "https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json",
    ),
)
