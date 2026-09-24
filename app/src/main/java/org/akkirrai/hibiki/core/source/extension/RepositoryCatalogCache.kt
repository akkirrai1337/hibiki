package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The last extension repository index fetched for each repository URL, kept in memory and on disk.
 *
 * The extensions screen shows installed extensions with their icons and available updates; all of
 * that comes from the index. Fetching it on every visit made icons pop in each time and re-downloaded
 * a list the user did not ask about, so the screen reads this snapshot and goes to the network only
 * when there is none, when it is stale and the user is browsing repositories, or when asked.
 */
object RepositoryCatalogCache {
    /** How old a snapshot may be before browsing repositories fetches a fresh one. */
    const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L

    data class Snapshot(val extensions: List<ApkRepositoryExtension>, val fetchedAt: Long) {
        fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - fetchedAt > STALE_AFTER_MS
    }

    // The download and icon URLs are derived from the index URL when it is fetched, so they are
    // stored explicitly here rather than in the entry, where they are not serialized.
    @Serializable
    private data class StoredEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String,
        val version: String,
        val nsfw: Int,
        val downloadUrl: String,
        val iconUrl: String,
    )

    @Serializable
    private data class StoredSnapshot(val fetchedAt: Long, val entries: List<StoredEntry>)

    private val json = Json { ignoreUnknownKeys = true }
    private val memory = ConcurrentHashMap<String, Snapshot>()

    fun get(context: Context, url: String): Snapshot? {
        memory[url]?.let { return it }
        val file = fileFor(context, url)
        if (!file.isFile) return null
        return runCatching {
            val stored = json.decodeFromString(StoredSnapshot.serializer(), file.readText())
            Snapshot(
                extensions = stored.entries.map { entry ->
                    ApkRepositoryExtension(
                        name = entry.name,
                        pkg = entry.pkg,
                        apk = entry.apk,
                        lang = entry.lang,
                        version = entry.version,
                        nsfw = entry.nsfw,
                        downloadUrl = entry.downloadUrl,
                        iconUrl = entry.iconUrl,
                    )
                },
                fetchedAt = stored.fetchedAt,
            )
        }.getOrNull()?.also { memory[url] = it }
    }

    fun put(context: Context, url: String, extensions: List<ApkRepositoryExtension>) {
        val snapshot = Snapshot(extensions, System.currentTimeMillis())
        memory[url] = snapshot
        runCatching {
            val stored = StoredSnapshot(
                fetchedAt = snapshot.fetchedAt,
                entries = extensions.map { extension ->
                    StoredEntry(
                        extension.name, extension.pkg, extension.apk, extension.lang, extension.version,
                        extension.nsfw, extension.downloadUrl, extension.iconUrl,
                    )
                },
            )
            fileFor(context, url).apply { parentFile?.mkdirs() }
                .writeText(json.encodeToString(StoredSnapshot.serializer(), stored))
        }
    }

    private fun fileFor(context: Context, url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.applicationContext.filesDir, "repository_catalogs"), "$digest.json")
    }
}
