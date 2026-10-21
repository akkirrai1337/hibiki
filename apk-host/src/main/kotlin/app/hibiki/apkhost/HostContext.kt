package app.hibiki.apkhost

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The "device" an extension runs on: directories under [dataDir], and SharedPreferences stored beside them. */
class HostContext(private val dataDir: File, private val packageName: String = PACKAGE_NAME) : Context() {
    private val preferences = ConcurrentHashMap<String, FilePreferences>()
    private val resources = Resources()
    private val contentResolver = ContentResolver()
    private val files = File(dataDir, "files").apply { mkdirs() }
    private val cache = File(dataDir, "cache").apply { mkdirs() }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { FilePreferences(File(dataDir, "prefs/${safeFileName(name)}.json")) }

    override fun getFilesDir(): File = files
    override fun getCacheDir(): File = cache
    override fun getPackageName(): String = packageName
    override fun getResources(): Resources = resources
    override fun getContentResolver(): ContentResolver = contentResolver
    override fun getApplicationContext(): Context = this

    companion object {
        /** What the host calls itself to extensions that ask. */
        const val PACKAGE_NAME = "app.hibiki.desktop"

        /** A preferences name becomes a file name: nothing in it may leave the directory. */
        fun safeFileName(name: String): String = name.map { if (it.isLetterOrDigit() || it in "-_.") it else '_' }.joinToString("").trimStart('.').ifEmpty { "_" }
    }
}

/** The Application extensions fetch through Injekt (settings, files); it answers as the host context. */
class HostApplication(base: HostContext) : Application() {
    init {
        attachBaseContext(base)
    }

    override fun getApplicationContext(): Context = this
}
