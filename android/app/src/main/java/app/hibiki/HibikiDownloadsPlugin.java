package app.hibiki;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * DownloadTransferPort for Android, in two steps so the caller can look at the response before
 * deciding how to write it (append only when a resumed request got 206, as on desktop):
 * open() starts the request and answers with its status and headers; pipe() writes the body to a
 * file, reporting "progress" events; cancel() aborts either step.
 */
@CapacitorPlugin(name = "HibikiDownloads")
public class HibikiDownloadsPlugin extends Plugin {

    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    private final Map<String, Response> responses = new ConcurrentHashMap<>();

    @PluginMethod
    public void open(PluginCall call) {
        String id = call.getString("id");
        String url = call.getString("url");
        JSObject headersObject = call.getObject("headers", new JSObject());
        Http.io.execute(() -> {
            try {
                Request.Builder builder = new Request.Builder().url(url);
                Iterator<String> names = headersObject.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    builder.header(name, headersObject.optString(name));
                }
                Call httpCall = Http.client.newCall(builder.build());
                calls.put(id, httpCall);
                Response response = httpCall.execute();
                responses.put(id, response);
                JSObject responseHeaders = new JSObject();
                for (String name : response.headers().names()) responseHeaders.put(name.toLowerCase(), new JSArray(response.headers(name)));
                JSObject result = new JSObject();
                result.put("status", response.code());
                result.put("headers", responseHeaders);
                result.put("contentLength", response.body() == null ? -1 : response.body().contentLength());
                call.resolve(result);
            } catch (Exception e) {
                calls.remove(id);
                call.reject(e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void pipe(PluginCall call) {
        String id = call.getString("id");
        String filePath = call.getString("filePath");
        boolean append = call.getBoolean("append", false);
        boolean buffered = call.getBoolean("buffered", false);
        Http.io.execute(() -> {
            Response response = responses.remove(id);
            try {
                if (response == null || response.body() == null) throw new IllegalStateException("no open response " + id);
                long written = 0;
                long lastReport = 0;
                byte[] chunk = new byte[64 * 1024];
                try (InputStream in = response.body().byteStream()) {
                    if (buffered) {
                        ByteArrayOutputStream memory = new ByteArrayOutputStream();
                        int n;
                        while ((n = in.read(chunk)) != -1) memory.write(chunk, 0, n);
                        try (FileOutputStream out = new FileOutputStream(filePath, append)) {
                            memory.writeTo(out);
                        }
                        written = memory.size();
                        progress(id, written);
                    } else {
                        try (FileOutputStream out = new FileOutputStream(filePath, append)) {
                            int n;
                            while ((n = in.read(chunk)) != -1) {
                                out.write(chunk, 0, n);
                                written += n;
                                long now = System.currentTimeMillis();
                                if (now - lastReport >= 200) {
                                    lastReport = now;
                                    progress(id, written);
                                }
                            }
                        }
                        progress(id, written);
                    }
                }
                JSObject result = new JSObject();
                result.put("bytesWritten", written);
                call.resolve(result);
            } catch (Exception e) {
                call.reject(e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
            } finally {
                calls.remove(id);
                if (response != null) response.close();
            }
        });
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        String id = call.getString("id");
        Call httpCall = calls.remove(id);
        if (httpCall != null) httpCall.cancel();
        Response response = responses.remove(id);
        if (response != null) response.close();
        call.resolve();
    }

    private void progress(String id, long received) {
        JSObject event = new JSObject();
        event.put("id", id);
        event.put("received", received);
        notifyListeners("progress", event);
    }
}
