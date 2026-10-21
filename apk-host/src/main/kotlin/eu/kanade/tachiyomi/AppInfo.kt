package eu.kanade.tachiyomi

/** Host application info exposed to extensions (extensions-lib 13+); the desktop host reports its own constants. */
object AppInfo {
    fun getVersionCode(): Int = 1

    fun getVersionName(): String = "1.0"
}
