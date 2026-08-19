package org.akkirrai.hibiki.app.destination.source

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.core.source.AppAddSourceRepositoryDialog
import org.akkirrai.hibiki.core.source.AppExternalSourcePackageInfoScreen
import org.akkirrai.hibiki.core.source.AppSourceConfigContent
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.core.source.AppSourceRepositoriesScreen
import org.akkirrai.hibiki.core.source.AppSourcesTabsScreen
import org.akkirrai.hibiki.core.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.core.source.ExternalSourceRepositoryUiState
import org.akkirrai.hibiki.core.source.SourcesSearchUiState

internal data class AppDestinationSourceSearchActions(
    val onQueryChange: (String) -> Unit,
    val onClear: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryForSource: (String) -> Unit,
)

internal data class AppDestinationSourceState(
    val sources: List<AppSourceDescriptor>,
    val selectedSourceId: String?,
    val search: SourcesSearchUiState,
)

internal data class AppDestinationExternalSourcesState(
    val repository: ExternalSourceRepositoryUiState?,
    val controller: ExternalSourceRepositoryController?,
    val selectedTab: Int = 0,
    val onSelectedTabChange: (Int) -> Unit = {},
    val readClipboardText: () -> String? = { null },
    val copyText: (String) -> Unit = {},
)

@Composable
internal fun SourcesDestinationRoute(
    editingSourceConfig: AppSourceDescriptor?,
    sourceConfigContent: AppSourceConfigContent?,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    sourceSearchState: SourcesSearchUiState,
    bottomContentPadding: Dp,
    onSourceSelected: (String) -> Unit,
    onEditSourceConfig: (AppSourceDescriptor) -> Unit,
    onSourceConfigSaved: (AppSourceDescriptor) -> Unit,
    onSourceConfigCancel: () -> Unit,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
    onSearchRetryForSource: (String) -> Unit,
    onAnimeClick: (org.akkirrai.hibiki.catalog.model.Anime) -> Unit,
    currentRoute: AppRoute,
    externalSourcesState: ExternalSourceRepositoryUiState?,
    externalSourcesController: ExternalSourceRepositoryController?,
    selectedSourcesTab: Int,
    onSelectedSourcesTabChange: (Int) -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenPackageInfo: (String, String) -> Unit,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    readClipboardText: () -> String?,
    copyText: (String) -> Unit,
) {
    var isAddRepositoryDialogOpen by remember { mutableStateOf(false) }
    val selectSource: (String) -> Unit = onSourceSelected

    when (currentRoute) {
        AppRoute.SourceRepositories -> AppSourceRepositoriesScreen(
            state = externalSourcesState ?: ExternalSourceRepositoryUiState(),
            bottomContentPadding = bottomContentPadding,
            onBack = onBack,
            onRepositoryClick = {},
            onRemoveRepository = externalSourcesController?.let { it::removeRepository } ?: {},
            onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
            onCopyUrl = copyText,
            onOpenUrl = onOpenUrl,
            onAddRepository = { isAddRepositoryDialogOpen = true },
            customRepositoriesSupported = false,
            modifier = Modifier.fillMaxSize(),
        )
        is AppRoute.SourcePackageInfo -> {
            val packageStatus = externalSourcesState?.repositoryContents
                ?.firstOrNull { it.endpoint.url == currentRoute.repositoryUrl }
                ?.packages
                ?.firstOrNull { it.sourceId.value == currentRoute.sourceId }
            AppExternalSourcePackageInfoScreen(
                packageStatus = packageStatus,
                isBusy = externalSourcesState?.isBusy == true,
                bottomContentPadding = bottomContentPadding,
                onBack = onBack,
                onUninstall = externalSourcesController?.let { controller ->
                    {
                        // Uninstalling hands off to the system confirmation dialog and
                        // completes asynchronously -- don't navigate away or reassign the
                        // selected source yet, the user hasn't actually confirmed anything.
                        // The package-change broadcast receiver refreshes this screen's state
                        // once the OS reports the uninstall really happened.
                        controller.uninstallPackage(
                            sourceId = org.akkirrai.beakokit.api.SourceId(currentRoute.sourceId),
                        )
                    }
                } ?: {},
                onUpdate = externalSourcesController?.let { controller ->
                    { controller.installPackage(org.akkirrai.beakokit.api.SourceId(currentRoute.sourceId)) }
                } ?: {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        else -> AppSourcesTabsScreen(
            selectedTab = selectedSourcesTab,
            packages = externalSourcesState?.packages.orEmpty(),
            selectedSourceId = selectedSourceId,
            state = externalSourcesState ?: ExternalSourceRepositoryUiState(),
            isBusy = externalSourcesState?.isBusy == true,
            bottomContentPadding = bottomContentPadding,
            onSelectedTabChange = onSelectedSourcesTabChange,
            onRepositoryClick = {},
            onRemoveRepository = externalSourcesController?.let { it::removeRepository } ?: {},
            onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
            onCopyUrl = copyText,
            onOpenUrl = onOpenUrl,
            onAddRepository = { isAddRepositoryDialogOpen = true },
            onInstall = externalSourcesController?.let { controller ->
                { sourceId -> controller.installPackage(sourceId) }
            } ?: {},
            onSourceSelected = selectSource,
            onManage = { sourceId ->
                externalSourcesState?.repositoryContents
                    ?.firstOrNull { content -> content.packages.any { it.sourceId == sourceId } }
                    ?.endpoint?.url
                    ?.let { repositoryUrl -> onOpenPackageInfo(repositoryUrl, sourceId.value) }
            },
            customRepositoriesSupported = false,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (isAddRepositoryDialogOpen) {
        AppAddSourceRepositoryDialog(
            isBusy = externalSourcesState?.isBusy == true,
            errorMessage = externalSourcesState?.error?.message,
            onDismiss = {
                externalSourcesController?.clearError()
                isAddRepositoryDialogOpen = false
            },
            onAdd = { url ->
                externalSourcesController?.addRepository(url) {
                    isAddRepositoryDialogOpen = false
                }
            },
            onPaste = readClipboardText,
        )
    }
}
