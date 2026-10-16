package app.hibiki;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.brotli.BrotliInterceptor;

final class Http {

    /** Same default as desktop's netFetchHost.ts; applied only when the caller set no User-Agent. */
    static final String DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /** No cookie jar on purpose: desktop's undici fetch keeps no cookies either; extensions pass them explicitly.
     * Brotli as well as gzip: OkHttp on its own only asks for gzip, and Brotli text (HTML, JSON) comes
     * out around 15-20% smaller - the same the APK sources' client already does (NetworkHelper). */
    static final OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor.INSTANCE)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    /** Capacitor runs plugin methods on a single thread; network work fans out here instead. */
    static final ExecutorService io = Executors.newCachedThreadPool();

    private Http() {}
}
