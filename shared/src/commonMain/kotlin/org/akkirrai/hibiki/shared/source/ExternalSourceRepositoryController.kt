package org.akkirrai.hibiki.shared.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint

data class ExternalSourceRepositoryUiState(
    val repositories: List<SourceRepositoryEndpoint> = emptyList(),
    val isBusy: Boolean = false,
    val error: Throwable? = null,
)

/** Coordinates repository settings without exposing runtime synchronization to the UI. */
class ExternalSourceRepositoryController(
    private val actions: ExternalSourceRepositoryActions,
    private val scope: CoroutineScope,
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
            actions.repositories()
        }
    }

    fun addRepository(url: String) {
        launchOperation {
            actions.addRepositoryFromUi(SourceRepositoryEndpoint(url.trim()))
            actions.repositories()
        }
    }

    fun removeRepository(url: String) {
        launchOperation {
            actions.removeRepositoryFromUi(url)
            actions.repositories()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun close() {
        operation?.cancel()
    }

    private fun launchOperation(operationBlock: suspend () -> List<SourceRepositoryEndpoint>) {
        operation?.cancel()
        operation = scope.launch {
            _state.value = _state.value.copy(isBusy = true, error = null)
            try {
                val repositories = operationBlock()
                _state.value = ExternalSourceRepositoryUiState(repositories = repositories)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.value = _state.value.copy(isBusy = false, error = error)
            }
        }
    }
}
