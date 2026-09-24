package eu.kanade.tachiyomi

import org.akkirrai.hibiki.BuildConfig

/** Host application info exposed to extensions (extensions-lib 13+). */
object AppInfo {
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    fun getVersionName(): String = BuildConfig.VERSION_NAME
}
