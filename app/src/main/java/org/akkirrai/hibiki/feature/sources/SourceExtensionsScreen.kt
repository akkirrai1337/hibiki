package org.akkirrai.hibiki.feature.sources

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.extension.ExtensionMarketplaceClient
import org.akkirrai.hibiki.core.source.extension.ApkRepositoryExtension
import org.akkirrai.hibiki.core.source.extension.ApkExtensionInstaller
import org.akkirrai.hibiki.core.source.extension.PreparedApkExtensionInstall
import org.akkirrai.hibiki.core.source.extension.InstalledApkExtensions
import org.akkirrai.hibiki.core.source.extension.InstalledApkExtensionInfo
import org.akkirrai.hibiki.core.source.extension.MarketplaceExtension
import org.akkirrai.hibiki.core.source.extension.isExtensionVersionNewer
import org.akkirrai.hibiki.core.source.extension.ExternalApkExtension
import org.akkirrai.hibiki.core.source.extension.ExternalApkExtensions
import org.akkirrai.hibiki.core.source.extension.RepositoryCatalogCache
import org.akkirrai.hibiki.core.source.extension.SourceExtensionUpdateChecker
import org.akkirrai.hibiki.core.source.extension.isUpdateAvailable

/**
 * Sources tab: the "Extensions" page browses repositories and installs script and Android APK
 * extensions. APKs are installed by Android and kept privately for Hibiki's compatible source loader.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceExtensionsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    onboarding: Boolean = false,
    /** Lists the repositories' installable extensions under the installed ones on the Extensions tab. */
    showAvailable: Boolean = onboarding,
    onInstallationActiveChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var languageFilterOpen by remember { mutableStateOf(false) }
    // The installed list and an opened repository filter independently, and a repository starts unfiltered.
    var installedSelectedLanguages by remember { mutableStateOf(emptySet<String>()) }
    var repositorySelectedLanguages by remember { mutableStateOf(emptySet<String>()) }
    var addRepositoryDialogOpen by remember { mutableStateOf(false) }
    var repositoryPendingRemovalUrl by remember { mutableStateOf<String?>(null) }
    var selectedRepositoryUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedRepositoryUrl) { repositorySelectedLanguages = emptySet() }
    val selectedLanguages = if (selectedRepositoryUrl != null) repositorySelectedLanguages else installedSelectedLanguages
    var repositoryRefreshSignal by remember { mutableStateOf(0) }
    val marketplaceHttpClient = remember { AndroidHttpClientFactory.create() }
    DisposableEffect(marketplaceHttpClient) { onDispose { marketplaceHttpClient.close() } }
    var repoStates by remember { mutableStateOf<Map<String, RepoFetchResult>>(emptyMap()) }
    var installingApkPackages by remember { mutableStateOf(emptySet<String>()) }
    var apkInstallErrors by remember { mutableStateOf(emptyMap<String, String>()) }
    var pendingApkInstallPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingApkInstallPath by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingApkInstallPermission by rememberSaveable { mutableStateOf(false) }
    var pendingApkUninstallPackage by rememberSaveable { mutableStateOf<String?>(null) }
    // Starts from what the registry already scanned at startup, so the list is there on the first frame.
    var installedApkExtensions by remember {
        mutableStateOf(AnimeSourceRegistry.installedApkExtensions() ?: emptyMap())
    }
    LaunchedEffect(installingApkPackages.isNotEmpty()) {
        onInstallationActiveChanged(installingApkPackages.isNotEmpty())
    }

    val preferences = LocalAppPreferences.current
    val appPreferencesState = LocalAppPreferencesState.current
    val sourceRepositoryUrls = appPreferencesState.sourceRepositoryUrls
    val selectedSource = appPreferencesState.animeSource
    val haptic = LocalHapticFeedback.current
    val updateChecker = remember(context) { SourceExtensionUpdateChecker.get(context) }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val tabScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingApkInstall = remember(pendingApkInstallPackage, pendingApkInstallPath) {
        val packageName = pendingApkInstallPackage
        val path = pendingApkInstallPath
        if (packageName != null && path != null) {
            PendingApkInstall(packageName, PreparedApkExtensionInstall(packageName, java.io.File(path)))
        } else {
            null
        }
    }

    // Extensions installed by something other than Hibiki: listed, but only run once the user trusts them.
    var externalExtensions by remember { mutableStateOf<List<ExternalApkExtension>>(emptyList()) }
    var externalToTrust by remember { mutableStateOf<ExternalApkExtension?>(null) }

    suspend fun refreshInstalledApkExtensions() {
        val (detection, installed) = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val detection = ExternalApkExtensions.sync(context)
            detection to InstalledApkExtensions.scan(context)
        }
        installedApkExtensions = installed
        externalExtensions = detection.pending
        if (detection.changed) AnimeSourceRegistry.refreshApkExtensions(context)
    }

    val packageInstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pending = pendingApkInstall ?: return@rememberLauncherForActivityResult
        tabScope.launch {
            try {
                if (result.resultCode == Activity.RESULT_OK) {
                    val hadNoSources = AnimeSourceRegistry.sources.isEmpty()
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ApkExtensionInstaller.completeSystemInstall(context, pending.prepared)
                    }
                    refreshInstalledApkExtensions()
                    AnimeSourceRegistry.refreshApkExtensions(context)
                    if (hadNoSources) {
                        AnimeSourceRegistry.sources.firstOrNull()?.let { preferences.setAnimeSource(it.id) }
                    }
                } else {
                    ApkExtensionInstaller.cancelSystemInstall(pending.prepared)
                }
            } catch (error: Exception) {
                ApkExtensionInstaller.cancelSystemInstall(pending.prepared)
                AppLogger.w("SourceExtensions", "APK installation failed for ${pending.packageName}", error)
                apkInstallErrors = apkInstallErrors +
                    (pending.packageName to (error.message ?: context.getString(R.string.source_extensions_apk_install_failed)))
            } finally {
                pendingApkInstallPackage = null
                pendingApkInstallPath = null
                awaitingApkInstallPermission = false
                installingApkPackages = installingApkPackages - pending.packageName
            }
        }
    }

    val packageUninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val packageName = pendingApkUninstallPackage ?: return@rememberLauncherForActivityResult
        pendingApkUninstallPackage = null
        if (result.resultCode == Activity.RESULT_OK) {
            tabScope.launch {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ApkExtensionInstaller.completeSystemUninstall(context, packageName)
                }
                refreshInstalledApkExtensions()
                AnimeSourceRegistry.refreshApkExtensions(context)
            }
        }
    }

    LaunchedEffect(context) { refreshInstalledApkExtensions() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tabScope.launch {
                    refreshInstalledApkExtensions()
                    val pending = pendingApkInstall
                    if (awaitingApkInstallPermission && pending != null) {
                        if (ApkExtensionInstaller.canRequestPackageInstalls(context)) {
                            awaitingApkInstallPermission = false
                            packageInstallLauncher.launch(
                                ApkExtensionInstaller.packageInstallIntent(context, pending.prepared),
                            )
                        } else {
                            ApkExtensionInstaller.cancelSystemInstall(pending.prepared)
                            pendingApkInstallPackage = null
                            pendingApkInstallPath = null
                            awaitingApkInstallPermission = false
                            installingApkPackages = installingApkPackages - pending.packageName
                            apkInstallErrors = apkInstallErrors +
                                (pending.packageName to context.getString(R.string.source_extensions_apk_allow_installs))
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val apkRepositoryExtensions = remember(repoStates) {
        repoStates.values
            .filterIsInstance<RepoFetchResult.Loaded>()
            .flatMap(RepoFetchResult.Loaded::extensions)
            .distinctBy(ApkRepositoryExtension::pkg)
    }
    // Which repository serves each package, so an update is downloaded from the one that lists it.
    val repositoryUrlByPackage = remember(sourceRepositoryUrls, repoStates) {
        val origins = mutableMapOf<String, String>()
        sourceRepositoryUrls.forEach { url ->
            (repoStates[url] as? RepoFetchResult.Loaded)?.extensions?.forEach { extension ->
                origins.putIfAbsent(extension.pkg, url)
            }
        }
        origins
    }
    val extensionLanguages = apkRepositoryExtensions.map(ApkRepositoryExtension::lang).distinct().sorted()
    val selectedRepositoryExtensions = selectedRepositoryUrl
        ?.let { repoStates[it] as? RepoFetchResult.Loaded }
        ?.extensions
    // The Sources tab lists only what is installed, so its filter offers only those languages; the
    // repository lists offer the languages of what they carry.
    val installedLanguages = AnimeSourceRegistry.installedApkExtensions().orEmpty()
        .filterValues(InstalledApkExtensionInfo::isSystemInstalled)
        .keys
        .flatMap { packageName ->
            val loaded = AnimeSourceRegistry.sources
                .filter { AnimeSourceRegistry.apkPackageForSource(it.id) == packageName }
                .map { it.language.tag }
            loaded.ifEmpty {
                listOf(apkRepositoryExtensions.firstOrNull { it.pkg == packageName }?.lang?.ifBlank { "all" } ?: "all")
            }
        }
        .distinct()
        .sorted()
    val repositoryFilterLanguages = selectedRepositoryExtensions
        ?.map(ApkRepositoryExtension::lang)?.distinct()?.sorted()
        ?: if (pagerState.currentPage == 0) installedLanguages else extensionLanguages
    val showLanguageFilter = selectedRepositoryUrl == null && pagerState.currentPage == 0 ||
        selectedRepositoryExtensions != null

    val installedApkLoadErrors = AnimeSourceRegistry.apkExtensionLoadErrors()

    /** Downloads the index of each of [urls]. A repository that already has an index keeps showing it meanwhile and if the fetch fails. */
    suspend fun loadRepositories(urls: List<String>) {
        if (urls.isEmpty()) return
        repoStates = repoStates + urls
            .filter { repoStates[it] !is RepoFetchResult.Loaded }
            .associateWith { RepoFetchResult.Loading }
        val results = coroutineScope {
            urls.map { url ->
                async {
                    url to try {
                        val extensions = ExtensionMarketplaceClient(marketplaceHttpClient, url).fetchCatalog()
                        withContext(kotlinx.coroutines.Dispatchers.IO) { RepositoryCatalogCache.put(context, url, extensions) }
                        RepoFetchResult.Loaded(extensions)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        RepoFetchResult.Error(error.message ?: error.toString())
                    }
                }
            }.awaitAll()
        }
        repoStates = repoStates + results.filter { (url, result) ->
            result is RepoFetchResult.Loaded || repoStates[url] !is RepoFetchResult.Loaded
        }
    }

    // The installed list needs no network: icons and update info come from the last index fetched
    // for each repository (memory or disk). The network is used only when a repository has no index
    // yet, when the repositories tab or one repository is open and its index is stale, in onboarding
    // (where the list is what to install), or when the user presses refresh.
    val browsingRepositories = onboarding || showAvailable || pagerState.currentPage == 1 || selectedRepositoryUrl != null
    var handledRefreshSignal by remember { mutableStateOf(0) }
    LaunchedEffect(sourceRepositoryUrls, browsingRepositories, repositoryRefreshSignal) {
        val forced = repositoryRefreshSignal != handledRefreshSignal
        handledRefreshSignal = repositoryRefreshSignal
        val cached = withContext(kotlinx.coroutines.Dispatchers.IO) {
            sourceRepositoryUrls.associateWith { RepositoryCatalogCache.get(context, it) }
        }
        repoStates = repoStates.filterKeys { it in sourceRepositoryUrls } + cached.mapNotNull { (url, snapshot) ->
            snapshot?.let { url to RepoFetchResult.Loaded(it.extensions) }
        }.filter { (url, _) -> repoStates[url] !is RepoFetchResult.Loaded }
        val toFetch = sourceRepositoryUrls.filter { url ->
            val snapshot = cached[url]
            forced || snapshot == null || (browsingRepositories && snapshot.isStale())
        }
        loadRepositories(toFetch)
    }
    // An extension is trusted when an added repository lists it, so the list of trusted ones can change
    // once an index arrives.
    val loadedRepositoryCount = repoStates.count { it.value is RepoFetchResult.Loaded }
    LaunchedEffect(loadedRepositoryCount, sourceRepositoryUrls) { refreshInstalledApkExtensions() }
    // Do not make a second request purely for the bottom-navigation badge: this screen already
    // fetched the index for an explicit visit or a manual refresh, so use that same snapshot.
    // In particular, app startup and returning from the background must remain fully offline.
    LaunchedEffect(apkRepositoryExtensions, installedApkExtensions) {
        updateChecker.updateFrom(apkRepositoryExtensions)
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
    BackHandler(enabled = searchOpen || selectedRepositoryUrl != null) {
        if (searchOpen) {
            query = ""
            searchOpen = false
        } else {
            selectedRepositoryUrl = null
        }
    }
    val invalidRepositoryUrlMessage = stringResource(R.string.source_extensions_repositories_invalid_url)
    val apkInstallFailedMessage = stringResource(R.string.source_extensions_apk_install_failed)

    val installApkExtension: (String, ApkRepositoryExtension) -> Unit = { repositoryUrl, extension ->
        installingApkPackages = installingApkPackages + extension.pkg
        apkInstallErrors = apkInstallErrors - extension.pkg
        tabScope.launch {
            try {
                val prepared = ApkExtensionInstaller.prepareSystemInstall(
                    context = context,
                    client = marketplaceHttpClient,
                    indexUrl = repositoryUrl,
                    extension = extension,
                )
                pendingApkInstallPackage = prepared.packageName
                pendingApkInstallPath = prepared.apkFile.absolutePath
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !ApkExtensionInstaller.canRequestPackageInstalls(context)
                ) {
                    awaitingApkInstallPermission = true
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                } else {
                    packageInstallLauncher.launch(ApkExtensionInstaller.packageInstallIntent(context, prepared))
                }
            } catch (error: Exception) {
                AppLogger.w("SourceExtensions", "APK installation failed for ${extension.pkg}", error)
                apkInstallErrors = apkInstallErrors +
                    (extension.pkg to (error.message ?: apkInstallFailedMessage))
                pendingApkInstall?.takeIf { it.packageName == extension.pkg }?.let {
                    ApkExtensionInstaller.cancelSystemInstall(it.prepared)
                    pendingApkInstallPackage = null
                    pendingApkInstallPath = null
                }
                awaitingApkInstallPermission = false
                installingApkPackages = installingApkPackages - extension.pkg
            }
        }
    }

    val uninstallApkExtension: (String) -> Unit = { packageName ->
        if (ApkExtensionInstaller.wasInstalledBySystem(context, packageName)) {
            pendingApkUninstallPackage = packageName
            packageUninstallLauncher.launch(ApkExtensionInstaller.packageUninstallIntent(packageName))
        } else {
            ApkExtensionInstaller.removePrivateCopy(context, packageName)
            tabScope.launch {
                refreshInstalledApkExtensions()
                AnimeSourceRegistry.refreshApkExtensions(context)
            }
        }
    }
    externalToTrust?.let { external ->
        ExternalExtensionTrustDialog(
            extension = external,
            onTrust = {
                externalToTrust = null
                tabScope.launch {
                    val adopted = withContext(kotlinx.coroutines.Dispatchers.IO) { ExternalApkExtensions.adopt(context, external) }
                    if (adopted) {
                        refreshInstalledApkExtensions()
                        AnimeSourceRegistry.refreshApkExtensions(context)
                        // Trusting it was the answer to "use this source", so make it the active one.
                        AnimeSourceRegistry.sources
                            .firstOrNull { AnimeSourceRegistry.apkPackageForSource(it.id) == external.packageName }
                            ?.let { preferences.setAnimeSource(it.id) }
                    } else {
                        apkInstallErrors = apkInstallErrors +
                            (external.packageName to context.getString(R.string.source_extensions_apk_install_failed))
                    }
                }
            },
            onDismiss = { externalToTrust = null },
        )
    }
    val repositoryContent: @Composable (String) -> Unit = { repositoryUrl ->
        when (val result = repoStates[repositoryUrl]) {
            is RepoFetchResult.Loaded -> ApkRepositoryList(
                repositoryUrl = repositoryUrl,
                extensions = result.extensions,
                bottomContentPadding = bottomContentPadding,
                query = query,
                selectedLanguages = selectedLanguages,
                hideNsfwSources = appPreferencesState.hideNsfwSources,
                selectedSource = selectedSource,
                installingPackages = installingApkPackages,
                installErrors = apkInstallErrors,
                installedExtensions = installedApkExtensions,
                onInstall = installApkExtension,
                onSelect = { packageName ->
                    AnimeSourceRegistry.sourceIdsForApkPackage(packageName).firstOrNull()?.let { sourceId ->
                        preferences.setAnimeSource(sourceId)
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                },
                onUninstall = uninstallApkExtension,
            )
            is RepoFetchResult.Error -> SourceRepositoryMessage(
                message = stringResource(R.string.source_extensions_repository_error),
                detail = result.message,
                onRetry = { tabScope.launch { loadRepositories(listOf(repositoryUrl)) } },
            )
            RepoFetchResult.Loading, null ->
                SourceRepositoryMessage(stringResource(R.string.source_extensions_repository_loading))
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        SourceExtensionsToolbar(
            title = selectedRepositoryUrl?.let(::repositoryTitle)
                ?: stringResource(R.string.nav_sources),
            onBack = selectedRepositoryUrl?.let { { selectedRepositoryUrl = null } },
            searchOpen = searchOpen,
            showFilter = showLanguageFilter,
            filterCount = selectedLanguages.size,
            query = query,
            onQueryChange = { query = it },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                query = ""
                searchOpen = false
            },
            onFilterClick = { languageFilterOpen = true },
            showAddRepository = selectedRepositoryUrl == null && pagerState.currentPage == 1,
            onAddRepository = { addRepositoryDialogOpen = true },
            onRefresh = {
                repositoryRefreshSignal++
            },
        )
        AnimatedContent(
            targetState = selectedRepositoryUrl,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally(animationSpec = tween(240)) { it / 3 } + fadeIn(tween(180)) togetherWith
                        slideOutHorizontally(animationSpec = tween(200)) { -it / 6 } + fadeOut(tween(120))
                } else {
                    slideInHorizontally(animationSpec = tween(240)) { -it / 3 } + fadeIn(tween(180)) togetherWith
                        slideOutHorizontally(animationSpec = tween(200)) { it / 6 } + fadeOut(tween(120))
                }
            },
            label = "SourceRepositoryNavigation",
        ) { repositoryUrl ->
            if (repositoryUrl != null) {
                repositoryContent(repositoryUrl)
            } else {
                Column(Modifier.fillMaxSize()) {
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
                            InstalledSourcesList(
                                bottomContentPadding = bottomContentPadding,
                                query = query,
                                selectedLanguages = selectedLanguages,
                                hideNsfwSources = appPreferencesState.hideNsfwSources,
                                selectedSource = selectedSource,
                                installingPackages = installingApkPackages,
                                installErrors = apkInstallErrors,
                                installedApkExtensions = installedApkExtensions,
                                externalExtensions = externalExtensions,
                                apkRepositoryExtensions = apkRepositoryExtensions,
                                availableExtensions = if (showAvailable) apkRepositoryExtensions else emptyList(),
                                repositoryUrlByPackage = repositoryUrlByPackage,
                                onInstallAvailable = installApkExtension,
                                apkLoadErrors = installedApkLoadErrors,
                                onUpdate = { extension ->
                                    repositoryUrlByPackage[extension.pkg]?.let { installApkExtension(it, extension) }
                                },
                                onSelect = { sourceId ->
                                    val external = externalExtensions
                                        .firstOrNull { "$APK_PACKAGE_ROW_PREFIX${it.packageName}" == sourceId }
                                    if (external != null) {
                                        externalToTrust = external
                                    } else {
                                        preferences.setAnimeSource(SourceId(sourceId))
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                },
                                onUninstall = { sourceId ->
                                    val packageName = if (sourceId.startsWith(APK_PACKAGE_ROW_PREFIX)) {
                                        sourceId.removePrefix(APK_PACKAGE_ROW_PREFIX)
                                    } else {
                                        AnimeSourceRegistry.apkPackageForSource(SourceId(sourceId))
                                    }
                                    if (packageName != null && externalExtensions.any { it.packageName == packageName }) {
                                        // Not Hibiki's install to remove: Android's own uninstall handles it.
                                        pendingApkUninstallPackage = packageName
                                        packageUninstallLauncher.launch(ApkExtensionInstaller.packageUninstallIntent(packageName))
                                    } else {
                                        packageName?.let(uninstallApkExtension)
                                    }
                                },
                            )
                        } else {
                            RepositoriesList(
                                urls = sourceRepositoryUrls,
                                repoStates = repoStates,
                                bottomContentPadding = bottomContentPadding,
                                onOpen = { url ->
                                    query = ""
                                    searchOpen = false
                                    selectedRepositoryUrl = url
                                },
                                onRemove = { url -> repositoryPendingRemovalUrl = url },
                            )
                        }
                    }
                }
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
            validate = { url ->
                if (!isHttpsRepositoryIndexUrl(url)) {
                    invalidRepositoryUrlMessage
                } else {
                    runCatching {
                        ExtensionMarketplaceClient(marketplaceHttpClient, url).fetchCatalog()
                    }.exceptionOrNull()?.message
                }
            },
        )
    }

    repositoryPendingRemovalUrl?.let { url ->
        RemoveRepositoryDialog(
            url = url,
            onConfirm = {
                preferences.removeSourceRepository(url)
                repositoryPendingRemovalUrl = null
            },
            onDismiss = { repositoryPendingRemovalUrl = null },
        )
    }

    if (languageFilterOpen) {
        SourceLanguageFilterDialog(
            languages = (repositoryFilterLanguages + selectedLanguages).distinct().sorted(),
            selectedLanguages = selectedLanguages,
            onLanguageToggle = { language ->
                val updated = if (language in selectedLanguages) selectedLanguages - language else selectedLanguages + language
                if (selectedRepositoryUrl != null) repositorySelectedLanguages = updated else installedSelectedLanguages = updated
            },
            onDismiss = { languageFilterOpen = false },
        )
    }
}

@Composable
private fun SourceExtensionsToolbar(
    title: String,
    onBack: (() -> Unit)?,
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
            .height(52.dp),
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
                barHeight = 50.dp,
                modifier = Modifier.weight(1f),
            )
        } else {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                val allLanguagesLabel = stringResource(R.string.source_extensions_language_all)
                // "All" first, then by English name - the codes themselves are not an order anyone reads.
                val presentations = languages.associateWith { sourceLanguagePresentation(it, allLanguagesLabel) }
                languages.sortedWith(
                    compareBy<String> { it.lowercase() != "all" }.thenBy { presentations.getValue(it).englishName.lowercase() },
                ).forEach { language ->
                    val presentation = presentations.getValue(language)
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

/**
 * A language tag as people read it: its own name and the English one. The platform knows the names
 * of every real language, so nothing here is a list to keep in step with the repositories; a tag it
 * does not recognise is shown as itself.
 */
private fun sourceLanguagePresentation(language: String, allLanguagesLabel: String): SourceLanguagePresentation {
    if (language.equals("all", ignoreCase = true)) return SourceLanguagePresentation(allLanguagesLabel, "Multi-language")
    val locale = java.util.Locale.forLanguageTag(language.replace('_', '-'))
    // An unknown tag has no display name and comes back as the tag itself (or blank).
    fun name(inLocale: java.util.Locale): String? =
        locale.getDisplayName(inLocale).takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(inLocale) }
    val english = name(java.util.Locale.ENGLISH)
    val native = name(locale)
    return if (english == null) {
        SourceLanguagePresentation(language.uppercase(), language.uppercase())
    } else {
        SourceLanguagePresentation(native ?: english, english)
    }
}

/** The short code shown next to a version: the tag in capitals, with Ukrainian as UA (the country people know it by). */
private fun languageBadge(language: String): String = when (language.lowercase()) {
    "uk" -> "UA"
    else -> language.uppercase()
}

/** One connected repository's own fetch outcome - kept separate per URL so one broken repository
 * doesn't blank out extensions from the others in the merged [RepositoryLoadState] Extensions
 * tab consumes. */
private sealed interface RepoFetchResult {
    data object Loading : RepoFetchResult
    data class Error(val message: String) : RepoFetchResult
    data class Loaded(val extensions: List<ApkRepositoryExtension>) : RepoFetchResult
}

private fun ApkRepositoryExtension.toMarketplaceExtension() = MarketplaceExtension(
    id = pkg,
    name = name,
    version = version,
    iconUrl = iconUrl,
    lang = lang,
    isNsfw = nsfw != 0,
)

private data class PendingApkInstall(
    val packageName: String,
    val prepared: PreparedApkExtensionInstall,
)

@Composable
private fun ApkRepositoryExtensionRow(
    repositoryUrl: String,
    extension: ApkRepositoryExtension,
    installingPackages: Set<String>,
    installErrors: Map<String, String>,
    installedExtensions: Map<String, InstalledApkExtensionInfo>,
    selectedSource: SourceId,
    onInstall: (String, ApkRepositoryExtension) -> Unit,
    onSelect: (String) -> Unit,
    onUninstall: () -> Unit,
) {
    val installed = installedExtensions[extension.pkg]
        ?.takeIf(InstalledApkExtensionInfo::isSystemInstalled)
    val sourceIds = AnimeSourceRegistry.sourceIdsForApkPackage(extension.pkg)
    MarketplaceExtensionRow(
        extension = extension.toMarketplaceExtension(),
        installedVersion = installed?.versionName,
        installing = extension.pkg in installingPackages,
        installingLabel = stringResource(R.string.source_extensions_apk_installing),
        errorMessage = installErrors[extension.pkg],
        selected = sourceIds.any { it == selectedSource },
        selectable = sourceIds.isNotEmpty(),
        onInstall = { onInstall(repositoryUrl, extension) },
        onSelect = { onSelect(extension.pkg) },
        onUninstall = onUninstall,
    )
}

@Composable
private fun ApkRepositoryList(
    repositoryUrl: String,
    extensions: List<ApkRepositoryExtension>,
    bottomContentPadding: Dp,
    query: String,
    selectedLanguages: Set<String>,
    hideNsfwSources: Boolean,
    selectedSource: SourceId,
    installingPackages: Set<String>,
    installErrors: Map<String, String>,
    installedExtensions: Map<String, InstalledApkExtensionInfo>,
    onInstall: (String, ApkRepositoryExtension) -> Unit,
    onSelect: (String) -> Unit,
    onUninstall: (String) -> Unit,
) {
    var uninstallDialogExtension by remember { mutableStateOf<ApkRepositoryExtension?>(null) }
    val visibleExtensions = remember(extensions, query, selectedLanguages, hideNsfwSources) {
        extensions.filter { extension ->
            val matchesQuery = query.isBlank() || extension.name.contains(query, ignoreCase = true) ||
                extension.pkg.contains(query, ignoreCase = true)
            val matchesLanguage = selectedLanguages.isEmpty() || extension.lang in selectedLanguages
            val matchesContentRating = !hideNsfwSources || extension.nsfw == 0
            matchesQuery && matchesLanguage && matchesContentRating
        }
    }
    if (visibleExtensions.isEmpty()) {
        SourceRepositoryMessage(stringResource(R.string.source_extensions_repository_empty))
        return
    }
    val installed = visibleExtensions.filter { installedExtensions[it.pkg]?.isSystemInstalled == true }
    val updates = installed.filter { extension ->
        isExtensionVersionNewer(extension.version, installedExtensions.getValue(extension.pkg).versionName)
    }
    val upToDate = installed - updates.toSet()
    val available = visibleExtensions.filterNot { installedExtensions[it.pkg]?.isSystemInstalled == true }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomContentPadding + 16.dp,
        ),
    ) {
        if (updates.isNotEmpty()) {
            item(key = "apk_available_updates") {
                SourceExtensionSectionHeader(
                    title = stringResource(R.string.source_extensions_updates_section),
                    action = {
                        OutlinedButton(onClick = { updates.forEach { onInstall(repositoryUrl, it) } }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(18.dp),
                            )
                            Text(stringResource(R.string.source_extensions_update_all))
                        }
                    },
                )
            }
        }
        items(updates, key = ApkRepositoryExtension::pkg) { extension ->
            ApkRepositoryExtensionRow(
                repositoryUrl = repositoryUrl,
                extension = extension,
                installingPackages = installingPackages,
                installErrors = installErrors,
                installedExtensions = installedExtensions,
                selectedSource = selectedSource,
                onInstall = onInstall,
                onSelect = onSelect,
                onUninstall = { uninstallDialogExtension = extension },
            )
        }
        if (upToDate.isNotEmpty()) {
            item(key = "apk_installed_extensions") {
                SourceExtensionSectionHeader(
                    title = stringResource(R.string.source_extensions_installed_section, upToDate.size),
                    separatedFromPrevious = updates.isNotEmpty(),
                )
            }
        }
        items(upToDate, key = ApkRepositoryExtension::pkg) { extension ->
            ApkRepositoryExtensionRow(
                repositoryUrl = repositoryUrl,
                extension = extension,
                installingPackages = installingPackages,
                installErrors = installErrors,
                installedExtensions = installedExtensions,
                selectedSource = selectedSource,
                onInstall = onInstall,
                onSelect = onSelect,
                onUninstall = { uninstallDialogExtension = extension },
            )
        }
        if (available.isNotEmpty()) {
            item(key = "apk_available_extensions") {
                SourceExtensionSectionHeader(
                    title = stringResource(R.string.source_extensions_available_section, available.size),
                    separatedFromPrevious = installed.isNotEmpty(),
                )
            }
        }
        items(available, key = ApkRepositoryExtension::pkg) { extension ->
            ApkRepositoryExtensionRow(
                repositoryUrl = repositoryUrl,
                extension = extension,
                installingPackages = installingPackages,
                installErrors = installErrors,
                installedExtensions = installedExtensions,
                selectedSource = selectedSource,
                onInstall = onInstall,
                onSelect = onSelect,
                onUninstall = { uninstallDialogExtension = extension },
            )
        }
    }
    uninstallDialogExtension?.let { extension ->
        AlertDialog(
            onDismissRequest = { uninstallDialogExtension = null },
            title = { Text(stringResource(R.string.source_extensions_apk_uninstall_title)) },
            text = { Text(stringResource(R.string.source_extensions_apk_uninstall_message, extension.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUninstall(extension.pkg)
                        uninstallDialogExtension = null
                    },
                ) { Text(stringResource(R.string.source_extensions_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { uninstallDialogExtension = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private data class InstalledApkSourceEntry(
    val extension: MarketplaceExtension,
    val packageName: String,
    val installedVersion: String,
    val repositoryEntry: ApkRepositoryExtension? = null,
    val loadError: String? = null,
    val selectable: Boolean = true,
    val settingsAvailable: Boolean = true,
)

private const val APK_PACKAGE_ROW_PREFIX = "apk-package:"

@Composable
private fun InstalledSourcesList(
    bottomContentPadding: Dp,
    query: String,
    selectedLanguages: Set<String>,
    hideNsfwSources: Boolean,
    selectedSource: SourceId,
    installingPackages: Set<String>,
    installErrors: Map<String, String>,
    installedApkExtensions: Map<String, InstalledApkExtensionInfo>,
    externalExtensions: List<ExternalApkExtension>,
    apkRepositoryExtensions: List<ApkRepositoryExtension>,
    availableExtensions: List<ApkRepositoryExtension>,
    repositoryUrlByPackage: Map<String, String>,
    onInstallAvailable: (String, ApkRepositoryExtension) -> Unit,
    apkLoadErrors: Map<String, String>,
    onUpdate: (ApkRepositoryExtension) -> Unit,
    onSelect: (String) -> Unit,
    onUninstall: (String) -> Unit,
) {
    // Until the extensions have been loaded once, "no usable source" only means "not loaded yet".
    val extensionsLoaded = AnimeSourceRegistry.installedApkExtensions() != null
    var settingsSheetSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val installedVersions = installedApkExtensions
        .filterValues(InstalledApkExtensionInfo::isSystemInstalled)
        .mapValues { it.value.versionName }

    // Sources of extensions that loaded; the repository index supplies the name's icon and any newer version.
    val loadedSources = AnimeSourceRegistry.sources.mapNotNull { descriptor ->
        val packageName = AnimeSourceRegistry.apkPackageForSource(descriptor.id) ?: return@mapNotNull null
        val apkInfo = installedApkExtensions[packageName]
            ?.takeIf(InstalledApkExtensionInfo::isSystemInstalled)
            ?: return@mapNotNull null
        val repositoryEntry = apkRepositoryExtensions.firstOrNull { it.pkg == packageName }
        val updateAvailable = repositoryEntry?.isUpdateAvailable(installedVersions) == true
        InstalledApkSourceEntry(
            extension = MarketplaceExtension(
                id = descriptor.id.value,
                name = descriptor.name,
                // The row compares this with the installed version to decide whether to offer an update.
                version = if (updateAvailable) repositoryEntry!!.version else apkInfo.versionName,
                // An APK source has no icon of its own; the repository index carries it.
                iconUrl = descriptor.iconUrl ?: repositoryEntry?.iconUrl,
                lang = descriptor.language.tag,
                isNsfw = (repositoryEntry?.nsfw ?: 0) != 0,
            ),
            packageName = packageName,
            installedVersion = apkInfo.versionName,
            repositoryEntry = repositoryEntry,
        )
    }
    val representedPackages = loadedSources.mapTo(mutableSetOf(), InstalledApkSourceEntry::packageName)
    // Installed APKs that produced no usable source: shown with the reason, so they can be removed or fixed.
    // From the registry's own snapshot, which changes together with the loaded sources, so an extension
    // is never shown as failed in the moment between being installed and being loaded.
    val failedSources = AnimeSourceRegistry.installedApkExtensions().orEmpty().mapNotNull { (packageName, info) ->
        if (!extensionsLoaded || !info.isSystemInstalled || packageName in representedPackages) return@mapNotNull null
        if (externalExtensions.any { it.packageName == packageName }) return@mapNotNull null
        val repositoryEntry = apkRepositoryExtensions.firstOrNull { it.pkg == packageName }
        val errorMessage = apkLoadErrors[packageName]
            ?: if (!info.isTrusted) {
                "APK signing certificate is not trusted by Hibiki."
            } else {
                "Installed APK did not register a usable anime source."
            }
        InstalledApkSourceEntry(
            extension = MarketplaceExtension(
                id = "$APK_PACKAGE_ROW_PREFIX$packageName",
                name = repositoryEntry?.name ?: packageName,
                version = info.versionName,
                iconUrl = repositoryEntry?.iconUrl,
                lang = repositoryEntry?.lang?.ifBlank { "all" } ?: "all",
                isNsfw = (repositoryEntry?.nsfw ?: 0) != 0,
            ),
            packageName = packageName,
            installedVersion = info.versionName,
            repositoryEntry = repositoryEntry,
            loadError = errorMessage,
            selectable = false,
        )
    }
    // Not trusted: installed elsewhere, or from a repository that has since been removed. Shown like any
    // source, and tapping one asks first.
    val externalSources = externalExtensions.map { external ->
        val repositoryEntry = apkRepositoryExtensions.firstOrNull { it.pkg == external.packageName }
        InstalledApkSourceEntry(
            extension = MarketplaceExtension(
                id = "$APK_PACKAGE_ROW_PREFIX${external.packageName}",
                name = repositoryEntry?.name ?: external.label,
                version = external.versionName,
                iconUrl = repositoryEntry?.iconUrl,
                lang = repositoryEntry?.lang?.ifBlank { "all" } ?: "all",
                isNsfw = (repositoryEntry?.nsfw ?: 0) != 0,
            ),
            packageName = external.packageName,
            installedVersion = external.versionName,
            repositoryEntry = null,
            settingsAvailable = false,
        )
    }
    val entries = (loadedSources + failedSources + externalSources).filter { entry ->
        val matchesQuery = query.isBlank() ||
            entry.extension.name.contains(query, ignoreCase = true) ||
            entry.extension.id.contains(query, ignoreCase = true)
        val matchesLanguage = selectedLanguages.isEmpty() || entry.extension.lang in selectedLanguages
        val matchesContentRating = !hideNsfwSources || !entry.extension.isNsfw
        matchesQuery && matchesLanguage && matchesContentRating
    }

    val available = availableExtensions.filter { extension ->
        installedApkExtensions[extension.pkg]?.isSystemInstalled != true &&
            (query.isBlank() || extension.name.contains(query, ignoreCase = true) || extension.pkg.contains(query, ignoreCase = true)) &&
            (selectedLanguages.isEmpty() || extension.lang in selectedLanguages) &&
            (!hideNsfwSources || extension.nsfw == 0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
    ) {
        if (entries.isEmpty() && available.isEmpty()) {
            if (extensionsLoaded) SourceRepositoryMessage(stringResource(R.string.source_extensions_installed_empty))
        } else {
            val updates = entries.filter { it.repositoryEntry?.isUpdateAvailable(installedVersions) == true }
            val upToDate = entries - updates.toSet()
            // The nav-bar reservation belongs in the LazyColumn's own contentPadding (like
            // CatalogScreen/LibraryScreen do), not on a wrapping Column: padding a Column shrinks its
            // measured height, so the list would inset its content by the same amount a second time.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding + 16.dp,
                ),
            ) {
                if (updates.isNotEmpty()) {
                    item(key = "available_updates") {
                        SourceExtensionSectionHeader(title = stringResource(R.string.source_extensions_updates_section))
                    }
                    items(updates, key = { "apk_${it.extension.id}" }) { entry ->
                        InstalledSourceRow(
                            entry = entry,
                            installing = entry.packageName in installingPackages,
                            errorMessage = entry.loadError ?: installErrors[entry.packageName],
                            selectedSource = selectedSource,
                            onUpdate = onUpdate,
                            onSelect = onSelect,
                            onUninstall = onUninstall,
                            onOpenSettings = { settingsSheetSourceId = it },
                        )
                    }
                }
                if (upToDate.isNotEmpty()) {
                    item(key = "installed_extensions") {
                        SourceExtensionSectionHeader(
                            title = stringResource(R.string.source_extensions_installed_section, upToDate.size),
                            separatedFromPrevious = updates.isNotEmpty(),
                        )
                    }
                    items(upToDate, key = { "apk_${it.extension.id}" }) { entry ->
                        InstalledSourceRow(
                            entry = entry,
                            installing = entry.packageName in installingPackages,
                            errorMessage = entry.loadError ?: installErrors[entry.packageName],
                            selectedSource = selectedSource,
                            onUpdate = onUpdate,
                            onSelect = onSelect,
                            onUninstall = onUninstall,
                            onOpenSettings = { settingsSheetSourceId = it },
                        )
                    }
                }
                if (available.isNotEmpty()) {
                    item(key = "available_extensions") {
                        SourceExtensionSectionHeader(
                            title = stringResource(R.string.source_extensions_available_section, available.size),
                            separatedFromPrevious = entries.isNotEmpty(),
                        )
                    }
                    items(available, key = { "available_${it.pkg}" }) { extension ->
                        ApkRepositoryExtensionRow(
                            repositoryUrl = repositoryUrlByPackage[extension.pkg].orEmpty(),
                            extension = extension,
                            installingPackages = installingPackages,
                            installErrors = installErrors,
                            installedExtensions = installedApkExtensions,
                            selectedSource = selectedSource,
                            onInstall = onInstallAvailable,
                            onSelect = {},
                            onUninstall = {},
                        )
                    }
                }
            }
        }
    }

    settingsSheetSourceId?.let { id ->
        ExtensionSettingsSheet(
            sourceId = SourceId(id),
            title = remember(id) { AnimeSourceRegistry.descriptor(SourceId(id)).name },
            onDismissRequest = { settingsSheetSourceId = null },
        )
    }
}

@Composable
private fun InstalledSourceRow(
    entry: InstalledApkSourceEntry,
    installing: Boolean,
    errorMessage: String?,
    selectedSource: SourceId,
    onUpdate: (ApkRepositoryExtension) -> Unit,
    onSelect: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
) {
    MarketplaceExtensionRow(
        extension = entry.extension,
        installedVersion = entry.installedVersion,
        installing = installing,
        errorMessage = errorMessage,
        selected = entry.extension.id == selectedSource.value,
        selectable = entry.selectable,
        onInstall = { entry.repositoryEntry?.let(onUpdate) },
        onSelect = { onSelect(entry.extension.id) },
        onUninstall = { onUninstall("$APK_PACKAGE_ROW_PREFIX${entry.packageName}") },
        // Rows for extensions that produced no source carry a package id, not a source id.
        onOpenSettings = entry.takeIf { it.selectable && it.settingsAvailable }?.let { AnimeSourceRegistry.apkSourceSettings(SourceId(it.extension.id)) }?.let {
            { onOpenSettings(entry.extension.id) }
        },
    )
}

@Composable
private fun SourceExtensionSectionHeader(
    title: String,
    separatedFromPrevious: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (separatedFromPrevious) 24.dp else 8.dp, bottom = 8.dp),
    ) {
        if (separatedFromPrevious) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            action?.invoke()
        }
    }
}

@Composable
private fun RepositoriesList(
    urls: List<String>,
    repoStates: Map<String, RepoFetchResult>,
    bottomContentPadding: Dp,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
    ) {
        if (urls.isEmpty()) {
            SourceRepositoryMessage(stringResource(R.string.source_extensions_repositories_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding + 8.dp,
                ),
            ) {
                items(urls, key = { it }) { url ->
                    RepositoryCard(
                        url = url,
                        state = repoStates[url],
                        removable = url != ExtensionMarketplaceClient.DEFAULT_INDEX_URL,
                        onClick = { onOpen(url) },
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    ElevatedCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
                        is RepoFetchResult.Loaded -> stringResource(
                            R.string.source_extensions_repositories_extension_count,
                            state.extensions.size,
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

private fun repositoryTitle(url: String): String {
    val uri = Uri.parse(url)
    val segments = uri.pathSegments
    return when (uri.host?.lowercase()) {
        "raw.githubusercontent.com" -> segments.getOrNull(1)
        "github.com" -> segments.getOrNull(1)
        else -> null
    }?.takeIf(String::isNotBlank) ?: repositoryDisplayName(url)
}

@Composable
private fun RemoveRepositoryDialog(
    url: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_extensions_repositories_remove_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.source_extensions_repositories_remove_confirm_message,
                    repositoryDisplayName(url),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.source_extensions_repositories_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun isHttpsRepositoryIndexUrl(url: String): Boolean = runCatching {
    Uri.parse(url).let { uri ->
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }
}.getOrDefault(false)

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
    installing: Boolean,
    installingLabel: String? = null,
    errorMessage: String?,
    selected: Boolean,
    selectable: Boolean = true,
    onInstall: () -> Unit,
    onSelect: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val upToDate = installedVersion != null && !isExtensionVersionNewer(extension.version, installedVersion)
    val versionLabel = when {
        installedVersion != null && !upToDate -> "$installedVersion → ${extension.version}"
        installedVersion != null -> installedVersion
        else -> extension.version
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = installedVersion != null && !installing && selectable,
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
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${languageBadge(extension.lang)} · $versionLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (installedVersion != null && !upToDate) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (extension.isNsfw) {
                        Text(
                            text = " · 18+",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (installing) {
                Button(onClick = {}, enabled = false) {
                    if (installingLabel != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(installingLabel, modifier = Modifier.padding(start = 8.dp))
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (installedVersion == null) {
                Button(onClick = onInstall, enabled = !installing) {
                    Text(stringResource(R.string.source_extensions_install))
                }
            } else {
                ExtensionManageButton(
                    enabled = !installing,
                    updateAvailable = !upToDate,
                    onUpdate = onInstall,
                    onUninstall = onUninstall,
                    onOpenSettings = onOpenSettings,
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
private fun ExtensionManageButton(
    enabled: Boolean,
    updateAvailable: Boolean,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
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
            if (onOpenSettings != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.source_extensions_settings)) },
                    onClick = {
                        menuExpanded = false
                        onOpenSettings()
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

/** Asks whether to run an extension that was installed by something other than Hibiki. */
@Composable
private fun ExternalExtensionTrustDialog(
    extension: ExternalApkExtension,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    val installer = extension.installerLabel
        ?: extension.installerPackage
        ?: stringResource(R.string.source_extensions_external_installer_unknown)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_extensions_external_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.source_extensions_external_message,
                        extension.label,
                        extension.versionName,
                        extension.packageName,
                        installer,
                    ),
                )
                Text(
                    text = stringResource(R.string.source_extensions_external_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text(stringResource(R.string.source_extensions_external_trust)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_extensions_external_not_now)) } },
    )
}
