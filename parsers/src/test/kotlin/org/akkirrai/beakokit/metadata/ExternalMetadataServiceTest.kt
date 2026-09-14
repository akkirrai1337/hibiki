package org.akkirrai.beakokit.metadata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.AnimeTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ExternalMetadataServiceTest {
    @Test
    fun `opening a fresh cached catalog entry makes no provider request`() = runBlocking {
        val media = ExternalMetadata(
            provider = MetadataProviderId.ANILIST,
            externalId = 154587,
            anilistId = 154587,
            romajiName = "Sousou no Frieren",
            status = "released",
        )
        val store = InMemoryStore().apply {
            writeMedia(media, cachedAtMillis = 1_000L)
        }
        val client = HttpClient(MockEngine { error("Fresh cached entry must not use the network") })
        try {
            val service = ExternalMetadataService(client, store, nowMillis = { 2_000L })

            val result = service.entryFor(MetadataReference(MetadataProviderId.ANILIST, externalId = 154587))

            assertSame(media, result)
        } finally {
            client.close()
        }
    }

    @Test
    fun `concurrent cache misses for one entry share one provider request`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            gate.await()
            respond(
                """{"data":{"id":"1","attributes":{"canonicalTitle":"Frieren","status":"finished"}}}""",
                HttpStatusCode.OK,
            )
        })
        try {
            val service = ExternalMetadataService(client, InMemoryStore())
            val reference = MetadataReference(MetadataProviderId.KITSU, externalId = 1)

            val results = List(2) {
                async(start = CoroutineStart.UNDISPATCHED) { service.entryFor(reference) }
            }
            gate.complete(Unit)

            assertEquals(listOf(1, 1), results.awaitAll().map { it?.externalId })
            assertEquals(1, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `batch cache lookup falls through a binding whose media was evicted`() {
        val media = ExternalMetadata(
            provider = MetadataProviderId.MAL,
            externalId = 52991,
            malId = 52991,
            romajiName = "Sousou no Frieren",
            status = "released",
        )
        val store = InMemoryStore().apply {
            writeMatch(MetadataMatchRecord("anichi:1", MetadataProviderId.ANILIST, 154587, 100, false, 1_000L))
            writeMatch(MetadataMatchRecord("anichi:1", MetadataProviderId.MAL, 52991, 100, false, 1_000L))
            writeMedia(media, 1_000L)
        }
        val client = HttpClient(MockEngine { error("Cache-only lookup must not use the network") })
        try {
            val service = ExternalMetadataService(client, store, nowMillis = { 2_000L })

            assertEquals(
                mapOf("anichi:1" to media),
                service.cachedMetadataForAll(
                    listOf("anichi:1"),
                    listOf(MetadataProviderId.ANILIST, MetadataProviderId.MAL),
                ),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `repeated candidate set skips a third spelling`() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond(
                """{"data":[{"id":"1","attributes":{"canonicalTitle":"Unrelated title","status":"finished"}}]}""",
                HttpStatusCode.OK,
            )
        })
        try {
            val service = ExternalMetadataService(client, InMemoryStore())
            val title = AnimeTitle(
                id = "anichi:1",
                originalName = "Re:ZERO -Starting Life in Another World- Season 3",
            )

            assertEquals(null, service.metadataFor(title, listOf(MetadataProviderId.KITSU)))
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `automatic card match pins its provider for the details page`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                """{"data":[{"id":"1","attributes":{"canonicalTitle":"Frieren","status":"finished"}}]}""",
                HttpStatusCode.OK,
            )
        })
        val store = InMemoryStore()
        try {
            val service = ExternalMetadataService(client, store)

            service.metadataFor(
                AnimeTitle(id = "anichi:frieren", originalName = "Frieren"),
                listOf(MetadataProviderId.KITSU, MetadataProviderId.ANILIST),
            )

            assertEquals(MetadataProviderId.KITSU, store.readDisplayProvider("anichi:frieren"))
        } finally {
            client.close()
        }
    }

    private class InMemoryStore : ExternalMetadataStore {
        private val media = mutableMapOf<Pair<MetadataProviderId, Int>, CachedMetadata>()
        private val matches = mutableMapOf<Pair<String, MetadataProviderId>, MetadataMatchRecord>()
        private val displayProviders = mutableMapOf<String, MetadataProviderId>()

        override fun readMatch(titleId: String, provider: MetadataProviderId): MetadataMatchRecord? = matches[titleId to provider]
        override fun writeMatch(record: MetadataMatchRecord) {
            matches[record.titleId to record.provider] = record
        }
        override fun readMatches(titleId: String): List<MetadataMatchRecord> =
            matches.filterKeys { it.first == titleId }.values.toList()
        override fun clearMatches(titleId: String) = Unit
        override fun readDisplayProvider(titleId: String): MetadataProviderId? = displayProviders[titleId]
        override fun writeDisplayProvider(titleId: String, provider: MetadataProviderId) {
            displayProviders[titleId] = provider
        }
        override fun matchesForEntry(sourceId: String, provider: MetadataProviderId, externalId: Int): List<MetadataMatchRecord> = emptyList()
        override fun readUnresolvedAt(sourceId: String, provider: MetadataProviderId, externalId: Int): Long? = null
        override fun writeUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int, attemptedAtMillis: Long) = Unit
        override fun clearUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int) = Unit
        override fun readMedia(provider: MetadataProviderId, externalId: Int): CachedMetadata? = media[provider to externalId]
        override fun writeMedia(media: ExternalMetadata, cachedAtMillis: Long) {
            this.media[media.provider to media.externalId] = CachedMetadata(media, cachedAtMillis)
        }
    }
}
