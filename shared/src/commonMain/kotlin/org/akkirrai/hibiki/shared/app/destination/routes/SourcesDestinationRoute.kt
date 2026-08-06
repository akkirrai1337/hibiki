package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.source.AppSourceConfigContent
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.SourcesDestinationContent
import org.akkirrai.hibiki.shared.source.SourcesSearchUiState
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryUiState
import org.akkirrai.hibiki.shared.source.AppSourceRepositoriesScreen
import org.akkirrai.hibiki.shared.source.AppSourceRepositoryPackagesScreen
import org.akkirrai.hibiki.shared.source.AppAddSourceRepositoryDialog
import org.akkirrai.hibiki.shared.source.AppSourceExtensionsTabScreen
import org.akkirrai.hibiki.shared.source.AppExternalSourcesTabScreen
import org.akkirrai.hibiki.shared.navigation.AppRoute

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
    onAnimeClick: (org.akkirrai.hibiki.shared.catalog.model.Anime) -> Unit,
    currentRoute: AppRoute,
    externalSourcesState: ExternalSourceRepositoryUiState?,
    externalSourcesController: ExternalSourceRepositoryController?,
    onOpenRepositories: () -> Unit,
    onOpenRepository: (String) -> Unit,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    readClipboardText: () -> String?,
    copyText: (String) -> Unit,
) {
    var isAddRepositoryDialogOpen by remember { mutableStateOf(false) }
    var selectedSourcesTab by rememberSaveable { mutableStateOf(0) }

    when (currentRoute) {
        AppRoute.SourceRepositories -> AppSourceRepositoriesScreen(
            state = externalSourcesState ?: ExternalSourceRepositoryUiState(),
            bottomContentPadding = bottomContentPadding,
            onBack = onBack,
            onRepositoryClick = onOpenRepository,
            onRemoveRepository = externalSourcesController?.let { it::removeRepository } ?: {},
            onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
            onCopyUrl = copyText,
            onOpenUrl = onOpenUrl,
            onAddRepository = { isAddRepositoryDialogOpen = true },
            modifier = Modifier.fillMaxSize(),
        )
        is AppRoute.SourceRepository -> AppSourceRepositoryPackagesScreen(
            repository = externalSourcesState?.repositoryContents
                ?.firstOrNull { it.endpoint.url == currentRoute.url },
            isBusy = externalSourcesState?.isBusy == true,
            bottomContentPadding = bottomContentPadding,
            onBack = onBack,
            onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
            onInstall = externalSourcesController?.let { controller ->
                { sourceId -> controller.installPackage(sourceId) }
            } ?: {},
            onRollback = externalSourcesController?.let { controller ->
                { sourceId -> controller.rollbackPackage(sourceId) }
            } ?: {},
            modifier = Modifier.fillMaxSize(),
        )
        else -> if (selectedSourcesTab == 0) AppSourceExtensionsTabScreen(
            packages = externalSourcesState?.packages.orEmpty(),
            isBusy = externalSourcesState?.isBusy == true,
            bottomContentPadding = bottomContentPadding,
            onSourcesSelected = { selectedSourcesTab = 1 },
            onInstall = externalSourcesController?.let { controller ->
                { sourceId -> controller.installPackage(sourceId) }
            } ?: {},
            onRollback = externalSourcesController?.let { controller ->
                { sourceId -> controller.rollbackPackage(sourceId) }
            } ?: {},
            onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
            modifier = Modifier.fillMaxSize(),
        ) else AppExternalSourcesTabScreen(
        state = externalSourcesState ?: ExternalSourceRepositoryUiState(),
        bottomContentPadding = bottomContentPadding,
        onExtensionsSelected = { selectedSourcesTab = 0 },
        onRepositoryClick = onOpenRepository,
        onRemoveRepository = externalSourcesController?.let { it::removeRepository } ?: {},
        onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
        onCopyUrl = copyText,
        onOpenUrl = onOpenUrl,
        onAddRepository = { isAddRepositoryDialogOpen = true },
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
                externalSourcesController?.addRepository(url) { addedUrl ->
                    isAddRepositoryDialogOpen = false
                    onOpenRepository(addedUrl)
                }
            },
            onPaste = readClipboardText,
        )
    }
}
