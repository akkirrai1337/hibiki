package org.akkirrai.beakokit.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyModelCompatibilityTest {
    @Test
    fun `legacy model namespace resolves to BeakoKit models`() {
        val legacyStatus: org.akkirrai.animeresolver.model.AnimeReleaseStatus =
            AnimeReleaseStatus.ONGOING

        assertEquals(AnimeReleaseStatus.ONGOING, legacyStatus)
        assertEquals(AnimeTitle::class, org.akkirrai.animeresolver.model.AnimeTitle::class)
        assertEquals(PlayerLink::class, org.akkirrai.animeresolver.model.PlayerLink::class)
    }
}
