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

    @Suppress("DEPRECATION")
    @Test
    fun `legacy capability names resolve to catalog capabilities`() {
        val beakoLegacy: MetadataSourceCapabilities = CatalogCapabilities.FULL
        val resolverLegacy: org.akkirrai.animeresolver.model.MetadataSourceCapabilities = beakoLegacy
        val feature: MetadataSourceFeature = CatalogFeature.LATEST_RELEASES

        assertEquals(CatalogCapabilities.FULL, resolverLegacy)
        assertEquals(CatalogFeature.LATEST_RELEASES, feature)
    }
}
