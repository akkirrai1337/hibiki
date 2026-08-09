package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.InstalledSourcePackage
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceRuntime

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
        assertEquals(emptyList(), actions.installed)

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
    fun controllerResolvesGitHubFileLinksBeforeAddingRepository() = runTest {
        val actions = FakeActions()
        val controller = ExternalSourceRepositoryController(actions, this)
        advanceUntilIdle()

        controller.addRepository("https://github.com/vadim/hibiki-sources/blob/main/index.json")
        advanceUntilIdle()

        assertEquals(
            listOf("https://raw.githubusercontent.com/vadim/hibiki-sources/main/index.json"),
            controller.state.value.repositories.map { it.url },
        )
    }

    @Test
    fun controllerKeepsPersistedRepositoryVisibleWhenRefreshFails() = runTest {
        val actions = FakeActions(refreshFailures = 2)
        val controller = ExternalSourceRepositoryController(actions, this)
        advanceUntilIdle()

        controller.addRepository("https://example.test/index.json")
        advanceUntilIdle()

        assertEquals(
            listOf("https://example.test/index.json"),
            controller.state.value.repositories.map { it.url },
        )
        assertNotNull(controller.state.value.error)
        assertEquals(false, controller.state.value.isBusy)
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

    @Test
    fun controllerRefreshesUpdateStateAfterPackageInstallation() = runTest {
        val actions = FakeActions(withUpdate = true)
        val controller = ExternalSourceRepositoryController(actions, this)
        advanceUntilIdle()

        assertEquals(true, controller.state.value.packages.single().updateAvailable)
        controller.installPackage(SourceId("external-source"))
        advanceUntilIdle()

        assertEquals(false, controller.state.value.packages.single().updateAvailable)
        assertEquals(true, controller.state.value.packages.single().rollbackAvailable)
    }

    private class FakeActions(
        private var refreshFailures: Int = 0,
        private val withUpdate: Boolean = false,
    ) : ExternalSourceRepositoryActions {
        private val items = mutableListOf<SourceRepositoryEndpoint>()
        val installed = mutableListOf<SourceId>()
        val rolledBack = mutableListOf<SourceId>()
        private var packageInstalled = false

        override suspend fun repositories(): List<SourceRepositoryEndpoint> = items.toList()

        override suspend fun addRepositoryFromUi(endpoint: SourceRepositoryEndpoint) {
            if (items.none { it.url == endpoint.url }) items += endpoint
            failRefreshIfRequested()
        }

        override suspend fun removeRepositoryFromUi(url: String) {
            items.removeAll { it.url == url }
        }

        override suspend fun refreshRepositories() {
            failRefreshIfRequested()
        }

        private fun failRefreshIfRequested() {
            if (refreshFailures > 0) {
                refreshFailures--
                error("Repository refresh failed")
            }
        }

        override suspend fun packageStatusesForUi(): List<ExternalSourcePackageStatus> =
            if (!withUpdate) {
                emptyList()
            } else {
                val available = manifest()
                val activeManifest = if (packageInstalled) available else available.copy(
                    packageVersion = "1.0.0",
                    sha256 = "b".repeat(64),
                )
                listOf(
                    ExternalSourcePackageStatus(
                        sourceId = available.sourceId,
                        availableManifest = available,
                        activePackage = ActiveExternalSourcePackage(
                            manifest = activeManifest,
                            installed = InstalledSourcePackage(
                                sourceId = available.sourceId,
                                packageVersion = activeManifest.packageVersion,
                                packagePath = "package/${activeManifest.packageVersion}",
                            ),
                        ),
                        rollbackAvailable = packageInstalled,
                    ),
                )
            }

        override suspend fun repositoryContentsForUi(): List<ExternalSourceRepositoryContent> = items.map { endpoint ->
            ExternalSourceRepositoryContent(
                endpoint = endpoint,
                packages = packageStatusesForUi(),
            )
        }

        override suspend fun installAvailablePackageFromUi(
            sourceId: SourceId,
            onStage: (org.akkirrai.beakokit.api.SourcePackageInstallStage) -> Unit,
            initialize: suspend () -> Unit,
        ) {
            installed += sourceId
            packageInstalled = true
            initialize()
        }

        override suspend fun rollbackPackageFromUi(sourceId: SourceId) {
            rolledBack += sourceId
        }

        override suspend fun uninstallPackageFromUi(sourceId: SourceId) {
            installed.removeAll { it == sourceId }
            packageInstalled = false
        }

        private fun manifest() = SourceManifest(
            manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
            sourceId = SourceId("external-source"),
            packageVersion = "2.0.0",
            sourceInfo = SourceManifestInfo(
                displayName = "External source",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
            ),
            apiVersion = SourceApi.VERSION,
            hostApiVersion = SourceHostApi.VERSION,
            runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
            entrypoint = "source.wasm",
            packageUrl = "https://example.test/source.zip",
            sha256 = "a".repeat(64),
            artifactSizeBytes = 1,
            minClientVersion = 0,
        )
    }
}
