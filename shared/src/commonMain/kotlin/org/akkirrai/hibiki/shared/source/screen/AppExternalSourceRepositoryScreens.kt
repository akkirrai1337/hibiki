package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
fun AppSourceRepositoriesScreen(
    state: ExternalSourceRepositoryUiState,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onRepositoryClick: (String) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onRefresh: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddRepository: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SourceRepositoryTopBar(
            title = appText(AppTextKey.SourcesExternalRepositories),
            onBack = onBack,
            actions = {
                IconButton(onClick = onAddRepository) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = appText(AppTextKey.SourcesExternalRepositoryAddTitle),
                    )
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
                        onClick = { onRepositoryClick(repository.endpoint.url) },
                        onOpenUrl = { onOpenUrl(repository.endpoint.url) },
                        onRemove = { onRemoveRepository(repository.endpoint.url) },
                        onCopy = { onCopyUrl(repository.endpoint.url) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomContentPadding + 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onAddRepository) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(appText(AppTextKey.SourcesExternalRepositoryAddTitle))
            }
        }
    }
}

@Composable
fun AppExternalSourcesTabScreen(
    state: ExternalSourceRepositoryUiState,
    bottomContentPadding: Dp,
    onExtensionsSelected: () -> Unit,
    onRepositoryClick: (String) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onRefresh: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddRepository: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val visibleRepositories = remember(state.repositoryContents, query) {
        state.repositoryContents.filter { repository ->
            query.isBlank() || repositoryDisplayName(repository.endpoint.url)
                .contains(query, ignoreCase = true) ||
                repository.endpoint.url.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppMihonSourcesToolbar(
            title = appText(AppTextKey.Sources),
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onClearSearch = { query = "" },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                query = ""
                searchOpen = false
            },
            onFilterClick = {},
            searchPlaceholder = appText(AppTextKey.SourcesExternalRepositorySearch),
            filterContentDescription = appText(AppTextKey.SourcesExternalRepositoryLanguages),
            showFilter = false,
            onRefresh = onRefresh,
            onAddClick = onAddRepository,
            tabContent = {
                AppSourcesTabs(
                    selectedTab = 1,
                    sourcesLabel = appText(AppTextKey.Sources),
                    extensionsLabel = appText(AppTextKey.SourcesExtensions),
                    onSourcesSelected = {},
                    onExtensionsSelected = onExtensionsSelected,
                )
            },
        )
        if (visibleRepositories.isEmpty()) {
            SourceRepositoryEmptyState(
                text = appText(AppTextKey.SourcesExternalRepositoriesEmpty),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
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
                        onClick = { onRepositoryClick(repository.endpoint.url) },
                        onOpenUrl = { onOpenUrl(repository.endpoint.url) },
                        onRemove = { onRemoveRepository(repository.endpoint.url) },
                        onCopy = { onCopyUrl(repository.endpoint.url) },
                    )
                }
            }
        }
    }
}

