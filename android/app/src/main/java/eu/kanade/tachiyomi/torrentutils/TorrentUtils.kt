package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo

/** Hibiki has no torrent engine; sources that need one fail with a clear message instead of a linkage error. */
object TorrentUtils {
    fun getTorrentInfo(url: String, title: String): TorrentInfo =
        throw UnsupportedOperationException("Torrent sources are not supported by Hibiki")

    /** extensions-lib 17 made the call suspending; same JVM name, one more (Continuation) parameter. */
    @JvmName("getTorrentInfo")
    suspend fun getTorrentInfoSuspending(url: String, title: String): TorrentInfo = getTorrentInfo(url, title)
}
