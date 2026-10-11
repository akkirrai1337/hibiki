package app.hibiki

import okhttp3.Interceptor
import okhttp3.Response
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * What the page's pictures (WebImageProxy, apk/ApkImageProxy) cost this session: how many came from
 * the network and how many bytes that was, how many from the disk cache. The page writes it to the
 * log (platform/android/bootstrap.ts), so a log says whether posters are what eats the traffic.
 */
object ImageStats {
    private val network = AtomicLong()
    private val revalidated = AtomicLong()
    private val cached = AtomicLong()
    private val failed = AtomicLong()
    private val networkBytes = AtomicLong()

    /** Counts [response] and hands back its body, counting the bytes read from the network too. */
    fun track(response: Response): InputStream {
        val body = response.body.byteStream()
        if (!response.isSuccessful) {
            failed.incrementAndGet()
            return body
        }
        val fromNetwork = response.networkResponse
        when {
            fromNetwork == null -> cached.incrementAndGet()
            // A 304: the cached copy was confirmed, only headers crossed the network.
            response.cacheResponse != null -> revalidated.incrementAndGet()
            else -> network.incrementAndGet()
        }
        if (fromNetwork == null || response.cacheResponse != null) return body
        return object : FilterInputStream(body) {
            override fun read(): Int = super.read().also { if (it >= 0) networkBytes.incrementAndGet() }
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, len).also { if (it > 0) networkBytes.addAndGet(it.toLong()) }
        }
    }

    fun failure() {
        failed.incrementAndGet()
    }

    fun snapshot(): Map<String, Long> = mapOf(
        "network" to network.get(),
        "networkBytes" to networkBytes.get(),
        "revalidated" to revalidated.get(),
        "cached" to cached.get(),
        "failed" to failed.get(),
    )

    /**
     * Keeps a picture on disk for at least a week, whatever its server said. Some send max-age=3600
     * (AniLiberty) or 4 hours (AniKappa), and every poster older than that was asked about again on
     * the next screen that showed it - a round trip each, for a picture that never changes. Posters
     * that do change get a new address, so a week-old copy is never the wrong picture.
     */
    val keepPictures = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val type = response.header("Content-Type").orEmpty()
        val maxAge = Regex("max-age=(\\d+)").find(response.header("Cache-Control").orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 0
        if (response.code != 200 || !type.startsWith("image/") || maxAge >= MIN_KEEP_SECONDS) response
        else response.newBuilder()
            .header("Cache-Control", "max-age=$MIN_KEEP_SECONDS")
            .removeHeader("Expires")
            .removeHeader("Pragma")
            .build()
    }

    private const val MIN_KEEP_SECONDS = 7L * 24 * 60 * 60
}
