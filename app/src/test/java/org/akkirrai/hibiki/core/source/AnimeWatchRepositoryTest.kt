package org.akkirrai.hibiki.core.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.akkirrai.animeresolver.model.PlayerLink
import org.akkirrai.animeresolver.model.PlayerType
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeWatchRepositoryTest {
    private val repository = AnimeWatchRepository(
        client = HttpClient(
            MockEngine { error("No network expected in AnimeWatchRepositoryTest") }
        )
    )

    @Test
    fun `automatic order prefers kodik before slower embeds`() {
        val sorted = repository.prioritizeLinks(
            links = listOf(
                playerLink(playerName = "Aksor"),
                playerLink(playerName = "Kodik"),
            ),
            preferredPlayerName = null,
            preferredQuality = null,
        )

        assertEquals(listOf("Kodik", "Aksor"), sorted.map { it.playerName })
    }

    @Test
    fun `selected player keeps priority over automatic order`() {
        val sorted = repository.prioritizeLinks(
            links = listOf(
                playerLink(playerName = "Aksor"),
                playerLink(playerName = "Kodik"),
            ),
            preferredPlayerName = "Aksor",
            preferredQuality = null,
        )

        assertEquals(listOf("Aksor", "Kodik"), sorted.map { it.playerName })
    }

    @Test
    fun `preferred player gets longer timeout`() {
        assertEquals(8_000L, repository.resolveAttemptTimeoutMillis(null, "Kodik"))
        assertEquals(8_000L, repository.resolveAttemptTimeoutMillis("Kodik", "Aksor"))
        assertEquals(12_000L, repository.resolveAttemptTimeoutMillis("Kodik", "Kodik"))
    }

    private fun playerLink(playerName: String) = PlayerLink(
        url = "https://example.test/$playerName",
        type = PlayerType.EMBED,
        quality = "720p",
        headers = emptyMap(),
        playerName = playerName,
        translation = "AniLibria",
    )
}
