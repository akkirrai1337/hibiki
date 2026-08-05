package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.CatalogCapabilities

class SourceOperationGateTest {
    @Test
    fun `core catalog operations are always available`() {
        val info = sourceInfo()

        assertTrue(SourceOperationGate.supports(info, SourceOperation.SEARCH))
        assertTrue(SourceOperationGate.supports(info, SourceOperation.DETAILS))
    }

    @Test
    fun `optional operations follow declared capabilities`() {
        val info = sourceInfo(setOf(SourceCapability.PLAYBACK))

        assertTrue(SourceOperationGate.supports(info, SourceOperation.PLAYBACK_GROUPS))
        assertFailsWith<SourceException> {
            SourceOperationGate.requireSupported(FakeSource(info), SourceOperation.LATEST)
        }.also { exception ->
            assertEquals(SourceErrorCode.UNSUPPORTED_OPERATION, exception.code)
        }
    }

    @Test
    fun `real source gate requires both capability and optional interface`() {
        val source = FakeSource(sourceInfo(setOf(SourceCapability.LATEST_RELEASES)))

        assertEquals(false, SourceOperationGate.supports(source, SourceOperation.LATEST))
        assertFailsWith<SourceException> {
            SourceOperationGate.requireSupported(source, SourceOperation.LATEST)
        }
    }

    @Test
    fun `real source gate rejects playback capability without playback interface`() {
        val source = FakeSource(sourceInfo(setOf(SourceCapability.PLAYBACK)))

        assertEquals(false, SourceOperationGate.supports(source, SourceOperation.PLAYBACK_GROUPS))
        assertFailsWith<SourceException> {
            SourceOperationGate.requireSupported(source, SourceOperation.PLAYER_LINKS)
        }
    }

    @Test
    fun `unsupported operation has stable error code`() {
        val exception = assertFailsWith<SourceException> {
            SourceOperationGate.requireSupported(FakeSource(sourceInfo()), SourceOperation.SCHEDULE)
        }

        assertEquals(SourceErrorCode.UNSUPPORTED_OPERATION, exception.code)
    }

    private fun sourceInfo(capabilities: Set<SourceCapability> = emptySet()) = SourceInfo(
        id = SourceId("test-source"),
        name = "Test",
        languages = setOf(SourceLanguage.ENGLISH),
        primaryLanguage = SourceLanguage.ENGLISH,
        capabilities = capabilities,
    )

    @Test
    fun `health check is available only to health check sources`() {
        assertTrue(SourceOperationGate.supports(HealthyFakeSource(sourceInfo()), SourceOperation.HEALTH_CHECK))
        assertEquals(false, SourceOperationGate.supports(FakeSource(sourceInfo()), SourceOperation.HEALTH_CHECK))
    }

    private open class FakeSource(
        override val info: SourceInfo,
    ) : AnimeSource {
        override val catalogCapabilities = CatalogCapabilities(
            supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
            supportedFilters = emptySet(),
        )

        override suspend fun search(query: String): List<AnimeTitle> = emptyList()

        override suspend fun getById(id: String): AnimeTitle = error("Not used")
    }

    private class HealthyFakeSource(info: SourceInfo) : FakeSource(info), HealthCheckSource {
        override suspend fun checkHealth() = Unit
    }
}
