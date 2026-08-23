package org.akkirrai.hibiki.app.destination.source

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.core.source.AppAddSourceRepositoryDialog
import org.akkirrai.hibiki.core.source.AppSourceConfigContent
import org.akkirrai.hibiki.core.source.SourcePackageInfoScreen
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.core.source.SourceRepositoriesScreen
import org.akkirrai.hibiki.core.source.SourceRepositoriesActions
import org.akkirrai.hibiki.core.source.SourcesTabsScreen
import org.akkirrai.hibiki.core.source.SourcesTabsActions
import org.akkirrai.hibiki.core.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.core.source.ExternalSourceRepositoryUiState

internal data class AppDestinationSourceState(
    val sources: List<AppSourceDescriptor>,
    val selectedSourceId: String?,
)

internal data class AppDestinationExternalSourcesState(
    val controller: ExternalSourceRepositoryController?,
    val selectedTab: Int = 0,
    val onSelectedTabChange: (Int) -> Unit = {},
)

internal data class SourcesRouteState(
    val sources: List<AppSourceDescriptor>,
    val selectedSourceId: String?,
    val currentRoute: AppRoute,
    val selectedSourcesTab: Int,
)

internal data class SourcesRouteActions(
    val onSourceSelected: (String) -> Unit,
    val onSelectedSourcesTabChange: (Int) -> Unit,
    val onOpenPackageInfo: (String, String) -> Unit,
    val onBack: () -> Unit,
)

@Composable
internal fun SourcesRoute(
    state: SourcesRouteState,
    actions: SourcesRouteActions,
    externalSourcesController: ExternalSourceRepositoryController?,
    sourceConfigContent: AppSourceConfigContent?,
    bottomContentPadding: Dp,
) {
    var isAddRepositoryDialogOpen by remember { mutableStateOf(false) }
    var editingSourceConfig by remember { mutableStateOf<AppSourceDescriptor?>(null) }
    val selectSource: (String) -> Unit = actions.onSourceSelected
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val copyText: (String) -> Unit = { text -> clipboardManager.setText(AnnotatedString(text)) }
    val readClipboardText: () -> String? = { clipboardManager.getText()?.text }
    val externalSourcesState = externalSourcesController?.state?.collectAsState()?.value

    when (val currentRoute = state.currentRoute) {
        AppRoute.SourceRepositories -> SourceRepositoriesScreen(
            state = externalSourcesState ?: ExternalSourceRepositoryUiState(),
            actions = SourceRepositoriesActions(
                onBack = actions.onBack,
                onRepositoryClick = {},
                onRemoveRepository = externalSourcesController?.let { it::removeRepository } ?: {},
                onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
                onCopyUrl = copyText,
                onOpenUrl = { url -> uriHandler.openUri(url) },
                onAddRepository = { isAddRepositoryDialogOpen = true },
            ),
            bottomContentPadding = bottomContentPadding,
            customRepositoriesSupported = false,
            modifier = Modifier.fillMaxSize(),
        )
        is AppRoute.SourcePackageInfo -> {
            val configuring = editingSourceConfig
            if (configuring != null && sourceConfigContent != null) {
                sourceConfigContent(
                    configuring,
                    { editingSourceConfig = null },
                    { editingSourceConfig = null },
                )
            } else {
                val packageStatus = externalSourcesState?.repositoryContents
                    ?.firstOrNull { it.endpoint.url == currentRoute.repositoryUrl }
                    ?.packages
                    ?.firstOrNull { it.sourceId.value == currentRoute.sourceId }
                val configurableSource = sourceConfigContent?.let {
                    state.sources
                        .firstOrNull { it.id == currentRoute.sourceId }
                        ?.takeIf { it.configSchema.fields.isNotEmpty() }
                }
                SourcePackageInfoScreen(
                    packageStatus = packageStatus,
                    isBusy = externalSourcesState?.isBusy == true,
                    bottomContentPadding = bottomContentPadding,
                    onBack = actions.onBack,
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
                    onConfigure = configurableSource?.let { source -> { editingSourceConfig = source } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        else -> SourcesTabsScreen(
            selectedTab = state.selectedSourcesTab,
            selectedSourceId = state.selectedSourceId,
            state = externalSourcesState ?: ExternalSourceRepositoryUiState(),
            actions = SourcesTabsActions(
                onSelectedTabChange = actions.onSelectedSourcesTabChange,
                onRepositoryClick = {},
                onRemoveRepository = externalSourcesController?.let { it::removeRepository } ?: {},
                onRefresh = externalSourcesController?.let { it::refreshRepositories } ?: {},
                onCopyUrl = copyText,
                onOpenUrl = { url -> uriHandler.openUri(url) },
                onAddRepository = { isAddRepositoryDialogOpen = true },
                onInstall = externalSourcesController?.let { controller ->
                    { sourceId -> controller.installPackage(sourceId) }
                } ?: {},
                onSourceSelected = selectSource,
                onManage = { sourceId ->
                    externalSourcesState?.repositoryContents
                        ?.firstOrNull { content -> content.packages.any { it.sourceId == sourceId } }
                        ?.endpoint?.url
                        ?.let { repositoryUrl -> actions.onOpenPackageInfo(repositoryUrl, sourceId.value) }
                },
            ),
            bottomContentPadding = bottomContentPadding,
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
