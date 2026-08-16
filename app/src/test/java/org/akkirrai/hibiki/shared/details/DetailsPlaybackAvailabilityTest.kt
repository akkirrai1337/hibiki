package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

class DetailsPlaybackAvailabilityTest {
    private val playableSource = AppSourceDescriptor(
        id = "playable",
        name = "Playable",
        language = "en",
        supportsPlayback = true,
    )

    @Test
    fun selectedPlayableSourceAllowsNonAnnouncementTitle() {
        assertTrue(
            resolveDetailsPlaybackAvailability(
                watchRepositoryAvailable = true,
                sources = listOf(playableSource),
                selectedSourceId = "playable",
                status = "ongoing",
                episodesLabel = "12",
            ),
        )
    }

    @Test
    fun unavailableSourceOrAnnouncementDisablesWatch() {
        val nonPlayableSource = playableSource.copy(
            id = "catalog-only",
            supportsPlayback = false,
        )
        assertFalse(
            resolveDetailsPlaybackAvailability(
                watchRepositoryAvailable = true,
                sources = listOf(playableSource, nonPlayableSource),
                selectedSourceId = "catalog-only",
                status = "ongoing",
                episodesLabel = "12",
            ),
        )
        assertFalse(
            resolveDetailsPlaybackAvailability(
                watchRepositoryAvailable = true,
                sources = listOf(playableSource),
                selectedSourceId = "playable",
                status = "announcement",
                episodesLabel = "announcement",
            ),
        )
    }

    @Test
    fun missingRepositoryOrSourceDisablesWatch() {
        assertFalse(
            resolveDetailsPlaybackAvailability(
                watchRepositoryAvailable = false,
                sources = listOf(playableSource),
                selectedSourceId = "playable",
                status = "ongoing",
                episodesLabel = "12",
            ),
        )
        assertFalse(
            resolveDetailsPlaybackAvailability(
                watchRepositoryAvailable = true,
                sources = listOf(playableSource),
                selectedSourceId = null,
                status = "ongoing",
                episodesLabel = "12",
            ),
        )
    }
}
