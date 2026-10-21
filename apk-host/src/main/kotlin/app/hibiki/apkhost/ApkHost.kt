package app.hibiki.apkhost

import android.app.Application
import app.hibiki.apk.ApkPreferences
import app.hibiki.apk.ApkSourceAdapter
import app.hibiki.apk.InstalledApkExtension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.Clearances
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import eu.kanade.tachiyomi.network.await
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * APK sources for the application: the installed extensions, installing / trusting / removing
 * them, and the registry of their loaded sources. The desktop twin of the Android build's
 * HibikiApkPlugin, without the Capacitor wrapper: Server.kt puts these calls on HTTP.
 */
class ApkHost(val dataDir: File) {
    val application: Application = HostSetup.initialize(dataDir)
    val store = ExtensionStore(dataDir)
    val registry = SourceRegistry(store, File(dataDir, "converted"))

    init {
        registry.refresh()
    }

    fun adapter(sourceId: String): ApkSourceAdapter =
        registry.adapter(sourceId) ?: throw IllegalStateException("APK source $sourceId is not installed")

    /** Sources ready to use, and every installed extension with its state (trusted, loaded, or why not). */
    fun describe(): JSONObject {
        val loaded = registry.extensions().associateBy { it.extension.packageName }
        val failures = registry.failures().associateBy { it.extension.packageName }
        val sources = JSONArray()
        for (extension in loaded.values) {
            for (adapter in extension.adapters) {
                val settings = runCatching { ApkPreferences.describe(application, adapter) }.getOrDefault(JSONArray())
                sources.put(adapter.info(settings))
            }
        }
        val extensions = JSONArray()
        store.list().forEach { extension ->
            extensions.put(JSONObject().apply {
                put("packageName", extension.packageName)
                put("name", extension.name)
                put("versionName", extension.versionName)
                put("versionCode", extension.versionCode)
                put("isNsfw", extension.isNsfw)
                put("trusted", extension.isTrusted)
                put("fingerprint", extension.signingFingerprint ?: JSONObject.NULL)
                put("iconPath", extension.iconFile?.absolutePath ?: JSONObject.NULL)
                put("sourceIds", JSONArray(loaded[extension.packageName]?.adapters?.map { it.id }.orEmpty()))
                put("error", failures[extension.packageName]?.message ?: JSONObject.NULL)
            })
        }
        return JSONObject().put("sources", sources).put("extensions", extensions)
    }

    /**
     * Installs an APK file the host already has. Installing from a repository the person added is
     * their go-ahead, so on a first install its signer becomes the trusted one for the package; an
     * update signed by anyone else is refused before it replaces anything.
     */
    fun install(downloaded: File, expectedPackage: String?): InstalledApkExtension {
        val incoming = store.inspect(downloaded) ?: throw IllegalStateException("The file is not an Aniyomi anime extension")
        val trusted = store.trustedFingerprint(incoming.packageName)
        if (trusted != null && trusted != incoming.signingFingerprint) {
            throw IllegalStateException("${incoming.name}: the update is signed by someone else than the installed version - not installed")
        }
        // The running copy holds its APK open, and Windows will not replace a file that is open.
        registry.unload(incoming.packageName)
        try {
            val installed = store.install(downloaded, expectedPackage)
            if (trusted == null && installed.signingFingerprint != null) store.trust(installed.packageName, installed.signingFingerprint)
            return installed
        } finally {
            // Loads the new version, or puts the old one back when the install failed.
            registry.refresh()
        }
    }

    /** Downloads an extension APK (https only) and installs it. */
    suspend fun install(url: String, expectedPackage: String?): InstalledApkExtension {
        require(url.startsWith("https://")) { "Extensions are only downloaded over https" }
        val downloaded = File(application.cacheDir, "apk-download-${System.nanoTime()}.apk")
        try {
            NetworkHelper.instance().client.newCall(Request.Builder().url(url).build()).await().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                downloaded.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            }
            return install(downloaded, expectedPackage)
        } finally {
            downloaded.delete()
        }
    }


    /**
     * What a Cloudflare check passed in the application's window earned: its cookies go into the
     * shared store, and the site is spoken to with that window's User-Agent from now on (a clearance
     * is bound to the browser that earned it).
     */
    fun applyClearance(url: String, userAgent: String, cookies: Map<String, String>) {
        val host = url.toHttpUrlOrNull()?.host ?: throw IllegalArgumentException("Not a web address: $url")
        val manager = android.webkit.CookieManager.getInstance()
        for ((name, value) in cookies) manager.setCookie(url, "$name=$value; Domain=$host; Path=/")
        Clearances.remember(url, userAgent)
    }
    fun trust(packageName: String, fingerprint: String) {
        val installed = store.find(packageName) ?: throw IllegalStateException("$packageName is not installed")
        check(installed.signingFingerprint == fingerprint) { "The extension's signature changed" }
        store.trust(packageName, fingerprint)
        registry.refresh()
    }

    fun uninstall(packageName: String) {
        registry.unload(packageName)
        store.uninstall(packageName)
        store.forget(packageName)
        File(dataDir, "converted").listFiles { f -> f.name.startsWith("$packageName-") }?.forEach(File::delete)
        registry.refresh()
    }
}
