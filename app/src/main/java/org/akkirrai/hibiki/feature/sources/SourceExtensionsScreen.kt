package org.akkirrai.hibiki.feature.sources

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.extension.ExtensionMarketplaceClient
import org.akkirrai.hibiki.core.source.extension.ExtensionMarketplaceException
import org.akkirrai.hibiki.core.source.extension.MarketplaceExtension
import org.akkirrai.hibiki.core.source.extension.isExtensionVersionNewer

/**
 * Sources tab: the "Extensions" page browses `hibiki-sources`' marketplace index over the
 * network and installs extensions via [AnimeSourceRegistry.installScriptExtension] - no
 * APK/PackageManager step involved. The "Sources" page only presents the repository itself.
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
    var addRepositoryDialogOpen by remember { mutableStateOf(false) }
    var repositoryRefreshSignal by remember { mutableStateOf(0) }
    val marketplaceHttpClient = remember { AndroidHttpClientFactory.create() }
    DisposableEffect(marketplaceHttpClient) { onDispose { marketplaceHttpClient.close() } }
    val marketplaceClient = remember(marketplaceHttpClient) { ExtensionMarketplaceClient(marketplaceHttpClient) }
    var repoStates by remember { mutableStateOf<Map<String, RepoFetchResult>>(emptyMap()) }
    var installingExtensionIds by remember { mutableStateOf(emptySet<String>()) }
    var extensionInstallErrors by remember { mutableStateOf(emptyMap<String, String>()) }

    val preferences = LocalAppPreferences.current
    val sourceRepositoryUrls = LocalAppPreferencesState.current.sourceRepositoryUrls
    val selectedSource = LocalAppPreferencesState.current.animeSource
    val haptic = LocalHapticFeedback.current

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val tabScope = rememberCoroutineScope()

    val mergedExtensions = remember(sourceRepositoryUrls, repoStates) {
        sourceRepositoryUrls
            .mapNotNull { url -> (repoStates[url] as? RepoFetchResult.Loaded)?.extensions }
            .flatten()
            .distinctBy(MarketplaceExtension::id)
    }
    val sourceExtensions = mergedExtensions.filter { it.type == "source" }
    // Player-resolver extensions have no lang field at all (index.json defaults it to "" - see
    // build_index.py) and language filtering only makes sense for sources anyway, so this must
    // come from sourceExtensions, not every mergedExtensions entry - otherwise that blank default
    // shows up as an empty, unlabeled toggle in the language filter dialog.
    val extensionLanguages = sourceExtensions.map(MarketplaceExtension::lang).distinct().sorted()

    // Extensions tab still consumes the flat Loading/Error/Loaded shape it always has - derived
    // here from the per-repository results so SourceRepositoryList/MarketplaceExtensionRow don't
    // need to know repositories are plural.
    val repositoryLoadState: RepositoryLoadState = when {
        sourceRepositoryUrls.any { repoStates[it] is RepoFetchResult.Loaded } ->
            RepositoryLoadState.Loaded(sourceExtensions)
        sourceRepositoryUrls.isNotEmpty() && sourceRepositoryUrls.all { repoStates[it] is RepoFetchResult.Error } ->
            RepositoryLoadState.Error(
                sourceRepositoryUrls.mapNotNull { (repoStates[it] as? RepoFetchResult.Error)?.message }
                    .distinct()
                    .joinToString("; "),
            )
        else -> RepositoryLoadState.Loading
    }

    suspend fun loadRepositories(urls: List<String>) {
        if (urls.isEmpty()) {
            repoStates = emptyMap()
            return
        }
        repoStates = urls.associateWith { RepoFetchResult.Loading }
        val results = coroutineScope {
            urls.map { url ->
                async {
                    url to try {
                        RepoFetchResult.Loaded(ExtensionMarketplaceClient(marketplaceHttpClient, url).fetchIndex().extensions)
                    } catch (error: ExtensionMarketplaceException) {
                        RepoFetchResult.Error(error.message ?: error.toString())
                    }
                }
            }.awaitAll()
        }
        repoStates = results.toMap()
    }

    LaunchedEffect(repositoryRefreshSignal, sourceRepositoryUrls) { loadRepositories(sourceRepositoryUrls) }

    // This screen's composable stays alive across bottom-nav tab switches (the NavHost restores
    // rather than recreates it, so its `remember`ed state survives) - without this, the
    // marketplace index fetched once on first visit would keep showing whatever versions were
    // current back then for the rest of the process' life, no matter how many times the user
    // navigates away and back into Sources looking for an update.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) repositoryRefreshSignal++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    val thirdPartyRepositoriesDisabledMessage = stringResource(R.string.source_extensions_repositories_add_disabled)

    Column(modifier = modifier.fillMaxSize()) {
        SourceExtensionsToolbar(
            searchOpen = searchOpen,
            showFilter = pagerState.currentPage == 0,
            filterCount = selectedLanguages.size,
            query = query,
            onQueryChange = { query = it },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                query = ""
                searchOpen = false
            },
            onFilterClick = { languageFilterOpen = true },
            showAddRepository = pagerState.currentPage == 1,
            onAddRepository = { addRepositoryDialogOpen = true },
            onRefresh = {
                repositoryRefreshSignal++
            },
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
                SourceRepositoryList(
                    bottomContentPadding = bottomContentPadding,
                    query = query,
                    state = repositoryLoadState,
                    selectedLanguages = selectedLanguages,
                    selectedSource = selectedSource,
                    installingIds = installingExtensionIds,
                    installErrors = extensionInstallErrors,
                    onRetry = { tabScope.launch { loadRepositories(sourceRepositoryUrls) } },
                    onInstall = { extension ->
                        installingExtensionIds = installingExtensionIds + extension.id
                        extensionInstallErrors = extensionInstallErrors - extension.id
                        // Installing doesn't select a source on its own -- if this is the first
                        // source the user has ever installed, select it automatically so Home and
                        // Catalog (both already listening for AppPreferences.animeSourceChanges)
                        // load right away instead of sitting on their earlier no-source error.
                        val hadNoSources = AnimeSourceRegistry.sources.isEmpty()
                        tabScope.launch {
                            try {
                                extension.resolverDependencies.forEach { resolverId ->
                                    val resolver = mergedExtensions.firstOrNull {
                                        it.id == resolverId && it.type == "player-resolver"
                                    } ?: error("Required resolver '$resolverId' is not present in this repository")
                                    AnimeSourceRegistry.installPlayerResolverExtension(
                                        marketplaceClient.fetchPlayerResolverManifest(resolver),
                                    )
                                }
                                AnimeSourceRegistry.installScriptExtension(marketplaceClient.fetchManifest(extension))
                                if (hadNoSources) {
                                    preferences.setAnimeSource(SourceId(extension.id))
                                }
                            } catch (error: Exception) {
                                extensionInstallErrors = extensionInstallErrors +
                                    (extension.id to (error.message ?: error.toString()))
                            } finally {
                                installingExtensionIds = installingExtensionIds - extension.id
                            }
                        }
                    },
                    onSelect = { sourceId ->
                        preferences.setAnimeSource(SourceId(sourceId))
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                    onUninstall = { sourceId -> AnimeSourceRegistry.uninstallScriptExtension(SourceId(sourceId)) },
                )
            } else {
                RepositoriesList(
                    urls = sourceRepositoryUrls,
                    repoStates = repoStates,
                    bottomContentPadding = bottomContentPadding,
                    onRemove = { url -> preferences.removeSourceRepository(url) },
                )
            }
        }
    }

    if (addRepositoryDialogOpen) {
        AddRepositoryDialog(
            onAdd = { url ->
                preferences.addSourceRepository(url)
                addRepositoryDialogOpen = false
            },
            onDismiss = { addRepositoryDialogOpen = false },
            // Third-party repositories aren't supported yet (no sandboxing/trust story for
            // arbitrary scripted-extension payloads from an untrusted feed) - the dialog stays
            // reachable so the "+" button isn't dead, but every attempt is rejected up front
            // instead of actually validating/adding anything.
            validate = { thirdPartyRepositoriesDisabledMessage },
        )
    }

    if (languageFilterOpen) {
        SourceLanguageFilterDialog(
            languages = extensionLanguages,
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
    filterCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onFilterClick: () -> Unit,
    showAddRepository: Boolean,
    onAddRepository: () -> Unit,
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
            AppSearchTopBar(
                query = query,
                onQueryChange = onQueryChange,
                onClear = { onQueryChange("") },
                showFilter = false,
                placeholderResId = R.string.onboarding_source_search,
                modifier = Modifier.weight(1f),
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
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.source_extensions_filter_languages),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                    if (filterCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .height(17.dp)
                                .widthIn(min = 17.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (filterCount > 9) "9+" else filterCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
        if (showAddRepository) {
            IconButton(onClick = onAddRepository) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.source_extensions_repositories_add),
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
                    val presentation = sourceLanguagePresentation(language)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageToggle(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = presentation.nativeName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = presentation.englishName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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

private data class SourceLanguagePresentation(
    val nativeName: String,
    val englishName: String,
)

private fun sourceLanguagePresentation(language: String): SourceLanguagePresentation = when (language.lowercase()) {
    "ru", "russian" -> SourceLanguagePresentation("русский", "Russian")
    "uk", "ukrainian" -> SourceLanguagePresentation("Українська", "Ukrainian")
    "en", "english" -> SourceLanguagePresentation("English", "English")
    "pt", "portuguese" -> SourceLanguagePresentation("Português", "Portuguese")
    "tr", "turkish" -> SourceLanguagePresentation("Türkçe", "Turkish")
    "th", "thai" -> SourceLanguagePresentation("ไทย", "Thai")
    else -> SourceLanguagePresentation(language.uppercase(), language.uppercase())
}

private sealed interface RepositoryLoadState {
    data object Loading : RepositoryLoadState
    data class Error(val message: String) : RepositoryLoadState
    data class Loaded(val extensions: List<MarketplaceExtension>) : RepositoryLoadState
}

/** One connected repository's own fetch outcome - kept separate per URL so one broken repository
 * doesn't blank out extensions from the others in the merged [RepositoryLoadState] Extensions
 * tab consumes. */
