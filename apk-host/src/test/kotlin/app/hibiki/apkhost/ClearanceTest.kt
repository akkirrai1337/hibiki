package app.hibiki.apkhost

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeException
import eu.kanade.tachiyomi.network.interceptor.Clearances
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A Cloudflare check is not passed by this process: it recognises the wall, reports it in words the
 * application understands, and takes the clearance the person earns in the application's window.
 */
class ClearanceTest {
    private val dir = Files.createTempDirectory("apk-host-clearance").toFile()
    private val host = ApkHost(dir)
    private val web = MockWebServer()

    @AfterTest
    fun stop() {
        web.shutdown()
        Clearances.clear()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }

    private val wall = """<html><body><h1 id="challenge-error-title">Just a moment</h1><div id="challenge-error-text">Enable JavaScript</div></body></html>"""

    private fun cloudflare(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Server", "cloudflare").setBody(body)

    @Test
    fun `a challenge page becomes the error the application looks for`() {
        web.enqueue(cloudflare(403, wall))
        web.start()
        val url = web.url("/anime").toString()
        val error = assertFailsWith<CloudflareChallengeException> {
            NetworkHelper.instance().client.newCall(Request.Builder().url(url).build()).execute()
        }
        assertEquals(url, error.url)
        // core/extensions/cloudflare.ts reads the address out of exactly this sentence.
        assertEquals("Cloudflare challenge could not be solved for $url", error.message)
    }

    @Test
    fun `a plain refusal or an ordinary page is left alone`() {
        web.enqueue(cloudflare(403, "<html><body>Sorry, you have been blocked</body></html>"))
        web.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "nginx").setBody(wall))
        web.enqueue(cloudflare(200, "<html>fine</html>"))
        web.start()
        val client = NetworkHelper.instance().client
        for (expected in listOf(403, 403, 200)) {
            client.newCall(Request.Builder().url(web.url("/x")).build()).execute().use { assertEquals(expected, it.code) }
        }
    }

    @Test
    fun `a clearance puts its cookies and user agent on the next requests`() {
        web.enqueue(MockResponse().setBody("ok"))
        web.start()
        val url = web.url("/anime").toString()
        host.applyClearance(url, "Mozilla/5.0 TestBrowser", mapOf("cf_clearance" to "abc123", "__cf_bm" to "zzz"))
        NetworkHelper.instance().client.newCall(Request.Builder().url(url).header("User-Agent", "SourceDefault/1.0").build()).execute().close()
        val sent = web.takeRequest()
        assertEquals("Mozilla/5.0 TestBrowser", sent.getHeader("User-Agent"), "the clearance is bound to the browser that earned it")
        val cookie = sent.getHeader("Cookie").orEmpty()
        assertTrue("cf_clearance=abc123" in cookie && "__cf_bm=zzz" in cookie, cookie)
    }

    @Test
    fun `a clearance reaches the site's subdomains and no other site`() {
        assertEquals("UA", run { Clearances.remember("https://example.com/", "UA"); Clearances.userAgentFor("www.example.com") })
        assertEquals("UA", Clearances.userAgentFor("example.com"))
        assertEquals(null, Clearances.userAgentFor("example.org"))
        assertEquals(null, Clearances.userAgentFor("com"))
    }

    @Test
    fun `an address that is not a web address is refused`() {
        assertFailsWith<IllegalArgumentException> { host.applyClearance("not a url", "UA", mapOf("a" to "b")) }
    }
}
