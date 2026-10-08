package app.hibiki;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

final class Http {

    /** Same default as desktop's netFetchHost.ts; applied only when the caller set no User-Agent. */
    static final String DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /** No cookie jar on purpose: desktop's undici fetch keeps no cookies either; extensions pass them explicitly. */
    static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    /** Capacitor runs plugin methods on a single thread; network work fans out here instead. */
    static final ExecutorService io = Executors.newCachedThreadPool();

    private Http() {}
}
