package eu.kanade.tachiyomi.animesource.model

/** extensions-lib 17: seek-bar preview tiles for a video. Hibiki does not show them yet. */
open class ThumbnailInfo(
    val tileInfo: List<TileInfo>,
    val imageTileUrls: List<String>,
)

data class TileInfo(
    val imageIndex: Int,
    val timeMs: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
