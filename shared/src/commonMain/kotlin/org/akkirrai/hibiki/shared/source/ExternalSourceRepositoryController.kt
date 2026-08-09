package org.akkirrai.hibiki.shared.source

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRepositoryUrlResolver
import org.akkirrai.beakokit.api.SourcePackageInstallStage

data class ExternalSourceRepositoryUiState(
    val repositories: List<SourceRepositoryEndpoint> = emptyList(),
    val repositoryContents: List<ExternalSourceRepositoryContent> = emptyList(),
    val packages: List<ExternalSourcePackageStatus> = emptyList(),
    val isBusy: Boolean = false,
    val installationStage: SourcePackageInstallStage? = null,
    val error: Throwable? = null,
)

/** Coordinates repository settings without exposing runtime synchronization to the UI. */
class ExternalSourceRepositoryController(
    private val actions: ExternalSourceRepositoryActions,
    private val scope: CoroutineScope,
    private val urlResolver: SourceRepositoryUrlResolver = SourceRepositoryUrlResolver(),
    private val operationContext: CoroutineContext = EmptyCoroutineContext,
) {
    private val _state = MutableStateFlow(ExternalSourceRepositoryUiState())
    val state: StateFlow<ExternalSourceRepositoryUiState> = _state.asStateFlow()

    private var operation: Job? = null

    init {
        refreshRepositories()
    }

    fun refreshRepositories() {
        launchOperation {
            actions.refreshRepositories()
            loadState()
        }
    }

    fun refreshRepository(url: String) {
        launchOperation {
            actions.refreshRepository(url)
            loadState()
        }
    }

    fun addRepository(
        url: String,
        onRepositoryAvailable: (String) -> Unit = {},
    ) {
        launchOperation {
            val endpoint = urlResolver.resolve(url)
            try {
                actions.addRepositoryFromUi(endpoint)
            } catch (error: Throwable) {
                if (actions.repositories().any { it.url == endpoint.url }) {
                    onRepositoryAvailable(endpoint.url)
                }
                throw error
            }
            onRepositoryAvailable(endpoint.url)
            loadState()
        }
    }

    fun removeRepository(url: String) {
        launchOperation {
            actions.removeRepositoryFromUi(url)
            loadState()
        }
    }

    fun installPackage(sourceId: SourceId, initialize: suspend () -> Unit = {}) {
        launchOperation {
            actions.installAvailablePackageFromUi(
                sourceId = sourceId,
                initialize = initialize,
                onStage = { stage ->
                _state.value = _state.value.copy(installationStage = stage)
                },
            )
            loadState()
        }
    }

    fun rollbackPackage(sourceId: SourceId) {
        launchOperation {
            actions.rollbackPackageFromUi(sourceId)
            loadState()
        }
    }

    fun uninstallPackage(sourceId: SourceId, onUninstalled: () -> Unit = {}) {
        launchOperation {
            actions.uninstallPackageFromUi(sourceId)
            loadState().also { onUninstalled() }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun close() {
        operation?.cancel()
    }

    private fun launchOperation(operationBlock: suspend () -> ExternalSourceRepositoryUiState) {
        operation?.cancel()
        operation = scope.launch(operationContext) {
            _state.value = _state.value.copy(isBusy = true, installationStage = null, error = null)
            try {
                _state.value = operationBlock()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val recoveredState = try {
                    loadState()
                } catch (recoveryError: CancellationException) {
                    throw recoveryError
                } catch (_: Throwable) {
                    null
                }
                _state.value = (recoveredState ?: _state.value).copy(
                    isBusy = false,
                    installationStage = null,
                    error = error,
                )
            }
        }
    }

    private suspend fun loadState(): ExternalSourceRepositoryUiState =
        ExternalSourceRepositoryUiState(
            repositories = actions.repositories(),
            repositoryContents = actions.repositoryContentsForUi(),
            packages = actions.packageStatusesForUi(),
        )
}
