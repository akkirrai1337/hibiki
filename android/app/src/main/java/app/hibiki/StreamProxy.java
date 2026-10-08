package app.hibiki;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Request;
import okhttp3.Response;

/**
 * /_hibiki/stream?sid=SESSION&amp;u=UPSTREAM - the Android counterpart of desktop's playerHeaders.ts
 * (inject Referer/User-Agent the page itself may not set) and playerStream.ts (follow CDN redirects
 * outside the page, so the player never sees a cross-origin redirect). The final upstream URL goes
 * back as X-Hibiki-Final-Url for hls.js's relative-URL resolution.
 */
final class StreamProxy {

    private static final String TAG = "HibikiStream";
    private static final ConcurrentHashMap<String, Map<String, String>> sessions = new ConcurrentHashMap<>();

    static String register(Map<String, String> headers) {
        String sid = UUID.randomUUID().toString();
        sessions.put(sid, headers);
        return sid;
    }

    static void unregister(String sid) {
        sessions.remove(sid);
    }

    static WebResourceResponse handle(WebResourceRequest request) {
        Uri url = request.getUrl();
        String upstream = url.getQueryParameter("u");
        Map<String, String> session = sessions.get(String.valueOf(url.getQueryParameter("sid")));
        if (upstream == null || session == null) return error(400, "Bad Request", "unknown stream session");

        Request.Builder builder = new Request.Builder().url(upstream);
        boolean hasUserAgent = false;
        for (Map.Entry<String, String> header : session.entrySet()) {
            builder.header(header.getKey(), header.getValue());
            if (header.getKey().equalsIgnoreCase("User-Agent")) hasUserAgent = true;
        }
        if (!hasUserAgent) builder.header("User-Agent", Http.DEFAULT_USER_AGENT);
        for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
            if (header.getKey().equalsIgnoreCase("Range")) builder.header("Range", header.getValue());
        }

        try {
            Response response = Http.client.newCall(builder.build()).execute();
            Map<String, String> headers = new HashMap<>();
            for (String name : new String[] { "Content-Type", "Content-Length", "Content-Range", "Accept-Ranges" }) {
                String value = response.header(name);
                if (value != null) headers.put(name, value);
            }
            headers.put("X-Hibiki-Final-Url", response.request().url().toString());
            headers.put("Access-Control-Allow-Origin", "*");
            String contentType = response.header("Content-Type", "application/octet-stream");
            String mime = contentType.split(";")[0].trim();
            String reason = response.message().isEmpty() ? "OK" : response.message();
            Log.d(TAG, response.code() + " " + upstream + " -> " + response.request().url());
            return new WebResourceResponse(mime, null, response.code(), reason, headers, response.body().byteStream());
        } catch (Exception e) {
            Log.w(TAG, "failed " + upstream, e);
            return error(502, "Bad Gateway", String.valueOf(e));
        }
    }

    /** The reason also goes as X-Hibiki-Error, which the player's log line reads (VideoPlayer.tsx). */
    private static WebResourceResponse error(int code, String reason, String message) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hibiki-Error", message.replaceAll("[\r\n]+", " "));
        return new WebResourceResponse("text/plain", "utf-8", code, reason, headers,
            new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
    }

    private StreamProxy() {}
}
