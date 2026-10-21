package app.hibiki.apkhost

import java.io.File
import java.nio.file.Files
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrustStoresTest {
    private class Fake(private val trusts: Boolean, private val issuers: Int = 0) : X509TrustManager {
        var asked = 0
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { asked++; if (!trusts) throw CertificateException("no") }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { asked++; if (!trusts) throw CertificateException("no") }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOfNulls<X509Certificate>(issuers).requireNoNulls()
    }

    @Test
    fun `a chain is trusted when any manager vouches for it`() {
        val refusing = Fake(false)
        val accepting = Fake(true)
        val combined = TrustStores.combine(listOf(refusing, accepting))
        combined.checkServerTrusted(emptyArray(), "ECDHE_ECDSA")
        assertEquals(1, refusing.asked)
        assertEquals(1, accepting.asked)
    }

    @Test
    fun `a chain nobody vouches for is refused with the first complaint`() {
        val combined = TrustStores.combine(listOf(Fake(false), Fake(false)))
        assertFailsWith<CertificateException> { combined.checkServerTrusted(emptyArray(), "RSA") }
        assertFailsWith<CertificateException> { TrustStores.combine(emptyList()).checkServerTrusted(emptyArray(), "RSA") }
    }

    @Test
    fun `the first manager that accepts ends the asking`() {
        val first = Fake(true)
        val second = Fake(true)
        TrustStores.combine(listOf(first, second)).checkServerTrusted(emptyArray(), "RSA")
        assertEquals(0, second.asked)
    }

    @Test
    fun `a PEM bundle becomes a store and a broken entry is skipped`() {
        val any = TrustStores.manager.acceptedIssuers.first()
        val pem = "-----BEGIN CERTIFICATE-----\n" + java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(any.encoded) + "\n-----END CERTIFICATE-----\n"
        val broken = "-----BEGIN CERTIFICATE-----\nnot a certificate\n-----END CERTIFICATE-----\n"
        val file = Files.createTempFile("bundle", ".pem").toFile().apply { writeText(broken + pem + pem) }
        val store = TrustStores.pemStore(file)
        assertEquals(2, store.size())
        file.delete()
    }

    @Test
    fun `the shipped Let's Encrypt roots are the ones Let's Encrypt publishes`() {
        val roots = TrustStores.supplementalManager()!!.acceptedIssuers.associateBy { it.subjectX500Principal.name }
        fun fingerprint(certificate: X509Certificate) =
            java.security.MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
        // As letsencrypt.org/certificates lists them, and as the cross-signed copies by ISRG Root X2 / X1 confirm.
        assertEquals("E1:4F:FC:AD:5B:00:25:73:10:06:CA:A4:3A:12:1A:22:D8:E9:70:0F:4F:B9:CF:85:2F:02:A7:08:AA:5D:56:66", fingerprint(roots.getValue("CN=Root YE,O=ISRG,C=US")))
        assertEquals("E5:7B:7E:6F:15:0C:41:91:02:E8:D5:C0:55:72:9F:F9:67:B9:D1:A8:29:BF:00:CE:C8:9C:A6:04:EB:F4:A8:6F", fingerprint(roots.getValue("CN=Root YR,O=ISRG,C=US")))
        assertEquals(2, roots.size)
    }

    @Test
    fun `the runtime's own roots are trusted`() {
        assertTrue(TrustStores.manager.acceptedIssuers.size > 50)
    }

    @Test
    fun `an https site on a root the runtime may not know loads through the host's client`() {
        // Let's Encrypt's new ECDSA hierarchy (ISRG Root YE): the runtime may not carry it, the system does.
        if (System.getProperty("apk.live") != "true") return
        HostSetup.initialize(Files.createTempDirectory("apk-host-trust").toFile())
        val client = eu.kanade.tachiyomi.network.NetworkHelper.instance().nonCloudflareClient
        client.newCall(okhttp3.Request.Builder().url("https://animevostfr.tv/").build()).execute().use {
            assertTrue(it.code in 200..499, "answered ${it.code}")
        }
    }
}