private sealed interface RepoFetchResult {
    data object Loading : RepoFetchResult
    data class Error(val message: String) : RepoFetchResult
    data class Loaded(val extensions: List<MarketplaceExtension>) : RepoFetchResult
}

@Composable
private fun SourceRepositoryList(
    bottomContentPadding: Dp,
    query: String,
    state: RepositoryLoadState,
    selectedLanguages: Set<String>,
    selectedSource: SourceId,
    installingIds: Set<String>,
    installErrors: Map<String, String>,
    onRetry: () -> Unit,
    onInstall: (MarketplaceExtension) -> Unit,
    onSelect: (String) -> Unit,
    onUninstall: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = bottomContentPadding),
    ) {
        when (state) {
            is RepositoryLoadState.Loading -> SourceRepositoryMessage(stringResource(R.string.source_extensions_repository_loading))
            is RepositoryLoadState.Error -> SourceRepositoryMessage(
                message = stringResource(R.string.source_extensions_repository_error),
                detail = state.message,
                onRetry = onRetry,
            )
            is RepositoryLoadState.Loaded -> {
                val installedVersions = AnimeSourceRegistry.installedScriptExtensionVersions()
                val installedResolverVersions = AnimeSourceRegistry.installedPlayerResolverVersions()
                val visibleExtensions = state.extensions.filter { extension ->
                    val matchesQuery = query.isBlank() ||
                        extension.name.contains(query, ignoreCase = true) ||
                        extension.id.contains(query, ignoreCase = true)
                    val matchesLanguage = selectedLanguages.isEmpty() || extension.lang in selectedLanguages
                    extension.type == "source" && matchesQuery && matchesLanguage
                }
                if (visibleExtensions.isEmpty()) {
                    SourceRepositoryMessage(stringResource(R.string.source_extensions_repository_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    ) {
                        items(visibleExtensions, key = MarketplaceExtension::id) { extension ->
                            // A resolver dependency can be fixed and re-published without its
                            // owning source's own version changing at all - without this, that fix
                            // would never reach anyone already past the initial install (see
                            // MarketplaceExtensionRow's onInstall, which is what actually refetches
                            // resolverDependencies, but only runs when the user taps an update).
                            val resolverUpdateAvailable = extension.resolverDependencies.any { resolverId ->
                                val installed = installedResolverVersions[resolverId] ?: return@any false
                                val available = state.extensions.firstOrNull { it.id == resolverId && it.type == "player-resolver" }
                                available != null && isExtensionVersionNewer(available.version, installed)
                            }
                            MarketplaceExtensionRow(
                                extension = extension,
                                installedVersion = installedVersions[extension.id],
                                resolverUpdateAvailable = resolverUpdateAvailable,
                                installing = extension.id in installingIds,
                                errorMessage = installErrors[extension.id],
                                selected = installedVersions.containsKey(extension.id) &&
                                    extension.id == selectedSource.value,
                                onInstall = { onInstall(extension) },
                                onSelect = { onSelect(extension.id) },
                                onUninstall = { onUninstall(extension.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoriesList(
    urls: List<String>,
    repoStates: Map<String, RepoFetchResult>,
    bottomContentPadding: Dp,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = bottomContentPadding),
    ) {
        if (urls.isEmpty()) {
            SourceRepositoryMessage(stringResource(R.string.source_extensions_repositories_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(urls, key = { it }) { url ->
                    RepositoryCard(
                        url = url,
                        state = repoStates[url],
                        removable = url != ExtensionMarketplaceClient.DEFAULT_INDEX_URL,
                        onRemove = { onRemove(url) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryCard(
    url: String,
    state: RepoFetchResult?,
    removable: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = repositoryDisplayName(url),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (state) {
                        is RepoFetchResult.Loaded ->
                            stringResource(
                                R.string.source_extensions_repositories_extension_count,
                                state.extensions.count { it.type == "source" },
                            )
                        is RepoFetchResult.Error -> state.message
                        RepoFetchResult.Loading, null -> stringResource(R.string.source_extensions_repository_loading)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state is RepoFetchResult.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                Icon(imageVector = Icons.Outlined.Public, contentDescription = null)
            }
            IconButton(onClick = { clipboardManager.setText(AnnotatedString(url)) }) {
                Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
            }
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.source_extensions_repositories_remove),
                    )
                }
            }
        }
    }
}

/** Best-effort "owner/repo" label from a repository index URL - e.g. the built-in
 * raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json reads as
 * "akkirrai1337/hibiki-sources". Falls back to the raw URL for anything hosted elsewhere. */
private fun repositoryDisplayName(url: String): String {
    val match = Regex("""^https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/""").find(url)
    return match?.let { "${it.groupValues[1]}/${it.groupValues[2]}" } ?: url
}

@Composable
private fun AddRepositoryDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
    validate: suspend (String) -> String?,
) {
    var url by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_extensions_repositories_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; errorMessage = null },
                    placeholder = { Text(stringResource(R.string.source_extensions_repositories_add_hint)) },
                    singleLine = true,
                    enabled = !validating,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !validating && url.isNotBlank(),
                onClick = {
                    val trimmed = url.trim()
                    validating = true
                    scope.launch {
                        val error = validate(trimmed)
                        validating = false
                        if (error == null) onAdd(trimmed) else errorMessage = error
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !validating) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SourceRepositoryMessage(
    message: String,
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail != null) {
            Text(
                text = detail,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.source_extensions_repository_retry))
            }
        }
    }
}

@Composable
private fun MarketplaceExtensionRow(
    extension: MarketplaceExtension,
    installedVersion: String?,
    resolverUpdateAvailable: Boolean,
    installing: Boolean,
    errorMessage: String?,
    selected: Boolean,
    onInstall: () -> Unit,
    onSelect: () -> Unit,
    onUninstall: () -> Unit,
) {
    // A resolver fix can ship without the source's own manifest version changing at all, so the
    // source-version comparison alone would never surface it - reinstalling the source is also what
    // refetches its resolverDependencies (see onInstall below), so an available resolver update is
    // just as much a reason to show "update" here as the source's own version being behind.
    val upToDate = installedVersion != null &&
        !isExtensionVersionNewer(extension.version, installedVersion) &&
        !resolverUpdateAvailable
    val versionLabel = when {
        // The source's own version can be identical on both sides when only its resolver moved -
        // "1.0.9 → 1.0.9" would just confuse the user, so name what's actually changing instead.
        installedVersion != null && !upToDate && extension.version == installedVersion ->
            "$installedVersion (${stringResource(R.string.source_extensions_resolver_update)})"
        installedVersion != null && !upToDate -> "$installedVersion → ${extension.version}"
        installedVersion != null -> installedVersion
        else -> extension.version
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = installedVersion != null && !installing,
                    onClick = onSelect,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = extension.iconUrl,
                placeholder = painterResource(R.drawable.animite_media_type_anime),
                error = painterResource(R.drawable.animite_media_type_anime),
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (extension.isNsfw) {
                        NsfwBadge()
                    }
                }
                Text(
                    text = "${extension.lang.uppercase()} · $versionLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (installedVersion != null && !upToDate) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
            if (installedVersion == null) {
                Button(onClick = onInstall, enabled = !installing) {
                    Text(stringResource(R.string.source_extensions_install))
                }
            } else {
                ExtensionManageButton(
                    enabled = !installing,
                    updateAvailable = !upToDate,
                    onUpdate = onInstall,
                    onUninstall = onUninstall,
                )
            }
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 64.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun NsfwBadge() {
    Text(
        text = stringResource(R.string.source_extensions_nsfw_badge),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .background(color = Color(0xFFD32F2F), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun ExtensionManageButton(
    enabled: Boolean,
    updateAvailable: Boolean,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { menuExpanded = true }, enabled = enabled) {
            Text(stringResource(R.string.source_extensions_manage))
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (updateAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.source_extensions_update)) },
                    onClick = {
                        menuExpanded = false
                        onUpdate()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_extensions_uninstall)) },
                onClick = {
                    menuExpanded = false
                    onUninstall()
                },
            )
        }
    }
}
