package org.akkirrai.beakokit.extension

import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.ChallengeSessionRequest
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.context.SourceContext
import org.akkirrai.beakokit.api.context.SourceLogLevel
import org.akkirrai.beakokit.http.decodeShiftedBase64
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.http.isAbsoluteUrl
import org.akkirrai.beakokit.http.normalizeUrl
import org.akkirrai.beakokit.http.originOf
import org.akkirrai.beakokit.http.pathOf
import org.akkirrai.beakokit.http.resolveUrl
import org.akkirrai.beakokit.http.schemeOf
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.WrapFactory
import java.net.URI

/**
 * Runs one scripted extension's JS payload inside a sandboxed Rhino interpreter.
 *
 * Rhino must stay in interpreted mode ([Context.setOptimizationLevel] `-1`) because its default
 * compiled mode generates JVM bytecode at runtime via a custom class loader, which Android's ART
 * does not support. Every call is serialized through [lock] because a single [Scriptable] scope is
 * not safe for concurrent use from multiple threads.
 *
 * The only globals a payload can reach are the ones explicitly installed below - the reflection
 * doors Rhino normally exposes (`Packages`, `java`, `JavaImporter`, ...) are removed from scope, so
 * a script can only touch the network (via [fetch]) and HTML parsing (via the curated [Jsoup]
 * binding), the same trust boundary as today's compiled-in Kotlin scrapers.
 */
