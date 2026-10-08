package app.hibiki;

import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The "xhr" transport: a worker blocks in a synchronous XHR on /_hibiki/bridge/&lt;id&gt;; the request
 * is held here until the main thread calls HibikiNet.bridgeResolve with the same id. Either side
 * may arrive first, hence computeIfAbsent on both.
 */
final class BridgeQueue {

    private static final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    static void complete(String id, String body) {
        pending.computeIfAbsent(id, k -> new CompletableFuture<>()).complete(body);
    }

    static WebResourceResponse await(String id) {
        CompletableFuture<String> future = pending.computeIfAbsent(id, k -> new CompletableFuture<>());
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        try {
            String body = future.get(120, TimeUnit.SECONDS);
            return new WebResourceResponse("application/json", "utf-8", 200, "OK", headers,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 504, "Bridge Timeout", headers,
                new ByteArrayInputStream(String.valueOf(e).getBytes(StandardCharsets.UTF_8)));
        } finally {
            pending.remove(id);
        }
    }

    private BridgeQueue() {}
}
