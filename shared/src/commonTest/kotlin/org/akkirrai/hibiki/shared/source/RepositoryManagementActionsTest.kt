package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourcePackageStateException
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryLoader
import org.akkirrai.beakokit.api.SourceRepositoryStore
import org.akkirrai.beakokit.api.SourceRepositoryTransport

class RepositoryManagementActionsTest {
    @Test
    fun repositoryOnlyAdapterDoesNotExposePackageOperations() = runTest {
        val actions = RepositoryManagementActions(emptyCoordinator())

        assertEquals(emptyList(), actions.packageStatusesForUi())
        assertFailsWith<SourcePackageStateException> {
            actions.installAvailablePackageFromUi(SourceId("external-source")) {}
        }
        assertFailsWith<SourcePackageStateException> {
            actions.rollbackPackageFromUi(SourceId("external-source"))
        }
    }

    private fun emptyCoordinator() = ExternalSourceRepositoryCoordinator(
        SourceRepositoryCatalogLoader(
            catalog = SourceRepositoryCatalog(object : SourceRepositoryStore {
                override fun load() = emptyList<org.akkirrai.beakokit.api.SourceRepositoryEndpoint>()

                override fun persistAtomically(
                    repositories: List<org.akkirrai.beakokit.api.SourceRepositoryEndpoint>,
                ) = Unit
            }),
            loader = SourceRepositoryLoader(SourceRepositoryTransport { _, _ ->
                error("Repository loading is not used by this test")
            }),
        ),
    )
}
