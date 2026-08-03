package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceId

class ExternalSourceRepositoryControllerTest {
    @Test
    fun controllerLoadsAddsAndRemovesRepositories() = runTest {
        val actions = FakeActions()
        val controller = ExternalSourceRepositoryController(actions, this)
        advanceUntilIdle()
        assertEquals(emptyList(), controller.state.value.repositories)

        controller.addRepository(" https://example.test/index.json ")
        advanceUntilIdle()
        assertEquals(listOf("https://example.test/index.json"), controller.state.value.repositories.map { it.url })

        controller.removeRepository("https://example.test/index.json")
        advanceUntilIdle()
        assertEquals(emptyList(), controller.state.value.repositories)
    }

    @Test
    fun controllerExposesValidationErrorsWithoutCrashingScope() = runTest {
        val controller = ExternalSourceRepositoryController(FakeActions(), this)
        advanceUntilIdle()

        controller.addRepository("not-https")
        advanceUntilIdle()

        assertNotNull(controller.state.value.error)
        assertEquals(false, controller.state.value.isBusy)
        controller.clearError()
        assertNull(controller.state.value.error)
    }

    @Test
    fun controllerDelegatesInstallationInitializationAndRollback() = runTest {
        val actions = FakeActions()
        val controller = ExternalSourceRepositoryController(actions, this)
        advanceUntilIdle()
        var initialized = false
        val sourceId = SourceId("external-source")

        controller.installPackage(sourceId) { initialized = true }
        advanceUntilIdle()
        controller.rollbackPackage(sourceId)
        advanceUntilIdle()

        assertEquals(listOf(sourceId), actions.installed)
        assertEquals(listOf(sourceId), actions.rolledBack)
        assertEquals(true, initialized)
    }

    private class FakeActions : ExternalSourceRepositoryActions {
        private val items = mutableListOf<SourceRepositoryEndpoint>()
        val installed = mutableListOf<SourceId>()
        val rolledBack = mutableListOf<SourceId>()

        override suspend fun repositories(): List<SourceRepositoryEndpoint> = items.toList()

        override suspend fun addRepositoryFromUi(endpoint: SourceRepositoryEndpoint) {
            if (items.none { it.url == endpoint.url }) items += endpoint
        }

        override suspend fun removeRepositoryFromUi(url: String) {
            items.removeAll { it.url == url }
        }

        override suspend fun refreshRepositories() = Unit

        override suspend fun packageStatusesForUi(): List<ExternalSourcePackageStatus> = emptyList()

        override suspend fun installAvailablePackageFromUi(
            sourceId: SourceId,
            initialize: suspend () -> Unit,
        ) {
            installed += sourceId
            initialize()
        }

        override suspend fun rollbackPackageFromUi(sourceId: SourceId) {
            rolledBack += sourceId
        }
    }
}
