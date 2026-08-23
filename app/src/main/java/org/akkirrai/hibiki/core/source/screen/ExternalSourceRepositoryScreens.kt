package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.platform.AppSystemBackHandler
import org.akkirrai.hibiki.design.component.button.AppSplitActionButton
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

data class SourceRepositoriesActions(
    val onBack: () -> Unit,
    val onRepositoryClick: (String) -> Unit,
    val onRemoveRepository: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onCopyUrl: (String) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onAddRepository: () -> Unit,
)

@Composable
fun SourceRepositoriesScreen(
    state: ExternalSourceRepositoryUiState,
    actions: SourceRepositoriesActions,
    bottomContentPadding: Dp,
    customRepositoriesSupported: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SourceRepositoryTopBar(
            title = appText(AppTextKey.SourcesExternalRepositories),
            onBack = actions.onBack,
            actions = {
                IconButton(onClick = actions.onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = appText(AppTextKey.SettingsExternalRepositoryRefresh),
                    )
                }
                if (customRepositoriesSupported) {
                    IconButton(onClick = actions.onAddRepository) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = appText(AppTextKey.SourcesExternalRepositoryAddTitle),
                        )
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.repositoryContents.isEmpty()) {
                item {
                    SourceRepositoryEmptyState(
                        text = appText(AppTextKey.SourcesExternalRepositoriesEmpty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            } else {
                items(state.repositoryContents, key = { it.endpoint.url }) { repository ->
                    SourceRepositoryCard(
                        repository = repository,
                        onClick = { actions.onRepositoryClick(repository.endpoint.url) },
                        onOpenUrl = { actions.onOpenUrl(repositoryBrowseUrl(repository.endpoint.url)) },
                        onRemove = { actions.onRemoveRepository(repository.endpoint.url) },
                        onCopy = { actions.onCopyUrl(repository.endpoint.url) },
                        showRemove = customRepositoriesSupported,
                        clickable = customRepositoriesSupported,
                    )
                }
            }
        }
        if (customRepositoriesSupported) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomContentPadding + 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = actions.onAddRepository) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(appText(AppTextKey.SourcesExternalRepositoryAddTitle))
                }
            }
        }
    }
}

