package app.hibiki;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http.HttpMethod;

/**
 * HttpPort for Android: OkHttp with no cookie jar, any header allowed, bodies decompressed - the
 * transport rules src/platform/contract/http.contract.ts pins for every platform. Failures carry a
 * code (TIMEOUT, CANCELED, NETWORK) so the JS side can name them the way Node's fetch does.
 */
@CapacitorPlugin(name = "HibikiNet")
public class HibikiNetPlugin extends Plugin {

    private final Map<String, Call> inFlight = new ConcurrentHashMap<>();

    @PluginMethod
    public void request(PluginCall call) {
        String id = call.getString("id", "");
        String url = call.getString("url");
        String method = call.getString("method", "GET").toUpperCase();
        Map<String, String> headers = toMap(call.getObject("headers", new JSObject()));
        String body = call.getString("body");
        boolean follow = call.getBoolean("followRedirects", true);
        int timeoutMs = call.getInt("timeoutMs", 20000);

        Http.io.execute(() -> {
            try {
                OkHttpClient client = Http.client.newBuilder()
                    .followRedirects(follow)
                    .followSslRedirects(follow)
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build();
                Request.Builder builder = new Request.Builder().url(url);
                String contentType = null;
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    // Desktop's fetch always decompresses; OkHttp only does when it negotiated the
                    // encoding itself, so a script-set Accept-Encoding would hand back raw gzip.
                    if (header.getKey().equalsIgnoreCase("Accept-Encoding")) continue;
                    if (header.getKey().equalsIgnoreCase("Content-Type")) contentType = header.getValue();
                    builder.header(header.getKey(), header.getValue());
                }
                RequestBody requestBody = null;
                if (body != null || HttpMethod.requiresRequestBody(method)) {
                    MediaType mediaType = contentType == null ? null : MediaType.parse(contentType);
                    requestBody = RequestBody.create((body == null ? "" : body).getBytes(StandardCharsets.UTF_8), mediaType);
                }
                builder.method(method, requestBody);

                Call httpCall = client.newCall(builder.build());
                if (!id.isEmpty()) inFlight.put(id, httpCall);
                try (Response response = httpCall.execute()) {
                    JSObject responseHeaders = new JSObject();
                    for (String name : response.headers().names()) {
                        responseHeaders.put(name, new JSArray(response.headers(name)));
                    }
                    JSObject result = new JSObject();
                    result.put("status", response.code());
                    result.put("url", response.request().url().toString());
                    result.put("headers", responseHeaders);
                    result.put("body", response.body() == null ? "" : response.body().string());
                    call.resolve(result);
                }
            } catch (Exception e) {
                String message = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                String code = "Canceled".equalsIgnoreCase(message) ? "CANCELED" : e instanceof InterruptedIOException ? "TIMEOUT" : "NETWORK";
                call.reject(message, code, e);
            } finally {
                if (!id.isEmpty()) inFlight.remove(id);
            }
        });
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Call httpCall = inFlight.remove(call.getString("id", ""));
        if (httpCall != null) httpCall.cancel();
        call.resolve();
    }

    @PluginMethod
    public void registerStream(PluginCall call) {
        JSObject result = new JSObject();
        result.put("sid", StreamProxy.register(toMap(call.getObject("headers", new JSObject()))));
        call.resolve(result);
    }

    @PluginMethod
    public void unregisterStream(PluginCall call) {
        StreamProxy.unregister(call.getString("sid", ""));
        call.resolve();
    }

    @PluginMethod
    public void bridgeResolve(PluginCall call) {
        BridgeQueue.complete(call.getString("id"), call.getString("body", ""));
        call.resolve();
    }

    private static Map<String, String> toMap(JSObject object) {
        Map<String, String> map = new HashMap<>();
        if (object == null) return map;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = object.optString(key, null);
            if (value != null) map.put(key, value);
        }
        return map;
    }
}
