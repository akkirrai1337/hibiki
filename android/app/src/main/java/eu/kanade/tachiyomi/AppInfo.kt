package eu.kanade.tachiyomi

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Host application info exposed to extensions (extensions-lib 13+), read from the installed package. */
object AppInfo {
    @Suppress("DEPRECATION")
    fun getVersionCode(): Int {
        val app = Injekt.get<Application>()
        return app.packageManager.getPackageInfo(app.packageName, 0).versionCode
    }

    fun getVersionName(): String {
        val app = Injekt.get<Application>()
        return app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.0"
    }
}
