package eu.kanade.tachiyomi.torrentutils.model

data class TorrentFile(
    val path: String,
    val indexFile: Int,
    val size: Long,
    private val torrentHash: String,
    private val trackers: List<String> = emptyList(),
) {
    fun toMagnetURI(): String = buildString {
        append("magnet:?xt=urn:btih:").append(torrentHash)
        trackers.forEach { append("&tr=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
    }
}
