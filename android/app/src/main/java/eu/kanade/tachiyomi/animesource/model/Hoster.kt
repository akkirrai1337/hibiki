package eu.kanade.tachiyomi.animesource.model

import kotlinx.serialization.json.JsonObject

/**
 * A video host for an episode (extensions-lib 16, [memo] since 17). Its videos are either listed or
 * loaded on demand; the source reads [memo] back in getVideoList(hoster).
 */
open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
    val memo: JsonObject = JsonObject(emptyMap()),
) {
    @Volatile
    var status: State = State.IDLE

    enum class State { IDLE, LOADING, READY, ERROR }

    /** extensions-lib 16 constructor. */
    constructor(
        hosterUrl: String = "",
        hosterName: String = "",
        videoList: List<Video>? = null,
        internalData: String = "",
        lazy: Boolean = false,
    ) : this(hosterUrl, hosterName, videoList, internalData, lazy, JsonObject(emptyMap()))

    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
        memo: JsonObject = this.memo,
    ): Hoster = Hoster(hosterUrl, hosterName, videoList, internalData, lazy, memo)

    /** extensions-lib 16 copy. */
    @Deprecated("Used only for compatibility with ext lib 16", level = DeprecationLevel.HIDDEN)
    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
    ): Hoster = Hoster(hosterUrl, hosterName, videoList, internalData, lazy, memo)

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        fun List<Video>.toHosterList(): List<Hoster> = listOf(
            Hoster(hosterName = NO_HOSTER_LIST, videoList = this, memo = JsonObject(emptyMap())),
        )
    }
}
