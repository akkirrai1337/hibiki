package org.akkirrai.beakokit.extension

import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.SourceCatalogEntry
import org.akkirrai.beakokit.api.SourceFactory
import org.akkirrai.beakokit.api.context.SourceContext
import java.io.File

/**
 * Loads/installs/uninstalls scripted extensions stored as one JSON file per extension in
 * [extensionsDir] - the same flat-file layout seanime uses for its external extensions.
 *
 * This is a pure-JVM class (a plain [File] directory, not an Android `Context`) so it can be
 * exercised directly from `:parsers` unit tests without an Android runtime.
 */
class ScriptExtensionRepository(private val extensionsDir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    data class LoadResult(
        val entries: List<SourceCatalogEntry>,
        val invalid: List<InvalidScriptExtension>,
    ) {
        companion object {
            val EMPTY = LoadResult(emptyList(), emptyList())
        }
    }

    fun listManifestFiles(): List<File> = extensionsDir
        .takeIf(File::isDirectory)
        ?.listFiles { file -> file.isFile && file.extension == "json" }
        ?.sortedBy(File::getName)
        .orEmpty()

    /**
     * Validates and persists [manifestJson] as `<id>.json`, overwriting any existing install from
     * the *same* [originRepositoryUrl]. A different origin claiming an id that's already installed
     * is refused instead - without this, a third-party repository could publish an extension (or,
     * worse, a [PlayerResolverExtensionRepository] resolver silently pulled in by some other
     * source's `resolverDependencies`) reusing an official id like `animevost` or `anitube-ashdi`
     * and silently replace its trusted code the next time anyone installs/updates it. An id that's
     * never been installed before, or was last installed from this exact origin, proceeds as
     * before - including anything installed before this check existed, which has no `.origin`
     * file yet and so is treated the same as "never installed" (whichever repository updates it
     * first now owns it going forward) rather than needing a migration.
     */
    fun install(manifestJson: String, originRepositoryUrl: String) {
        val manifest = json.decodeFromString(ScriptExtensionManifest.serializer(), manifestJson)
        manifest.violations().takeIf(List<String>::isNotEmpty)?.let { violations ->
            throw ScriptExtensionValidationException(manifest.id, violations)
        }
        val originFile = File(extensionsDir, "${manifest.id}.origin")
        val existingOrigin = originFile.takeIf(File::exists)?.readText()?.trim()
        if (!existingOrigin.isNullOrEmpty() && existingOrigin != originRepositoryUrl) {
            throw ScriptExtensionOriginConflictException(manifest.id, existingOrigin, originRepositoryUrl)
        }
        extensionsDir.mkdirs()
        File(extensionsDir, "${manifest.id}.json").writeText(json.encodeToString(ScriptExtensionManifest.serializer(), manifest))
        originFile.writeText(originRepositoryUrl)
    }

    fun uninstall(id: String) {
        File(extensionsDir, "$id.json").delete()
        File(extensionsDir, "$id.origin").delete()
    }

    /** Every currently-installed manifest (payload included), skipping any that fail to parse. */
    fun installedManifests(): List<ScriptExtensionManifest> = listManifestFiles().mapNotNull { file ->
        runCatching { json.decodeFromString(ScriptExtensionManifest.serializer(), file.readText()) }.getOrNull()
    }

    fun loadAll(): LoadResult {
        val entries = mutableListOf<SourceCatalogEntry>()
        val invalid = mutableListOf<InvalidScriptExtension>()
        listManifestFiles().forEach { file ->
            runCatching {
                val manifest = json.decodeFromString(ScriptExtensionManifest.serializer(), file.readText())
                val violations = manifest.violations()
                if (violations.isNotEmpty()) throw ScriptExtensionValidationException(manifest.id, violations)
                entries += SourceCatalogEntry(
                    info = manifest.toSourceInfo(),
                    factory = SourceFactory { context -> ScriptedAnimeSource(context, manifest) },
                )
            }.onFailure { error ->
                invalid += InvalidScriptExtension(
                    id = file.nameWithoutExtension,
                    path = file.path,
                    reason = error.message ?: error.toString(),
                )
            }
        }
        return LoadResult(entries, invalid)
    }
}
