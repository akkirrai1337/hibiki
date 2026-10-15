package eu.kanade.tachiyomi.animesource.model

import android.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * extensions-lib 17: a local server a source serves its streams from. Its videos point at
 * [PLACEHOLDER_URL], which the host rewrites to the running server's port (Video.copyHttpServer).
 */
open class HttpServer : NanoHTTPD(0) {
    val url: String
        get() = "http://localhost:$listeningPort"

    @Volatile
    private var isRunning = false

    fun isRunning(): Boolean = isRunning

    override fun start() {
        try {
            super.start()
            isRunning = true
        } catch (e: Exception) {
            Log.d("HttpServer", "Failed to start http server", e)
        }
    }

    override fun stop() {
        super.stop()
        isRunning = false
    }

    companion object {
        const val PLACEHOLDER_URL = "http://localhost:1"
    }
}
