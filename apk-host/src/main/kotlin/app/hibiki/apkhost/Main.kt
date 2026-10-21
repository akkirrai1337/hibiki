package app.hibiki.apkhost

import app.hibiki.apk.ApkImageProxy
import java.io.File
import kotlin.system.exitProcess

/**
 * The host process the application starts on demand:
 *
 *   --data <dir>          where extensions, settings and caches live
 *   --token <secret>      every request must carry it (see Server)
 *   --image-base <url>    what the page loads pictures from; the host's picture addresses start with it
 *
 * Once listening it prints `READY <port>` on one line of stdout; everything else goes to stderr.
 * It exits when its parent closes its stdin (the application quit or crashed).
 */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { (key, value) -> key to value }
    val data = File(options["--data"] ?: error("--data is required"))
    val token = options["--token"] ?: error("--token is required")
    options["--image-base"]?.let { ApkImageProxy.base = it }

    val host = ApkHost(data)
    val pages = RemotePages()
    android.webkit.WebView.backend = pages
    val server = Server(host, token, pages)
    server.start()
    println("READY ${server.port}")
    System.out.flush()

    Thread {
        // Blocks until the parent goes away: no stray process is left holding a port and the extension files.
        while (System.`in`.read() >= 0) { /* nothing is expected on stdin */ }
        server.stop()
        exitProcess(0)
    }.apply { isDaemon = false; name = "parent-watch" }.start()
}