@Composable
fun AppSourceExtensionsTabScreen(
    packages: List<ExternalSourcePackageStatus>,
    isBusy: Boolean,
    bottomContentPadding: Dp,
    onSourcesSelected: () -> Unit,
    onInstall: (SourceId) -> Unit,
    onRollback: (SourceId) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var languageFilterOpen by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf(emptySet<String>()) }
    val languages = remember(packages) {
        packages.flatMap { it.availableManifest.sourceInfo?.languages.orEmpty() }
            .map { it.tag }
            .distinct()
            .sorted()
    }
    val visiblePackages = remember(packages, query, selectedLanguages) {
        packages.filter { packageStatus ->
            val manifest = packageStatus.availableManifest
            val name = manifest.sourceInfo?.displayName.orEmpty()
            val matchesQuery = query.isBlank() || name.contains(query, ignoreCase = true) ||
                manifest.sourceId.value.contains(query, ignoreCase = true)
            val packageLanguages = manifest.sourceInfo?.languages.orEmpty().map { it.tag }.toSet()
            matchesQuery && (selectedLanguages.isEmpty() || packageLanguages.any(selectedLanguages::contains))
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        AppMihonSourcesToolbar(
            title = appText(AppTextKey.Sources),
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onClearSearch = { query = "" },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                query = ""
                searchOpen = false
            },
            onFilterClick = { languageFilterOpen = true },
            searchPlaceholder = appText(AppTextKey.SourcesExternalRepositorySearch),
            filterContentDescription = appText(AppTextKey.SourcesExternalRepositoryLanguages),
            onRefresh = onRefresh,
            tabContent = {
                AppSourcesTabs(
                    selectedTab = 0,
                    sourcesLabel = appText(AppTextKey.Sources),
                    extensionsLabel = appText(AppTextKey.SourcesExtensions),
                    onSourcesSelected = onSourcesSelected,
                    onExtensionsSelected = {},
                )
            },
        )
        if (visiblePackages.isEmpty()) {
            SourceRepositoryEmptyState(
                text = appText(AppTextKey.SourcesExternalRepositoryPackagesEmpty),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
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
                        onInstall = { onInstall(packageStatus.sourceId) },
                        onRollback = { onRollback(packageStatus.sourceId) },
                    )
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
fun AppSourceRepositoryPackagesScreen(
    repository: ExternalSourceRepositoryContent?,
    isBusy: Boolean,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (SourceId) -> Unit,
    onRollback: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember(repository?.endpoint?.url) { mutableStateOf("") }
    var selectedLanguages by remember(repository?.endpoint?.url) { mutableStateOf(emptySet<String>()) }
    var searchDialogOpen by remember(repository?.endpoint?.url) { mutableStateOf(false) }
    var languageFilterOpen by remember(repository?.endpoint?.url) { mutableStateOf(false) }
    val packages = repository?.packages.orEmpty()
    val languages = remember(packages) {
        packages.flatMap { it.availableManifest.sourceInfo?.languages.orEmpty() }
            .map { it.tag }
            .distinct()
            .sorted()
    }
    val visiblePackages = remember(packages, query, selectedLanguages) {
        packages.filter { packageStatus ->
            val manifest = packageStatus.availableManifest
            val name = manifest.sourceInfo?.displayName.orEmpty()
            val matchesQuery = query.isBlank() || name.contains(query, ignoreCase = true) ||
                manifest.sourceId.value.contains(query, ignoreCase = true)
            val packageLanguages = manifest.sourceInfo?.languages.orEmpty().map { it.tag }.toSet()
            matchesQuery && (selectedLanguages.isEmpty() || packageLanguages.any(selectedLanguages::contains))
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        SourceRepositoryTopBar(
            title = repository?.endpoint?.url?.let(::repositoryDisplayName)
                ?: appText(AppTextKey.SourcesExternalRepositories),
            onBack = onBack,
            actions = {
                IconButton(onClick = { searchDialogOpen = true }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = appText(AppTextKey.SourcesExternalRepositorySearch),
                    )
                }
                IconButton(onClick = { languageFilterOpen = true }) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = appText(AppTextKey.SourcesExternalRepositoryLanguages),
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = appText(AppTextKey.SettingsExternalRepositoryRefresh),
                    )
                }
            },
        )
        if (searchDialogOpen) {
            AlertDialog(
                onDismissRequest = { searchDialogOpen = false },
                title = { Text(appText(AppTextKey.SourcesExternalRepositorySearch)) },
                text = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(onClick = { searchDialogOpen = false }) {
                        Text(appText(AppTextKey.Search))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { query = ""; searchDialogOpen = false }) {
                        Text(appText(AppTextKey.Cancel))
                    }
                },
            )
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
        repository?.error?.let { error ->
            Text(
                text = error.message ?: appText(AppTextKey.SettingsExternalRepositoryOperationFailed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (visiblePackages.isEmpty()) {
            SourceRepositoryEmptyState(
                text = appText(AppTextKey.SourcesExternalRepositoryPackagesEmpty),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visiblePackages, key = { it.sourceId.value }) { packageStatus ->
                    SourcePackageCard(
                        packageStatus = packageStatus,
                        busy = isBusy,
                        onInstall = { onInstall(packageStatus.sourceId) },
                        onRollback = { onRollback(packageStatus.sourceId) },
                    )
                }
            }
        }
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
    actions: @Composable RowScope.() -> Unit,
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
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
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
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = appText(AppTextKey.SettingsExternalRepositoryRemove))
            }
        }
    }
}

@Composable
private fun SourcePackageCard(
    packageStatus: ExternalSourcePackageStatus,
    busy: Boolean,
    onInstall: () -> Unit,
    onRollback: () -> Unit,
) {
    val manifest = packageStatus.availableManifest
    val title = manifest.sourceInfo?.displayName?.takeIf(String::isNotBlank) ?: manifest.sourceId.value
    val languages = manifest.sourceInfo?.languages.orEmpty().joinToString { it.tag.uppercase() }
    var manageExpanded by remember(packageStatus.sourceId, packageStatus.updateAvailable, packageStatus.rollbackAvailable) {
        mutableStateOf(false)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            AppSourceIconImage(
                url = manifest.sourceInfo?.iconUrl,
                sourceId = manifest.sourceId.value,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(languages.takeIf(String::isNotBlank), manifest.packageVersion).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (packageStatus.activePackage == null) {
                Button(onClick = onInstall, enabled = !busy) {
                    Text(appText(AppTextKey.SettingsExternalPackageInstall))
                }
            } else {
                Box {
                    Button(onClick = { manageExpanded = true }, enabled = !busy) {
                        Text(appText(AppTextKey.SettingsExternalPackageManage))
                    }
                    DropdownMenu(
                        expanded = manageExpanded,
                        onDismissRequest = { manageExpanded = false },
                    ) {
                        if (packageStatus.updateAvailable) {
                            DropdownMenuItem(
                                text = { Text(appText(AppTextKey.SettingsExternalPackageUpdate)) },
                                onClick = {
                                    manageExpanded = false
                                    onInstall()
                                },
                            )
                        }
                        if (packageStatus.rollbackAvailable) {
                            DropdownMenuItem(
                                text = { Text(appText(AppTextKey.SettingsExternalPackageRollback)) },
                                onClick = {
                                    manageExpanded = false
                                    onRollback()
                                },
                            )
                        }
                        if (!packageStatus.updateAvailable && !packageStatus.rollbackAvailable) {
                            DropdownMenuItem(
                                text = { Text(appText(AppTextKey.SettingsExternalPackageInstalled)) },
                                enabled = false,
                                onClick = {},
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun SourceRepositoryEmptyState(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
