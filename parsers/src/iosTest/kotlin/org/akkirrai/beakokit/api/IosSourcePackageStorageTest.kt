package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import platform.Foundation.NSUserDefaults

class IosSourcePackageStorageTest {
    @Test
    fun stagingPathInsideApplicationContainerIsAcceptedAndPersisted() {
        val storage = IosSourcePackageStorage()
        val sourceId = SourceId("ios-smoke-source")
        val packagePath = storage.newStagingPath(sourceId)
        val defaults = NSUserDefaults(suiteName = "beakokit.ios.package-smoke")
        val store = IosSourcePackageActivationStore(
            defaults = defaults,
            packagePathValidator = storage::requireManagedPackagePath,
        )
        val installed = InstalledSourcePackage(sourceId, "1.0.0", packagePath)

        store.persistAtomically(sourceId, SourcePackageActivationState(active = installed))

        assertEquals(installed, store.load(sourceId).active)
        assertTrue(storage.requireManagedPackagePath(packagePath).contains("ios-smoke-source"))
        defaults.removeObjectForKey("beakokit.source_package.${sourceId.value}")
    }

    @Test
    fun activationStateOutsideManagedRootIsRejected() {
        val storage = IosSourcePackageStorage()
        val sourceId = SourceId("ios-smoke-source")
        val defaults = NSUserDefaults(suiteName = "beakokit.ios.package-smoke-invalid")
        val store = IosSourcePackageActivationStore(
            defaults = defaults,
            packagePathValidator = storage::requireManagedPackagePath,
        )

        assertFailsWith<IllegalArgumentException> {
            store.persistAtomically(
                sourceId,
                SourcePackageActivationState(
                    active = InstalledSourcePackage(sourceId, "1.0.0", "/tmp/not-a-source-package"),
                ),
            )
        }
    }
}
