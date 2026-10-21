package android.webkit;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An extension's page, for the sources that find a video by loading its player and watching the
 * requests it makes. The page itself is the host application's business (a hidden browser
 * window): a [Backend] set by the host runs it and reports back through the [WebViewClient].
 * Without a backend nothing loads and the client hears an error.
 *
 * An object added with addJavascriptInterface is reachable from the page under its name, as on
 * Android: only its @JavascriptInterface methods, called with the page's arguments. The page side is
 * the backend's (Page.addInterface); the call comes back through javascriptInterface(name).
 */
public class WebView extends View {
    public interface Backend {
        Page open(WebView view);
    }

    public interface Page {
        void loadUrl(String url, Map<String, String> headers);
        void loadData(String baseUrl, String data, String mimeType);
        void evaluate(String script, ValueCallback<String> callback);
        void stop();
        void close();
        /** Makes `name` with these methods callable from the page's scripts, from the next document on. */
        void addInterface(String name, List<String> methods);
    }

    public static volatile Backend backend;

    private final WebSettings settings = new WebSettings();
    private WebViewClient client = new WebViewClient();
    private Page page;
    private final Map<String, Object> interfaces = new LinkedHashMap<>();
    private boolean destroyed;

    public WebView(Context context) {}

    public WebSettings getSettings() { return settings; }

    public void setWebViewClient(WebViewClient client) { this.client = client; }

    public WebViewClient getWebViewClient() { return client; }

    public void addJavascriptInterface(Object object, String name) {
        if (object == null || name == null) return;
        Page page;
        synchronized (this) {
            interfaces.put(name, object);
            page = this.page;
        }
        if (page != null) page.addInterface(name, interfaceMethods(object));
    }

    public synchronized void removeJavascriptInterface(String name) {
        interfaces.remove(name);
    }

    /** The object the page calls by `name`, or null. For the backend, not part of Android's API. */
    public synchronized Object javascriptInterface(String name) {
        return interfaces.get(name);
    }

    /** The names a page may call on `object`: its @JavascriptInterface methods, as on Android. */
    public static List<String> interfaceMethods(Object object) {
        List<String> names = new ArrayList<>();
        for (Method method : object.getClass().getMethods()) {
            if (method.isAnnotationPresent(JavascriptInterface.class) && !names.contains(method.getName())) names.add(method.getName());
        }
        return names;
    }

    private Page page() {
        Page created;
        Map<String, Object> added;
        synchronized (this) {
            if (destroyed) return null;
            if (page != null || backend == null) return page;
            page = backend.open(this);
            created = page;
            added = new LinkedHashMap<>(interfaces);
        }
        // Interfaces added before the page existed go in before it loads anything.
        for (Map.Entry<String, Object> entry : added.entrySet()) created.addInterface(entry.getKey(), interfaceMethods(entry.getValue()));
        return created;
    }

    public void loadUrl(String url) { loadUrl(url, java.util.Collections.emptyMap()); }

    public void loadUrl(String url, Map<String, String> headers) {
        Page page = page();
        if (page != null) page.loadUrl(url, headers);
    }

    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        Page page = page();
        if (page != null) page.loadData(baseUrl, data, mimeType);
    }

    public void evaluateJavascript(String script, ValueCallback<String> callback) {
        Page page = page();
        if (page != null) page.evaluate(script, callback);
        else if (callback != null) callback.onReceiveValue(null);
    }

    public void stopLoading() {
        Page page;
        synchronized (this) { page = this.page; }
        if (page != null) page.stop();
    }

    public void destroy() {
        Page page;
        synchronized (this) { destroyed = true; page = this.page; this.page = null; }
        if (page != null) page.close();
    }
}
