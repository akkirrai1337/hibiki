package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmSourcePackageSha256Test {
    @Test
    fun `digest returns lowercase sha256 for downloaded bytes`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            JvmSourcePackageSha256.digest("hello".encodeToByteArray()),
        )
    }
}