class RhinoExtensionRuntime(
    @PublishedApi internal val extensionId: String,
    payload: String,
    private val sourceContext: SourceContext,
) {
    private val lock = Any()

    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope: ScriptableObject

    init {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            cx.wrapFactory = StringPassthroughWrapFactory
            val newScope = cx.initStandardObjects()
            hardenScope(newScope)
            installGlobals(cx, newScope)
            cx.evaluateString(newScope, payload, "$extensionId.js", 1, null)
            scope = newScope
        } finally {
            Context.exit()
        }
    }

    /** Calls `Provider.<functionName>(args)` and decodes its JSON-serialized return value as [T]. */
    inline fun <reified T> call(functionName: String, vararg args: Any?): T {
        val jsonText = callRaw(functionName, args)
        return try {
            json.decodeFromString(jsonText)
        } catch (error: Exception) {
            throw SourceException(
                "Extension '$extensionId' returned an unexpected shape from $functionName",
                cause = error,
                kind = SourceErrorKind.PARSE,
            )
        }
    }

    fun callRaw(functionName: String, args: Array<out Any?>): String = synchronized(lock) {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.wrapFactory = StringPassthroughWrapFactory
            val provider = ScriptableObject.getProperty(scope, "Provider") as? Scriptable
                ?: throw SourceException(
                    "Extension '$extensionId' does not define a Provider object",
                    kind = SourceErrorKind.PARSE,
                )
            val fn = ScriptableObject.getProperty(provider, functionName) as? Function
                ?: throw SourceException(
                    "Extension '$extensionId' Provider.$functionName is not a function",
                    kind = SourceErrorKind.PARSE,
                )
            val jsArgs = args.map { Context.javaToJS(it, scope) }.toTypedArray()
            val result = fn.call(cx, scope, provider, jsArgs)
            Context.toString(NativeJSON.stringify(cx, scope, result, null, null))
        } catch (error: SourceException) {
            throw error
        } catch (error: Exception) {
            throw SourceException(
                "Extension '$extensionId' threw while running $functionName: ${error.message ?: error}",
                cause = error,
                kind = SourceErrorKind.PARSE,
            )
        } finally {
            Context.exit()
        }
    }

    /**
     * Fixes the "Rhino Java-string boxing" gotcha at its root instead of leaning on every payload
     * remembering to call an `S(x)`/`String(x)` helper: a Java method returning `java.lang.String`
     * (every Jsoup `.text()`/`.attr()`/`.absUrl()` call) is otherwise handed to JS as a wrapped
     * `NativeJavaObject`, not a native string primitive, so `===`, `.charAt()`, and regexes on it
     * misbehave until coerced. Passing the raw Java String straight through here makes Rhino treat
     * it as a native JS string automatically, the same way it already does for numbers/booleans -
     * existing `String(x)` calls in extension payloads remain harmless no-ops on an already-native
     * value, so this doesn't require touching any existing `.js` payload.
     */
    private object StringPassthroughWrapFactory : WrapFactory() {
        override fun wrap(cx: Context?, scope: Scriptable?, obj: Any?, staticType: Class<*>?): Any? =
            if (obj is String) obj else super.wrap(cx, scope, obj, staticType)
    }

    private fun hardenScope(scope: ScriptableObject) {
        listOf("Packages", "java", "JavaAdapter", "JavaImporter", "Continuation", "Java").forEach { name ->
            if (ScriptableObject.hasProperty(scope, name)) {
                ScriptableObject.deleteProperty(scope, name)
            }
        }
    }

    private fun installGlobals(cx: Context, scope: ScriptableObject) {
        ScriptableObject.putProperty(scope, "Jsoup", Context.javaToJS(JsoupBinding(), scope))
        ScriptableObject.putProperty(scope, "Base64", Context.javaToJS(Base64Binding(), scope))
        ScriptableObject.putProperty(scope, "Url", Context.javaToJS(UrlBinding(), scope))
        ScriptableObject.putProperty(scope, "AnimeTitle", AnimeTitleFunction(scope))
        ScriptableObject.putProperty(scope, "collectPaginated", PaginationCollectFunction(scope))
        ScriptableObject.putProperty(scope, "console", Context.javaToJS(ConsoleBinding(sourceContext), scope))
        val fetchFunction = FetchFunction(sourceContext, scope)
        ScriptableObject.putProperty(scope, "fetch", fetchFunction)
        ScriptableObject.putProperty(scope, "challenge", ChallengeFunction(sourceContext, scope))
        ScriptableObject.putProperty(
            scope,
            "preferredLanguage",
            sourceContext.preferredLanguages.firstOrNull()?.tag ?: "en",
        )
    }

    /** Curated HTML-parsing surface; only this instance (not the Jsoup class itself) is reachable from JS. */
    class JsoupBinding {
        fun parse(html: String): Document = Jsoup.parse(html)

        fun parse(html: String, baseUri: String): Document = Jsoup.parse(html, baseUri)

        fun parseBodyFragment(html: String): Document = Jsoup.parseBodyFragment(html)

        fun parseBodyFragment(html: String, baseUri: String): Document = Jsoup.parseBodyFragment(html, baseUri)

        fun resolve(baseUrl: String, relative: String): String =
            runCatching { URI(baseUrl).resolve(relative).toString() }.getOrDefault(relative)
    }

    /**
     * Base64 encode/decode as a host global - Rhino has no native atob/btoa, and hand-rolling a
     * decoder was showing up duplicated verbatim across extensions that need to unwrap a
     * base64-encoded HTML/URI fragment (kodik.js, donghuastream.js, ...). Decoding follows the
     * same "one byte per char code" convention those hand-rolled versions used (so existing
     * `String.fromCharCode`-based post-processing keeps working unchanged), and tolerates stray
     * whitespace/newlines and missing padding the way they did too.
     */
    class Base64Binding {
        fun decode(value: String): String {
            val cleaned = value.replace(Regex("[^A-Za-z0-9+/]"), "")
            val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
            val bytes = try {
                java.util.Base64.getDecoder().decode(padded)
            } catch (error: IllegalArgumentException) {
                return ""
            }
            return String(CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).toChar() })
        }

        fun encode(value: String): String {
            val bytes = ByteArray(value.length) { (value[it].code and 0xFF).toByte() }
            return java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    /**
     * URL parsing/normalization as a host global, backed by the same [org.akkirrai.beakokit.http]
     * functions the compiled-in scrapers use - extensions were each hand-rolling their own
     * `normalizeUrl`/`originOf` regex (yummy-anime.js, kodik.js, sibnet.js, vk.js), slightly
     * differently, instead of reusing this already-tested logic. `decodeShifted` similarly replaces
     * Kodik's own `shiftLetter`/`decodeShiftedBase64` pair with the existing Kotlin implementation.
     */
    class UrlBinding {
        fun normalize(url: String): String = normalizeUrl(url)
        fun resolve(base: String, reference: String): String = resolveUrl(base, reference)
        fun origin(url: String): String = originOf(url)
        fun scheme(url: String): String = schemeOf(url)
        fun host(url: String): String? = hostOf(url)
        fun path(url: String): String = pathOf(url)
        fun isAbsolute(url: String): Boolean = isAbsoluteUrl(url)
        fun decodeShifted(raw: String): String = decodeShiftedBase64(raw)
    }

    class ConsoleBinding(private val sourceContext: SourceContext) {
        fun log(message: Any?) = sourceContext.logger.log(SourceLogLevel.DEBUG, "$message", null)
        fun warn(message: Any?) = sourceContext.logger.log(SourceLogLevel.WARNING, "$message", null)
        fun error(message: Any?) = sourceContext.logger.log(SourceLogLevel.ERROR, "$message", null)
    }

    /**
     * A synchronous `fetch(url, options)` backed by [SourceContext.httpClient]. Synchronous because
     * Rhino has no native Promise/async-await support; this is safe since every extension call
     * already runs on a background dispatcher (see [ScriptedAnimeSource]).
     */
    private class FetchFunction(
        private val sourceContext: SourceContext,
        private val scope: Scriptable,
    ) : org.mozilla.javascript.BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable,
            args: Array<out Any?>,
        ): Any {
            val url = Context.toString(args.getOrNull(0))
            val options = args.getOrNull(1) as? NativeObject
            val method = (options?.get("method", options) as? String)?.uppercase() ?: "GET"
            val headers = (options?.get("headers", options) as? NativeObject)?.entries
                ?.associate { (key, value) -> key.toString() to Context.toString(value) }
                .orEmpty()
            val form = (options?.get("form", options) as? NativeObject)?.entries
                ?.associate { (key, value) -> key.toString() to Context.toString(value) }
            val body = options?.get("body", options) as? String

            val (status, responseBody, responseHeaders) = runBlocking {
                val response = sourceContext.httpClient.request(url) {
                    this.method = HttpMethod.parse(method)
                    headers.forEach { (key, value) -> header(key, value) }
                    when {
                        form != null -> setBody(FormDataContent(Parameters.build { form.forEach { (k, v) -> append(k, v) } }))
                        body != null -> setBody(body)
                    }
                }
                Triple(
                    response.status.value,
                    response.bodyAsText(),
                    response.headers.names().associateWith { name -> response.headers[name].orEmpty() },
                )
            }

            val result = cx.newObject(this.scope)
            ScriptableObject.putProperty(result, "status", status)
            ScriptableObject.putProperty(result, "ok", status in 200..299)
            ScriptableObject.putProperty(result, "body", responseBody)
            val headersObject = cx.newObject(this.scope)
            responseHeaders.forEach { (name, value) -> ScriptableObject.putProperty(headersObject, name.lowercase(), value) }
            ScriptableObject.putProperty(result, "headers", headersObject)
            return result
        }
    }

    /**
     * Fills in every [org.akkirrai.beakokit.model.AnimeTitle] field a source doesn't set itself, as
     * a host global - the same ~25-key "base object + override" literal was hand-copied verbatim
     * into 8 source payloads (ani-liberty.js, anichi.js, animego.js, animepahe.js, animevost.js,
     * donghuastream.js, kickassanime.js, yummy-anime.js). Each keeps its own one-line
     * `function title(fields) { return AnimeTitle(fields); }` wrapper so existing `title({...})`
     * call sites don't need touching.
     */
    private class AnimeTitleFunction(private val scope: Scriptable) : org.mozilla.javascript.BaseFunction() {
        private val nullDefaultKeys = listOf(
            "russianName", "englishName", "japaneseName", "year", "type", "episodeCount",
            "posterUrl", "status", "description", "nextEpisodeAt", "ageRating", "viewCount",
            "trailer", "sourceMaterial", "season", "availableEpisodeCount", "posterFallbackUrl",
        )
        private val emptyListDefaultKeys = listOf(
            "synonyms", "genres", "ratings", "screenshots", "studios",
            "mainCharacters", "similarAnime", "franchiseAnime", "relatedAnime",
        )

        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable,
            args: Array<out Any?>,
        ): Any {
            val result = cx.newObject(this.scope)
            nullDefaultKeys.forEach { ScriptableObject.putProperty(result, it, null) }
            emptyListDefaultKeys.forEach { ScriptableObject.putProperty(result, it, cx.newArray(this.scope, 0)) }
            val fields = args.getOrNull(0) as? Scriptable
            if (fields != null) {
                for (id in fields.ids) {
                    val key = id as? String ?: continue
                    ScriptableObject.putProperty(result, key, ScriptableObject.getProperty(fields, key))
                }
            }
            return result
        }
    }

    /**
     * Walks a paginated catalog listing until `wanted` results are collected, a page comes back
     * empty, a page is shorter than `pageSize` (the last page), or `fetchPage` itself throws - many
     * WP-theme/KuAnime-engine hosts error instead of returning an empty listing past their real
     * last page, so that's treated the same as "nothing more to collect", not a fatal error.
     * Dedupes by each result's `id` field. Replaces the byte-identical `collectResults(fetchPage,
     * wanted)` previously hand-copied into anichi.js and donghuastream.js.
     */
    private class PaginationCollectFunction(private val scope: Scriptable) : org.mozilla.javascript.BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable,
            args: Array<out Any?>,
        ): Any {
            val fetchPage = args.getOrNull(0) as? Function
                ?: throw IllegalArgumentException("collectPaginated requires a fetchPage(page) function as its first argument")
            val wanted = Context.toNumber(args.getOrNull(1)).toInt()
            val pageSize = Context.toNumber(args.getOrNull(2)).toInt()

            val results = mutableListOf<Any?>()
            val seen = HashSet<String>()
            var page = 1
            while (results.size < wanted && page <= 50) {
                val items = try {
                    fetchPage.call(cx, this.scope, thisObj, arrayOf(page)) as? Scriptable
                } catch (error: Exception) {
                    null
                } ?: break

                val length = Context.toNumber(ScriptableObject.getProperty(items, "length")).toInt()
                if (length == 0) break
                for (i in 0 until length) {
                    val item = ScriptableObject.getProperty(items, i) as? Scriptable ?: continue
                    val id = ScriptableObject.getProperty(item, "id")
                    if (!seen.add(Context.toString(id))) continue
                    results.add(item)
                }
                if (length < pageSize) break
                page += 1
            }

            return cx.newArray(this.scope, results.toTypedArray())
        }
    }

    /**
     * Exposes [org.akkirrai.beakokit.api.ChallengeSessionProvider] to JS for sources (like
     * AnimePahe) that sit behind an interactive browser challenge (e.g. Cloudflare). Mirrors
     * [org.akkirrai.beakokit.http.ChallengeRequestExecutor]'s contract exactly, just callable from
     * script instead of baked into a Kotlin HTTP client: the script decides when a response looks
     * challenged and calls this to get cookies/UA to retry with.
     */
    private class ChallengeFunction(
        private val sourceContext: SourceContext,
        private val scope: Scriptable,
    ) : org.mozilla.javascript.BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable,
            args: Array<out Any?>,
        ): Any {
            val url = Context.toString(args.getOrNull(0))
            val cookieNames = (args.getOrNull(1) as? org.mozilla.javascript.NativeArray)
                ?.map { Context.toString(it) }
                ?.toSet()
                ?: emptySet()
            val forceRefresh = args.getOrNull(2)?.let { Context.toBoolean(it) } ?: false

            val session = runBlocking {
                sourceContext.challengeSessionProvider.acquire(
                    ChallengeSessionRequest(url = url, requiredCookieNames = cookieNames, forceRefresh = forceRefresh),
                )
            }

            val result = cx.newObject(this.scope)
            val cookiesObject = cx.newObject(this.scope)
            session.cookies.forEach { (name, value) -> ScriptableObject.putProperty(cookiesObject, name, value) }
            ScriptableObject.putProperty(result, "cookies", cookiesObject)
            ScriptableObject.putProperty(result, "cookieHeader", session.cookieHeader)
            ScriptableObject.putProperty(result, "userAgent", session.userAgent)
            return result
        }
    }
}
