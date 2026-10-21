package android

import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.webkit.CookieManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The android.* stand-ins answer the way Android's do, where an extension can tell. */
class AndroidStubsTest {
    @Test
    fun `uri splits an address into its parts`() {
        val uri = Uri.parse("https://user:pw@example.com:8080/a/b%20c/d.mp4?x=1&y=two+words&x=3#frag")
        assertEquals("https", uri.scheme)
        assertEquals("example.com", uri.host)
        assertEquals(8080, uri.port)
        assertEquals("/a/b c/d.mp4", uri.path)
        assertEquals("d.mp4", uri.lastPathSegment)
        assertEquals("1", uri.getQueryParameter("x"))
        assertEquals(listOf("1", "3"), uri.getQueryParameters("x"))
        assertEquals("two words", uri.getQueryParameter("y"))
        assertNull(uri.getQueryParameter("missing"))
        assertEquals("frag", uri.fragment)
        assertEquals("https://user:pw@example.com:8080/a/b%20c/d.mp4?x=1&y=two+words&x=3#frag", uri.toString())
    }

    @Test
    fun `uri parse never fails on an untidy address`() {
        assertEquals("example.com", Uri.parse("http://example.com").host)
        assertEquals("", Uri.parse("http://example.com").path)
        assertNull(Uri.parse("/just/a/path").host)
        assertEquals("/just/a/path", Uri.parse("/just/a/path").path)
        assertEquals("a b", Uri.parse("http://x.com/a b").lastPathSegment)
        assertEquals("[::1]", Uri.parse("http://[::1]:80/").host)
        assertNull(Uri.parse("mailto:someone@example.com").host)
    }

    @Test
    fun `uri encode and decode round trip`() {
        assertEquals("a%20b%2Fc%3Fd%26e", Uri.encode("a b/c?d&e"))
        assertEquals("a%20b/c", Uri.encode("a b/c", "/"))
        assertEquals("a b/c?d&e", Uri.decode("a%20b%2Fc%3Fd%26e"))
        // Android does not turn a plus into a space when decoding a whole string.
        assertEquals("a+b", Uri.decode("a+b"))
        val unicode = "аниме 日本 😀"
        assertEquals(unicode, Uri.decode(Uri.encode(unicode)))
        // A broken escape is kept as it is.
        assertEquals("100%", Uri.decode("100%"))
        assertEquals("%zz", Uri.decode("%zz"))
    }

    @Test
    fun `uri builder appends escaped query parameters`() {
        val built = Uri.parse("https://example.com/search").buildUpon()
            .appendQueryParameter("q", "one piece")
            .appendQueryParameter("page", "2")
            .toString()
        assertEquals("https://example.com/search?q=one%20piece&page=2", built)
        assertEquals("https://example.com/search?a=1&b=2", Uri.parse("https://example.com/search?a=1").buildUpon().appendQueryParameter("b", "2").toString())
    }

    @Test
    fun `uri from file points at the file`() {
        val uri = Uri.fromFile(java.io.File("/tmp/a b.txt"))
        assertEquals("file", uri.scheme)
        assertTrue(uri.path!!.endsWith("a b.txt"))
    }

    @Test
    fun `base64 matches Android's flags`() {
        val bytes = "hello, world?>>".toByteArray()
        assertEquals("aGVsbG8sIHdvcmxkPz4+\n", Base64.encodeToString(bytes, Base64.DEFAULT))
        assertEquals("aGVsbG8sIHdvcmxkPz4+", Base64.encodeToString(bytes, Base64.NO_WRAP))
        assertEquals("YQ==", Base64.encodeToString("a".toByteArray(), Base64.NO_WRAP))
        assertEquals("YQ", Base64.encodeToString("a".toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING))
        val tricky = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        assertEquals("+/+/", Base64.encodeToString(tricky, Base64.NO_WRAP))
        assertEquals("-_-_", Base64.encodeToString(tricky, Base64.NO_WRAP or Base64.URL_SAFE))
        assertEquals(tricky.toList(), Base64.decode("-_-_", Base64.URL_SAFE).toList())
        val long = ByteArray(100) { it.toByte() }
        val wrapped = Base64.encodeToString(long, Base64.DEFAULT)
        assertTrue(wrapped.lines().first().length == 76)
        assertEquals(long.toList(), Base64.decode(wrapped, Base64.DEFAULT).toList())
        // Missing padding and line breaks are accepted when decoding.
        assertEquals("a", String(Base64.decode("YQ", Base64.DEFAULT)))
        assertEquals("a", String(Base64.decode("Y\nQ=\n=", Base64.DEFAULT)))
        assertFailsWith<IllegalArgumentException> { Base64.decode("!!!", Base64.DEFAULT) }
    }

    @Test
    fun `lru cache evicts the least recently used`() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")
        cache.put("c", 3)
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun `cookie manager keeps cookies for the hosts they belong to`() {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.setCookie("https://example.com/", "session=abc; Path=/")
        cookies.setCookie("https://example.com/", "theme=dark; Path=/; Max-Age=3600")
        cookies.setCookie("https://other.org/", "x=y")
        assertEquals("session=abc; theme=dark", cookies.getCookie("https://example.com/page"))
        assertEquals("x=y", cookies.getCookie("https://other.org/"))
        assertNull(cookies.getCookie("https://nowhere.net/"))
        // A later value replaces the earlier one; an expired one removes it.
        cookies.setCookie("https://example.com/", "session=def; Path=/")
        assertEquals("session=def; theme=dark", cookies.getCookie("https://example.com/"))
        cookies.setCookie("https://example.com/", "session=; Path=/; Max-Age=0")
        assertEquals("theme=dark", cookies.getCookie("https://example.com/"))
        cookies.removeAllCookies(null)
    }
}
