package org.akkirrai.beakokit.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmSourcePackageArtifactReaderTest {
    @Test
    fun `reader calculates archive size and sha256`() {
        val archive = Files.createTempFile("hibiki-source-artifact-", ".zip")
        try {
            Files.writeString(archive, "hello")

            assertEquals(
                SourcePackageArtifact(
                    sizeBytes = 5,
                    sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                ),
                JvmSourcePackageArtifactReader().read(archive),
            )
        } finally {
            Files.deleteIfExists(archive)
        }
    }
}
