package app.hibiki.apk

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * APK sources (Aniyomi extensions) for the page, next to its JS sources: the installed extensions,
 * installing / trusting / removing them, and each source's catalogue and playback calls - answered
 * as the shared/types.ts shapes, so core/extensions can route an "apk:" source id here and the rest
 * of the app treats it like any other source.
 */
@CapacitorPlugin(name = "HibikiApk")
class HibikiApkPlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The installed extensions' first load: every call waits for it, so one arriving at startup
     * (the page lists sources as soon as it boots) doesn't find an empty registry. */
    private lateinit var firstLoad: Deferred<Unit>

    override fun load() {
        firstLoad = scope.async { runCatching { ApkSourceRegistry.refresh(context) }; Unit }
    }

    private fun run(call: PluginCall, block: suspend () -> JSObject) {
        scope.launch {
            try {
                firstLoad.await()
                call.resolve(block())
            } catch (error: Throwable) {
                call.reject(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun adapter(call: PluginCall): ApkSourceAdapter {
        val sourceId = call.getString("sourceId") ?: throw IllegalArgumentException("sourceId is required")
        return ApkSourceRegistry.adapter(sourceId) ?: throw IllegalStateException("APK source $sourceId is not installed")
    }

    private fun items(array: JSONArray) = JSObject().put("items", array)

    // --- the installed extensions ------------------------------------------------------------------

    /** Sources ready to use, and every installed extension with its state (trusted, loaded, or why not). */
    @PluginMethod
    fun list(call: PluginCall) = run(call) { describe() }

    @PluginMethod
    fun refresh(call: PluginCall) = run(call) {
        ApkSourceRegistry.refresh(context)
        describe()
    }

    private suspend fun describe(): JSObject {
        val loaded = ApkSourceRegistry.extensions().associateBy { it.extension.packageName }
        val failures = ApkSourceRegistry.failures().associateBy { it.extension.packageName }
        val sources = JSONArray()
        for (extension in loaded.values) {
            for (adapter in extension.adapters) {
                // The extension's settings screen is its own UI code: built on the main thread.
                val settings = withContext(Dispatchers.Main) { runCatching { ApkPreferences.describe(context, adapter) }.getOrDefault(JSONArray()) }
                sources.put(adapter.info(settings))
            }
        }
        val extensions = JSONArray()
        ApkExtensionStore.list(context).forEach { extension ->
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
        return JSObject().put("sources", sources).put("extensions", extensions)
    }

    /**
     * Downloads an extension APK and installs it into the app. Installing it from a repository the
     * person added is their go-ahead, so on a first install its signer becomes the trusted one for
     * the package (as the Kotlin app trusts by repository); an update signed by anyone else is
     * refused before it replaces anything.
     */
    @PluginMethod
    fun install(call: PluginCall) = run(call) {
        val url = call.getString("url") ?: throw IllegalArgumentException("url is required")
        require(url.startsWith("https://")) { "Extensions are only downloaded over https" }
        val expected = call.getString("packageName")
        val downloaded = File(context.cacheDir, "apk-download-${System.nanoTime()}.apk")
        try {
            NetworkHelper.instance().client.newCall(Request.Builder().url(url).build()).await().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                downloaded.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            }
            val incoming = ApkExtensionStore.inspect(context, downloaded) ?: throw IllegalStateException("The file is not an Aniyomi anime extension")
            val trusted = ApkExtensionStore.trustedFingerprint(context, incoming.packageName)
            if (trusted != null && trusted != incoming.signingFingerprint) {
                throw IllegalStateException("${incoming.name}: the update is signed by someone else than the installed version - not installed")
            }
            val installed = ApkExtensionStore.install(context, downloaded, expected)
            if (trusted == null && installed.signingFingerprint != null) {
                ApkExtensionStore.trust(context, installed.packageName, installed.signingFingerprint)
            }
            ApkSourceRegistry.refresh(context)
            JSObject().apply {
                put("packageName", installed.packageName)
                put("name", installed.name)
                put("versionName", installed.versionName)
                put("fingerprint", installed.signingFingerprint)
                put("trusted", true)
            }
        } finally {
            downloaded.delete()
        }
    }

    @PluginMethod
    fun trust(call: PluginCall) = run(call) {
        val packageName = call.getString("packageName") ?: throw IllegalArgumentException("packageName is required")
        val fingerprint = call.getString("fingerprint") ?: throw IllegalArgumentException("fingerprint is required")
        val installed = ApkExtensionStore.find(context, packageName) ?: throw IllegalStateException("$packageName is not installed")
        check(installed.signingFingerprint == fingerprint) { "The extension's signature changed" }
        ApkExtensionStore.trust(context, packageName, fingerprint)
        ApkSourceRegistry.refresh(context)
        describe()
    }

    @PluginMethod
    fun uninstall(call: PluginCall) = run(call) {
        val packageName = call.getString("packageName") ?: throw IllegalArgumentException("packageName is required")
        ApkExtensionStore.uninstall(context, packageName)
        ApkExtensionStore.forget(context, packageName)
        ApkSourceRegistry.refresh(context)
        describe()
    }

    // --- one source's calls -------------------------------------------------------------------------

    @PluginMethod
    fun readSettings(call: PluginCall) = run(call) {
        val adapter = adapter(call)
        JSObject.fromJSONObject(withContext(Dispatchers.Main) { ApkPreferences.values(context, adapter) })
    }

    @PluginMethod
    fun writeSetting(call: PluginCall) = run(call) {
        val adapter = adapter(call)
        val key = call.getString("key") ?: throw IllegalArgumentException("key is required")
        val value = call.getString("value")
        withContext(Dispatchers.Main) { ApkPreferences.write(context, adapter, key, value) }
        JSObject()
    }

    @PluginMethod
    fun search(call: PluginCall) = run(call) {
        items(adapter(call).search(call.getObject("request") ?: JSObject()))
    }

    @PluginMethod
    fun latest(call: PluginCall) = run(call) {
        items(adapter(call).latest(call.getInt("limit") ?: 24))
    }

    @PluginMethod
    fun getById(call: PluginCall) = run(call) {
        JSObject.fromJSONObject(adapter(call).getById(call.getString("id") ?: throw IllegalArgumentException("id is required")))
    }

    @PluginMethod
    fun playbackGroups(call: PluginCall) = run(call) {
        items(adapter(call).playbackGroups(call.getString("titleId") ?: throw IllegalArgumentException("titleId is required")))
    }

    @PluginMethod
    fun playerLinks(call: PluginCall) = run(call) {
        items(adapter(call).playerLinks(call.getString("episodeId") ?: throw IllegalArgumentException("episodeId is required")))
    }

    @PluginMethod
    fun filterCatalog(call: PluginCall) = run(call) {
        JSObject.fromJSONObject(adapter(call).filterCatalog())
    }
}
