package org.akkirrai.hibiki.core.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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
        assertEquals(15_000L, repository.resolveAttemptTimeoutMillis(null, "Kodik"))
        assertEquals(15_000L, repository.resolveAttemptTimeoutMillis("Kodik", "Aksor"))
        assertEquals(20_000L, repository.resolveAttemptTimeoutMillis("Kodik", "Kodik"))
        assertEquals(35_000L, repository.resolveAttemptTimeoutMillis(null, "Direct", PlayerType.DIRECT_HLS))
        assertEquals(45_000L, repository.resolveAttemptTimeoutMillis("Direct", "Direct", PlayerType.DIRECT_MP4))
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
    fun `concurrent player link requests share one provider load`() = runBlocking {
        val loaderStarted = CompletableDeferred<Unit>()
        val releaseLoader = CompletableDeferred<Unit>()
        val loadCount = AtomicInteger()
        val expected = listOf(playerLink(playerName = "Kodik"))

        val first = async {
            repository.loadPlayerLinks("source\u0000episode") {
                loadCount.incrementAndGet()
                loaderStarted.complete(Unit)
                releaseLoader.await()
                expected
            }
        }
        loaderStarted.await()
        val second = async {
            repository.loadPlayerLinks("source\u0000episode") {
                loadCount.incrementAndGet()
                expected
            }
        }

        first.cancel()
        releaseLoader.complete(Unit)

        assertEquals(expected, second.await())
        assertEquals(expected, repository.loadPlayerLinks("source\u0000episode") { emptyList() })
        assertEquals(1, loadCount.get())

        repository.loadPlayerLinks("source\u0000episode", forceRefresh = true) {
            loadCount.incrementAndGet()
            expected
        }
        assertEquals(2, loadCount.get())
    }

    @Test
    fun `watch source id keeps the complete scoped title id`() {
        assertEquals(
            "source:animego:mob-psiho-100-3-2131",
            watchTitleIdFromSourceId(
                "source:animego:mob-psiho-100-3-2131|watch|animego-0",
            ),
        )
        assertEquals("42", watchTitleIdFromSourceId("42:legacy-voiceover"))
    }

    @Test
    fun `clearCaches removes all repository cache state`() {
        repository.cachedSources()["title"] = Any()
        repository.sourcePayloads()["source"] = Any()
        repository.cachedStreams()["stream"] = Any()
        repository.cachedPlayerLinks()["links"] = Any()
        repository.inFlightLoads()["load"] = Any()
        repository.inFlightPlayerLinks()["links"] = Any()

        repository.clearCaches()

        assertTrue(repository.cachedSources().isEmpty())
        assertTrue(repository.sourcePayloads().isEmpty())
        assertTrue(repository.cachedStreams().isEmpty())
        assertTrue(repository.cachedPlayerLinks().isEmpty())
        assertTrue(repository.inFlightLoads().isEmpty())
        assertTrue(repository.inFlightPlayerLinks().isEmpty())
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
    private fun AnimeWatchRepository.cachedPlayerLinks(): MutableMap<String, Any> {
        val field = AnimeWatchRepository::class.java.getDeclaredField("cachedPlayerLinks")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeWatchRepository.inFlightLoads(): MutableMap<String, Any> {
        val field = AnimeWatchRepository::class.java.getDeclaredField("inFlightLoads")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeWatchRepository.inFlightPlayerLinks(): MutableMap<String, Any> {
        val field = AnimeWatchRepository::class.java.getDeclaredField("inFlightPlayerLinks")
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
