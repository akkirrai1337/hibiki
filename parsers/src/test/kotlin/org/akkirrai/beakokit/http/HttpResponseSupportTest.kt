package org.akkirrai.beakokit.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpResponseSupportTest {
    @Test
    fun `classifies authentication and rate limit responses`() = runBlocking {
        suspend fun classifiedKind(status: HttpStatusCode): SourceErrorKind {
            val client = HttpClient(MockEngine { respond("error", status) })
            return try {
                assertFailsWith<SourceException> {
                    client.get("https://source.test").bodyOrThrow<String>("TestSource")
                }.kind
            } finally {
                client.close()
            }
        }

        assertEquals(SourceErrorKind.AUTH, classifiedKind(HttpStatusCode.Unauthorized))
        assertEquals(SourceErrorKind.RATE_LIMITED, classifiedKind(HttpStatusCode.TooManyRequests))
        assertEquals(SourceErrorKind.NOT_FOUND, classifiedKind(HttpStatusCode.NotFound))
    }
}
