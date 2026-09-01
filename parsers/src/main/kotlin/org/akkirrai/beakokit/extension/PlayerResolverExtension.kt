package org.akkirrai.beakokit.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.context.SourceContext
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.VideoStream
import java.io.File

/** A portable, site-specific player resolver installed separately from an anime source. */
@Serializable
data class PlayerResolverExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val hosts: Set<String>,
    val payload: String = "",
    val type: String = TYPE,
    /** Browser payloads are executed by a host-provided browser runtime, never by the resolver itself. */
    val runtime: ResolverRuntime = ResolverRuntime.HTTP,
    /** Browser resolvers only prepare ExoPlayer streams; PAGE is retained for manifest compatibility. */
    val browserPlaybackMode: BrowserPlaybackMode? = BrowserPlaybackMode.EXTRACT_STREAM,
) {
    fun violations(): List<String> = buildList {
        if (!ID.matches(id)) add("Resolver id must be a lowercase slug: $id")
        if (name.isBlank()) add("Resolver name must not be blank")
        if (!SEMVER.matches(version)) add("Resolver version must be a basic semver string: $version")
        if (hosts.isEmpty() || hosts.any { it.isBlank() }) add("Resolver must declare at least one host")
        if (payload.isBlank()) add("Resolver payload must not be blank")
        if (type != TYPE) add("Resolver type must be $TYPE")
    }

    companion object {
        const val TYPE = "player-resolver"
        private val ID = Regex("""[a-z0-9]+(?:-[a-z0-9]+)*""")
        private val SEMVER = Regex("""\d+\.\d+\.\d+""")
    }
}

@Serializable
enum class ResolverRuntime { HTTP, BROWSER }

/** The host owns the browser implementation; extensions only choose which generic mode they need. */
@Serializable
enum class BrowserPlaybackMode { EXTRACT_STREAM, PAGE }

/** Optional capability implemented by an extension that supplies a browser-page script. */
interface BrowserScriptResolver {
    fun supportsBrowser(link: PlayerLink): Boolean
    suspend fun browserScript(link: PlayerLink): String
    val browserPlaybackMode: BrowserPlaybackMode get() = BrowserPlaybackMode.EXTRACT_STREAM
}

/** Loads resolver files from the same extension directory as source extensions. */
class PlayerResolverExtensionRepository(private val extensionsDir: File) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** See [ScriptExtensionRepository.install] - same cross-repository id-collision protection, applied to resolvers. */
    fun install(manifestJson: String, originRepositoryUrl: String) {
        val manifest = json.decodeFromString(PlayerResolverExtensionManifest.serializer(), manifestJson)
        manifest.violations().takeIf(List<String>::isNotEmpty)?.let { throw IllegalArgumentException(it.joinToString()) }
        val originFile = File(extensionsDir, "${manifest.id}.resolver.origin")
        val existingOrigin = originFile.takeIf(File::exists)?.readText()?.trim()
        if (!existingOrigin.isNullOrEmpty() && existingOrigin != originRepositoryUrl) {
            throw ScriptExtensionOriginConflictException(manifest.id, existingOrigin, originRepositoryUrl)
        }
        extensionsDir.mkdirs()
        File(extensionsDir, "${manifest.id}.resolver.json").writeText(
            json.encodeToString(PlayerResolverExtensionManifest.serializer(), manifest),
        )
        originFile.writeText(originRepositoryUrl)
    }

    fun loadAll(context: SourceContext): List<StreamExtractor> = extensionsDir
        .takeIf(File::isDirectory)
        ?.listFiles { file -> file.isFile && file.name.endsWith(".resolver.json") }
        ?.sortedBy(File::getName)
        .orEmpty()
        .mapNotNull { file ->
            runCatching { json.decodeFromString(PlayerResolverExtensionManifest.serializer(), file.readText()) }
                .getOrNull()
                ?.takeIf { it.violations().isEmpty() }
                ?.let { ScriptedPlayerResolver(it, context, json) }
        }

    /** Installed resolver metadata is needed by the host to surface dependency updates. */
    fun installedManifests(): List<PlayerResolverExtensionManifest> = extensionsDir
        .takeIf(File::isDirectory)
        ?.listFiles { file -> file.isFile && file.name.endsWith(".resolver.json") }
        ?.sortedBy(File::getName)
        .orEmpty()
        .mapNotNull { file ->
            runCatching { json.decodeFromString(PlayerResolverExtensionManifest.serializer(), file.readText()) }
                .getOrNull()
                ?.takeIf { it.violations().isEmpty() }
        }
}

private class ScriptedPlayerResolver(
    private val manifest: PlayerResolverExtensionManifest,
    context: SourceContext,
    private val json: Json,
) : StreamExtractor, BrowserScriptResolver {
    private val runtime = RhinoExtensionRuntime("resolver-${manifest.id}", manifest.payload, context)

    private fun matchesHost(link: PlayerLink): Boolean {
        val host = runCatching { java.net.URI(link.url).host?.lowercase() }.getOrNull()
        return host != null && manifest.hosts.any { host == it || host.endsWith(".$it") }
    }

    override fun supports(link: PlayerLink): Boolean = manifest.runtime == ResolverRuntime.HTTP && matchesHost(link)

    override fun supportsBrowser(link: PlayerLink): Boolean =
        manifest.runtime == ResolverRuntime.BROWSER && matchesHost(link)

    override val browserPlaybackMode: BrowserPlaybackMode
        get() = BrowserPlaybackMode.EXTRACT_STREAM

    override suspend fun browserScript(link: PlayerLink): String {
        check(manifest.runtime == ResolverRuntime.BROWSER) { "Resolver ${manifest.id} is not a browser resolver" }
        return withContext(Dispatchers.IO) {
            runtime.call("browserScript", json.encodeToString(PlayerLink.serializer(), link))
        }
    }

    override suspend fun extract(link: PlayerLink): VideoStream = extractVariants(link).first()

    override suspend fun extractVariants(link: PlayerLink): List<VideoStream> {
        check(manifest.runtime == ResolverRuntime.HTTP) { "Browser resolver ${manifest.id} needs a browser runtime" }
        return withContext(Dispatchers.IO) { runtime.call("resolve", json.encodeToString(PlayerLink.serializer(), link)) }
    }
}
