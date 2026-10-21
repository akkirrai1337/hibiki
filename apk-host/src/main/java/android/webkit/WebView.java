package android.webkit;

import android.content.Context;
import android.view.View;
import java.util.Map;

/**
 * An extension's page, for the sources that find a video by loading its player and watching the
 * requests it makes. The page itself is the host application's business (a hidden browser
 * window): a [Backend] set by the host runs it and reports back through the [WebViewClient].
 * Without a backend nothing loads and the client hears an error.
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
    }

    public static volatile Backend backend;

    private final WebSettings settings = new WebSettings();
    private WebViewClient client = new WebViewClient();
    private Page page;
    private boolean destroyed;

    public WebView(Context context) {}

    public WebSettings getSettings() { return settings; }

    public void setWebViewClient(WebViewClient client) { this.client = client; }

    public WebViewClient getWebViewClient() { return client; }

    public void addJavascriptInterface(Object object, String name) {}

    private synchronized Page page() {
        if (destroyed) return null;
        if (page == null && backend != null) page = backend.open(this);
        return page;
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
