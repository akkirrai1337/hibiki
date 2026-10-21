package app.hibiki.apkhost

import android.util.Log
import java.io.File
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Which certificate authorities the host trusts. The Java runtime ships its own list, which is only
 * as new as the runtime: a site on a CA that is newer (Let's Encrypt's new root hierarchy, say) fails
 * with "unable to find valid certification path" although every browser accepts it. Android keeps its
 * list current through system updates, and so does the operating system here - so what the system
 * trusts is trusted too, next to the runtime's own list. Either vouching for a chain is enough.
 */
object TrustStores {
    private const val TAG = "TrustStores"

    /** Where a Linux system keeps its bundle of trusted roots, in the order distributions use them. */
    private val LINUX_BUNDLES = listOf(
        "/etc/ssl/certs/ca-certificates.crt", // Debian, Ubuntu, Arch
        "/etc/pki/tls/certs/ca-bundle.crt", // Fedora, RHEL
        "/etc/ssl/ca-bundle.pem", // openSUSE
        "/etc/ssl/cert.pem", // Alpine, macOS-style
    )

    val manager: X509TrustManager by lazy { combine(listOfNotNull(runtimeManager(), systemManager(), supplementalManager())) }

    val socketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }.socketFactory
    }

    private fun runtimeManager(): X509TrustManager? = runCatching {
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    }.getOrNull()

    /** Roots the platforms do not carry yet, shipped with the host (see the file's own header). */
    fun supplementalManager(): X509TrustManager? = runCatching {
        val bundle = TrustStores::class.java.getResourceAsStream("/trust/letsencrypt-gen-y.pem")?.use { it.readBytes().decodeToString() } ?: return null
        val file = File.createTempFile("supplemental-roots", ".pem").apply { writeText(bundle); deleteOnExit() }
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(pemStore(file)) }
            .trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    }.getOrNull()

    /** The operating system's roots, or null where Java cannot read them. */
    private fun systemManager(): X509TrustManager? {
        val store = runCatching { systemStore() }.onFailure { Log.w(TAG, "the system's certificate list is not available: ${it.message}") }.getOrNull() ?: return null
        return runCatching {
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
                .trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        }.getOrNull()
    }

    private fun systemStore(): KeyStore? {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("win") -> KeyStore.getInstance("Windows-ROOT").apply { load(null, null) }
            os.contains("mac") -> KeyStore.getInstance("KeychainStore").apply { load(null, null) }
            else -> LINUX_BUNDLES.map(::File).firstOrNull { it.isFile }?.let(::pemStore)
        }
    }

    /** A PEM bundle as a key store; a certificate in it that cannot be read is skipped, not fatal. */
    fun pemStore(bundle: File): KeyStore {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        val factory = CertificateFactory.getInstance("X.509")
        val text = bundle.readText()
        Regex("-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", RegexOption.DOT_MATCHES_ALL).findAll(text).forEachIndexed { index, match ->
            runCatching {
                val certificate = factory.generateCertificate(match.value.byteInputStream()) as X509Certificate
                store.setCertificateEntry("system-$index", certificate)
            }
        }
        return store
    }

    /** Trusts a chain when any of [managers] does; with none, nothing is trusted. */
    fun combine(managers: List<X509TrustManager>): X509TrustManager = Combined(managers)

    private class Combined(private val managers: List<X509TrustManager>) : X509ExtendedTrustManager() {
        private fun anyOf(check: (X509TrustManager) -> Unit) {
            var first: Exception? = null
            for (manager in managers) {
                try {
                    return check(manager)
                } catch (error: java.security.cert.CertificateException) {
                    if (first == null) first = error
                }
            }
            throw first ?: java.security.cert.CertificateException("No trusted certificate authorities")
        }

        // The extended forms carry the socket or engine, which lets a manager check the host name and
        // algorithm constraints; a plain manager gets the simple call.
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = anyOf { manager ->
            if (manager is X509ExtendedTrustManager) manager.checkServerTrusted(chain, authType, socket) else manager.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = anyOf { manager ->
            if (manager is X509ExtendedTrustManager) manager.checkServerTrusted(chain, authType, engine) else manager.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = anyOf { it.checkServerTrusted(chain, authType) }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = anyOf { manager ->
            if (manager is X509ExtendedTrustManager) manager.checkClientTrusted(chain, authType, socket) else manager.checkClientTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = anyOf { manager ->
            if (manager is X509ExtendedTrustManager) manager.checkClientTrusted(chain, authType, engine) else manager.checkClientTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = anyOf { it.checkClientTrusted(chain, authType) }

        override fun getAcceptedIssuers(): Array<X509Certificate> = managers.flatMap { it.acceptedIssuers.asList() }.toTypedArray()
    }
}
