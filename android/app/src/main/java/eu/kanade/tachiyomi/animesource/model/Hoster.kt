package eu.kanade.tachiyomi.animesource.model

/** A video host for an episode (extensions-lib 16). Its videos are either listed or loaded on demand. */
open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
) {
    @Volatile
    var status: State = State.IDLE

    enum class State { IDLE, LOADING, READY, ERROR }

    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
    ): Hoster = Hoster(hosterUrl, hosterName, videoList, internalData, lazy)

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        fun List<Video>.toHosterList(): List<Hoster> = listOf(
            Hoster(hosterName = NO_HOSTER_LIST, videoList = this),
        )
    }
}