data class SourcesTabsActions(
    val onSelectedTabChange: (Int) -> Unit,
    val onRepositoryClick: (String) -> Unit,
    val onRemoveRepository: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onCopyUrl: (String) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onAddRepository: () -> Unit,
    val onInstall: (SourceId) -> Unit,
    val onSourceSelected: (String) -> Unit,
    val onManage: (SourceId) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourcesTabsScreen(
    selectedTab: Int,
    selectedSourceId: String?,
    state: ExternalSourceRepositoryUiState,
    actions: SourcesTabsActions,
    bottomContentPadding: Dp,
    customRepositoriesSupported: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val packages = state.packages
    val isBusy = state.isBusy
    var extensionsQuery by remember { mutableStateOf("") }
    var extensionsSearchOpen by remember { mutableStateOf(false) }
    var sourcesQuery by remember { mutableStateOf("") }
    var sourcesSearchOpen by remember { mutableStateOf(false) }
    var languageFilterOpen by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf(emptySet<String>()) }
    var displayedSelectedSourceId by remember(selectedSourceId) {
        mutableStateOf(selectedSourceId)
    }
    val languages = remember(packages) {
        packages.flatMap { it.availableManifest.sourceInfo?.languages.orEmpty() }
            .map { it.tag }
            .distinct()
            .sorted()
    }
    var searchFieldFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val tabScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, 1),
        pageCount = { 2 },
    )
    val extensionsTabSelected = pagerState.currentPage == 0
    val activeQuery = if (extensionsTabSelected) extensionsQuery else sourcesQuery
    val activeSearchOpen = if (extensionsTabSelected) extensionsSearchOpen else sourcesSearchOpen

    LaunchedEffect(selectedTab) {
        val targetPage = selectedTab.coerceIn(0, 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect(actions.onSelectedTabChange)
    }

    AppSystemBackHandler(
        enabled = activeSearchOpen,
        onBack = {
            focusManager.clearFocus()
            keyboardController?.hide()
            searchFieldFocused = false
            if (extensionsTabSelected) {
                extensionsQuery = ""
                extensionsSearchOpen = false
            } else {
                sourcesQuery = ""
                sourcesSearchOpen = false
            }
        },
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            AppMihonSourcesToolbar(
            title = appText(AppTextKey.Sources),
            searchOpen = activeSearchOpen,
            query = activeQuery,
            onQueryChange = { query ->
                if (extensionsTabSelected) extensionsQuery = query else sourcesQuery = query
            },
            onClearSearch = {
                if (extensionsTabSelected) extensionsQuery = "" else sourcesQuery = ""
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
            searchPlaceholder = appText(AppTextKey.SourcesExternalRepositorySearch),
            filterContentDescription = appText(AppTextKey.SourcesExternalRepositoryLanguages),
            showFilter = extensionsTabSelected,
            titleStyle = MaterialTheme.typography.titleLarge,
            onRefresh = actions.onRefresh,
            onAddClick = if (extensionsTabSelected || !customRepositoriesSupported) null else actions.onAddRepository,
            onSearchFocusChanged = { searchFieldFocused = it },
            tabContent = {
                AppSourcesTabs(
                    selectedTab = pagerState.currentPage,
                    sourcesLabel = appText(AppTextKey.Sources),
                    extensionsLabel = appText(AppTextKey.SourcesExtensions),
                    onSourcesSelected = { tabScope.launch { pagerState.animateScrollToPage(1) } },
                    onExtensionsSelected = { tabScope.launch { pagerState.animateScrollToPage(0) } },
                )
            },
            )
            state.error?.let { error ->
                Text(
                    text = error.message ?: appText(AppTextKey.SettingsExternalRepositoryOperationFailed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                if (page == 0) {
                val visiblePackages = packages.filter { packageStatus ->
                    val manifest = packageStatus.availableManifest
                    val name = manifest.sourceInfo?.displayName.orEmpty()
                    val matchesQuery = extensionsQuery.isBlank() ||
                        name.contains(extensionsQuery, ignoreCase = true) ||
                        manifest.sourceId.value.contains(extensionsQuery, ignoreCase = true)
                    val packageLanguages = manifest.sourceInfo?.languages.orEmpty().map { it.tag }.toSet()
                    matchesQuery && (selectedLanguages.isEmpty() || packageLanguages.any(selectedLanguages::contains))
                }.sortedForDisplay()
                if (visiblePackages.isEmpty()) {
                    SourceRepositoryEmptyState(
                        text = appText(AppTextKey.SourcesExternalRepositoryPackagesEmpty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 0.dp,
                            top = 8.dp,
                            end = 0.dp,
                            bottom = bottomContentPadding + 16.dp,
                        ),
                    ) {
                        items(visiblePackages, key = { it.sourceId.value }) { packageStatus ->
                            SourcePackageCard(
                                packageStatus = packageStatus,
                                busy = isBusy,
                                onInstall = { actions.onInstall(packageStatus.sourceId) },
                                onPackageClick = {
                                    if (packageStatus.activePackage != null) {
                                        displayedSelectedSourceId = packageStatus.sourceId.value
                                        actions.onSourceSelected(packageStatus.sourceId.value)
                                    }
                                },
                                selected = packageStatus.activePackage != null &&
                                    displayedSelectedSourceId == packageStatus.sourceId.value,
                                onManage = { actions.onManage(packageStatus.sourceId) },
                                onUpdate = { actions.onInstall(packageStatus.sourceId) },
                            )
                        }
                    }
                }
                } else {
                val visibleRepositories = state.repositoryContents.filter { repository ->
                    sourcesQuery.isBlank() || repositoryDisplayName(repository.endpoint.url)
                        .contains(sourcesQuery, ignoreCase = true) ||
                        repository.endpoint.url.contains(sourcesQuery, ignoreCase = true)
                }
                if (visibleRepositories.isEmpty()) {
                    SourceRepositoryEmptyState(
                        text = appText(AppTextKey.SourcesExternalRepositoriesEmpty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 0.dp,
                            top = 12.dp,
                            end = 0.dp,
                            bottom = bottomContentPadding + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(visibleRepositories, key = { it.endpoint.url }) { repository ->
                            SourceRepositoryCard(
                                repository = repository,
                                onClick = { actions.onRepositoryClick(repository.endpoint.url) },
                                onOpenUrl = { actions.onOpenUrl(repositoryBrowseUrl(repository.endpoint.url)) },
                                onRemove = { actions.onRemoveRepository(repository.endpoint.url) },
                                onCopy = { actions.onCopyUrl(repository.endpoint.url) },
                                showRemove = customRepositoriesSupported,
                                clickable = customRepositoriesSupported,
                            )
                        }
                    }
                }
                }
            }
        }
    }
    if (languageFilterOpen) {
        SourceRepositoryLanguageFilterDialog(
            languages = languages,
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
fun SourcePackageInfoScreen(
    packageStatus: ExternalSourcePackageStatus?,
    isBusy: Boolean,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SourceRepositoryTopBar(
            title = appText(AppTextKey.SourcesExternalPackageInfo),
            onBack = onBack,
        )
        if (packageStatus == null) {
            SourceRepositoryEmptyState(
                text = appText(AppTextKey.SourcesExternalRepositoryPackagesEmpty),
                modifier = Modifier.weight(1f),
            )
        } else {
            val manifest = packageStatus.availableManifest
            val title = manifest.sourceInfo?.displayName?.takeIf(String::isNotBlank)
                ?: manifest.sourceId.value
            val languages = manifest.sourceInfo?.languages.orEmpty().joinToString { it.tag.uppercase() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomContentPadding + 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                AppSourceIconImage(
                    url = manifest.sourceInfo?.iconUrl,
                    modifier = Modifier.size(112.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = manifest.sourceId.value,
                    style = MaterialTheme.typography.bodySmall,
                )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourcePackageInfoValue(
                        value = packageStatus.activePackage?.installed?.packageVersion
                            ?: manifest.packageVersion,
                        label = appText(AppTextKey.SourcesExternalPackageVersion),
                    )
                    VerticalDivider(modifier = Modifier.height(20.dp))
                    SourcePackageInfoValue(
                        value = languages.ifBlank { "—" },
                        label = appText(AppTextKey.SourcesExternalPackageLanguage),
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onUninstall,
                        enabled = !isBusy && packageStatus.activePackage != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(appText(AppTextKey.SettingsExternalPackageUninstall))
                    }
                    if (packageStatus.updateAvailable) {
                        Button(
                            onClick = onUpdate,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(appText(AppTextKey.SettingsExternalPackageUpdate))
                        }
                    }
                }
                if (onConfigure != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp),
                    ) {
                        Button(
                            onClick = onConfigure,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(appText(AppTextKey.SettingsExternalPackageConfigure))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePackageInfoValue(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
fun AppAddSourceRepositoryDialog(
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onPaste: () -> String?,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(appText(AppTextKey.SourcesExternalRepositoryAddTitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    enabled = !isBusy,
                    placeholder = { Text(appText(AppTextKey.SettingsExternalRepositoryUrlHint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onPaste()?.let { url = it } },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(appText(AppTextKey.SourcesExternalRepositoryPaste))
                }
                errorMessage?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(url) }, enabled = url.isNotBlank() && !isBusy) {
                Text(appText(AppTextKey.SettingsExternalRepositoryAdd))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, enabled = !isBusy) {
                Text(appText(AppTextKey.Cancel))
            }
        },
    )
}

@Composable
private fun SourceRepositoryLanguageFilterDialog(
    languages: List<String>,
    selectedLanguages: Set<String>,
    onLanguageToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appText(AppTextKey.SourcesExternalRepositoryLanguages)) },
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
                            Text(presentation.nativeName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                presentation.englishName,
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
                Text(appText(AppTextKey.Cancel))
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

@Composable
private fun SourceRepositoryTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = appText(AppTextKey.Back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun SourceRepositoryCard(
    repository: ExternalSourceRepositoryContent,
    onClick: () -> Unit,
    onOpenUrl: () -> Unit,
    onRemove: () -> Unit,
    onCopy: () -> Unit,
    showRemove: Boolean = true,
    clickable: Boolean = true,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .let { if (clickable) it.clickable(onClick = onClick) else it },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
            Text(
                text = repositoryDisplayName(repository.endpoint.url),
                modifier = Modifier.padding(start = 16.dp).weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        repository.error?.let { error ->
            Text(
                text = error.message ?: appText(AppTextKey.SettingsExternalRepositoryOperationFailed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onOpenUrl) {
                Icon(Icons.Outlined.Public, contentDescription = null)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = appText(AppTextKey.SourcesExternalRepositoryCopy))
            }
            if (showRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = appText(AppTextKey.SettingsExternalRepositoryRemove))
                }
            }
        }
    }
}

@Composable
private fun SourcePackageCard(
    packageStatus: ExternalSourcePackageStatus,
    busy: Boolean,
    onInstall: () -> Unit,
    onPackageClick: () -> Unit,
    selected: Boolean,
    onManage: () -> Unit,
    onUpdate: () -> Unit,
) {
    val manifest = packageStatus.availableManifest
    val title = manifest.sourceInfo?.displayName?.takeIf(String::isNotBlank) ?: manifest.sourceId.value
    val languages = manifest.sourceInfo?.languages.orEmpty().joinToString { it.tag.uppercase() }
    val installedVersion = packageStatus.activePackage?.installed?.packageVersion
    val versionLabel = if (installedVersion != null && packageStatus.updateAvailable) {
        "$installedVersion → ${manifest.packageVersion}"
    } else {
        installedVersion ?: manifest.packageVersion
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onPackageClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            AppSourceIconImage(
                url = manifest.sourceInfo?.iconUrl,
                modifier = Modifier.size(52.dp).clip(CircleShape),
                debugTag = "repository-card",
            )
        Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(languages.takeIf(String::isNotBlank), versionLabel).joinToString(" · "),
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
        if (packageStatus.activePackage == null) {
                Button(onClick = onInstall, enabled = !busy) {
                    Text(appText(AppTextKey.SettingsExternalPackageInstall))
                }
            } else if (packageStatus.updateAvailable) {
                AppSplitActionButton(
                    primaryLabel = appText(AppTextKey.SettingsExternalPackageManage),
                    secondaryLabel = appText(AppTextKey.SettingsExternalPackageUpdate),
                    onPrimaryClick = onManage,
                    onSecondaryClick = onUpdate,
                    enabled = !busy,
                )
            } else {
                    Button(onClick = onManage, enabled = !busy) {
                        Text(appText(AppTextKey.SettingsExternalPackageManage))
                    }
            }
    }
}

private fun List<ExternalSourcePackageStatus>.sortedForDisplay(): List<ExternalSourcePackageStatus> =
    sortedWith(
        compareBy(
            { it.availableManifest.sourceInfo?.displayName.orEmpty().lowercase() },
            { it.sourceId.value.lowercase() },
        ),
    )

@Composable
private fun SourceRepositoryEmptyState(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
