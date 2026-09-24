package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.UnknownHostException

class NetworkFailureLogTest {
    private fun client(action: (okhttp3.Interceptor.Chain) -> Response) = OkHttpClient.Builder()
        .addInterceptor(FailureRecordingInterceptor())
        .addInterceptor(okhttp3.Interceptor(action))
        .build()

    private fun fakeResponse(chain: okhttp3.Interceptor.Chain, code: Int) = Response.Builder()
        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("m").build()

    @Test
    fun `a request that cannot resolve its host is recorded and still fails for the caller`() {
        val since = System.currentTimeMillis()
        val client = client { throw UnknownHostException("nope") }

        var thrown: Throwable? = null
        try {
            client.newCall(Request.Builder().url("https://missing-host.example/x").build()).execute()
        } catch (error: Throwable) {
            thrown = error
        }

        assertNotNull(thrown)
        assertEquals("missing-host.example: cannot resolve host", NetworkFailureLog.summary(since))
    }

    @Test
    fun `http errors are recorded and successes are not`() {
        val since = System.currentTimeMillis()
        client { fakeResponse(it, 403) }.newCall(Request.Builder().url("https://blocked.example/a").build()).execute().close()
        client { fakeResponse(it, 200) }.newCall(Request.Builder().url("https://fine.example/a").build()).execute().close()

        val summary = NetworkFailureLog.summary(since)
        assertEquals("blocked.example: HTTP 403", summary)
    }

    @Test
    fun `nothing recorded means no summary`() {
        assertNull(NetworkFailureLog.summary(System.currentTimeMillis() + 60_000))
    }
}
