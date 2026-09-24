package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo

/** Hibiki has no torrent engine; sources that need one fail with a clear message instead of a linkage error. */
object TorrentUtils {
    fun getTorrentInfo(url: String, title: String): TorrentInfo =
        throw UnsupportedOperationException("Torrent sources are not supported by Hibiki")
}
