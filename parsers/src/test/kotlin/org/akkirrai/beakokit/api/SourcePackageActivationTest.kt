package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourcePackageActivationTest {
    @Test
    fun `activation state rejects packages from different sources`() {
        assertFailsWith<IllegalArgumentException> {
            SourcePackageActivationState(
                active = InstalledSourcePackage(SourceId("source-a"), "1.0.0", "active"),
                previous = InstalledSourcePackage(SourceId("source-b"), "1.0.0", "previous"),
            )
        }
    }

    @Test
    fun `activation state rejects duplicate active and previous packages`() {
        val packageVersion = InstalledSourcePackage(SourceId("source"), "1.0.0", "active")

        assertFailsWith<IllegalArgumentException> {
            SourcePackageActivationState(active = packageVersion, previous = packageVersion)
        }
    }

    @Test
    fun `installed package rejects blank version and path`() {
        assertFailsWith<IllegalArgumentException> {
            InstalledSourcePackage(SourceId("source"), "", "active")
        }
        assertFailsWith<IllegalArgumentException> {
            InstalledSourcePackage(SourceId("source"), "1.0.0", "")
        }
        assertFailsWith<IllegalArgumentException> {
            InstalledSourcePackage(SourceId("source"), "1.0\n0", "active")
        }
        assertFailsWith<IllegalArgumentException> {
            InstalledSourcePackage(SourceId("source"), "1.0.0", "active\rpath")
        }
    }

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
    fun `reinstalling the active package does not create a fake rollback version`() {
        val active = InstalledSourcePackage(SourceId("source"), "1.0.0", "active")
        val controller = SourcePackageActivationController(SourcePackageActivationState(active = active))

        assertEquals(
            SourcePackageActivationState(active = active),
            controller.activate(active, initializationSucceeded = true),
        )
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
