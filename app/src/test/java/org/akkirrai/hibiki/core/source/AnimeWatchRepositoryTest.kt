package org.akkirrai.hibiki.core.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeWatchRepositoryTest {
    private val repository = AnimeWatchRepository(
        client = HttpClient(
            MockEngine { error("No network expected in AnimeWatchRepositoryTest") }
        )
    )

    @Test
    fun `failed player does not cancel another player in the race`() = runBlocking {
        val (player, stream) = raceFirstSuccessful(listOf("unavailable", "working")) { candidate ->
            if (candidate == "unavailable") error("Player is unavailable")
            delay(10)
            "https://video.example/stream.m3u8"
        }

        assertEquals("working", player)
        assertEquals("https://video.example/stream.m3u8", stream)
    }

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

    @Test
    fun `preferred quality is sorted before higher automatic quality`() {
        val sorted = repository.prioritizeLinks(
            links = listOf(
                playerLink(playerName = "Kodik", quality = "1080p"),
                playerLink(playerName = "Kodik", quality = "720p"),
            ),
            preferredPlayerName = null,
            preferredQuality = "720p",
        )

        assertEquals(listOf("720p", "1080p"), sorted.map { it.quality })
    }

    @Test
    fun `clearCaches removes all repository cache state`() {
        repository.cachedSources()["title"] = Any()
        repository.sourcePayloads()["source"] = Any()
        repository.cachedStreams()["stream"] = Any()
        repository.inFlightLoads()["load"] = Any()

        repository.clearCaches()

        assertTrue(repository.cachedSources().isEmpty())
        assertTrue(repository.sourcePayloads().isEmpty())
        assertTrue(repository.cachedStreams().isEmpty())
        assertTrue(repository.inFlightLoads().isEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeWatchRepository.cachedSources(): MutableMap<String, Any> {
        val field = AnimeWatchRepository::class.java.getDeclaredField("cachedSources")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeWatchRepository.sourcePayloads(): MutableMap<String, Any> {
        val field = AnimeWatchRepository::class.java.getDeclaredField("sourcePayloads")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeWatchRepository.cachedStreams(): MutableMap<String, Any> {
        val field = AnimeWatchRepository::class.java.getDeclaredField("cachedStreams")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeWatchRepository.inFlightLoads(): MutableMap<String, Any> {
        val field = AnimeWatchRepository::class.java.getDeclaredField("inFlightLoads")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }

    private fun playerLink(
        playerName: String,
        quality: String = "720p",
    ) = PlayerLink(
        url = "https://example.test/$playerName",
        type = PlayerType.EMBED,
        quality = quality,
        headers = emptyMap(),
        playerName = playerName,
        translation = "AniLibria",
    )
}
