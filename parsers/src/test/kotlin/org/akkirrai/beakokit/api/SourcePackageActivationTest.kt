package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourcePackageActivationTest {
    @Test
    fun `failed initialization keeps the currently active package`() {
        val old = packageVersion("1.0.0")
        val controller = SourcePackageActivationController(
            SourcePackageActivationState(active = old),
        )

        val result = controller.activate(packageVersion("2.0.0"), initializationSucceeded = false)

        assertEquals(SourcePackageActivationState(active = old), result)
    }

    @Test
    fun `successful activation preserves the old package for rollback`() {
        val old = packageVersion("1.0.0")
        val next = packageVersion("2.0.0")
        val controller = SourcePackageActivationController(
            SourcePackageActivationState(active = old),
        )

        controller.activate(next, initializationSucceeded = true)

        assertEquals(SourcePackageActivationState(active = next, previous = old), controller.state)
        assertEquals(SourcePackageActivationState(active = old), controller.rollback())
    }

    @Test
    fun `rollback without a previous package is rejected`() {
        val controller = SourcePackageActivationController()

        assertFailsWith<SourcePackageRollbackException> { controller.rollback() }
    }

    private fun packageVersion(version: String) = InstalledSourcePackage(
        sourceId = SourceId("external-source"),
        packageVersion = version,
        packagePath = "packages/external-source/$version",
    )
}
