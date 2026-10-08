package app.hibiki;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * Primitives for BROWSER-runtime player resolvers (the logic lives in src/platform/android/browser.ts,
 * ported from desktop's browserResolveHost.ts). Desktop runs the resolver script inside the embed's
 * child frame with Electron's WebFrameMain; Android WebView can only evaluate in the top frame, so
 * every frame instead gets a small document-start script that talks to native code over a
 * WebMessageListener: it announces itself ("hello") and runs fixed actions on request - the
 * resolver script, reading the HibikiResolver captures, checking a stream URL from inside the frame.
 * The resolver script is installed the same way, at document start, so no eval is needed and the
 * embed page's own CSP cannot block it.
 *
 * Also records every media request the page makes (URL and request headers) and can cancel
 * everything but the top document while a referring page loads, as desktop does.
 */
@CapacitorPlugin(name = "HibikiResolver")
public class HibikiResolverPlugin extends Plugin {

    private static final Pattern MEDIA_URL = Pattern.compile("\\.(m3u8|mpd|mp4)(\\?|#|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER_URL = Pattern.compile("cdn\\.plyr\\.io/static/blank\\.mp4", Pattern.CASE_INSENSITIVE);

    private static final class Frame {
        final int id;
        final String url;
        final String origin;
        final boolean main;
        final JavaScriptReplyProxy proxy;

        Frame(int id, String url, String origin, boolean main, JavaScriptReplyProxy proxy) {
            this.id = id;
            this.url = url;
            this.origin = origin;
            this.main = main;
            this.proxy = proxy;
        }
    }

    private static final class Page {
        WebView view;
        final List<ScriptHandler> scripts = new ArrayList<>();
        /** The resolver script's own handle, swapped when a pooled page serves another resolver. */
        ScriptHandler resolverScript;
        final List<Frame> frames = Collections.synchronizedList(new ArrayList<>());
        final List<JSObject> captures = Collections.synchronizedList(new ArrayList<>());
        final Map<String, PluginCall> pending = new HashMap<>();
        volatile boolean documentOnly;
        int nextFrameId = 1;
    }

    private final Map<String, Page> pages = new HashMap<>();

    @SuppressLint({ "SetJavaScriptEnabled", "RequiresFeature" })
    @PluginMethod
    public void open(PluginCall call) {
        String key = call.getString("key");
        String bootScript = call.getString("bootScript", "");
        String resolverScript = call.getString("resolverScript", "");
        getActivity().runOnUiThread(() -> {
            try {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                    || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    call.reject("This WebView cannot run browser resolvers (needs WEB_MESSAGE_LISTENER and DOCUMENT_START_SCRIPT)");
                    return;
                }
                destroy(pages.remove(key));
                Page page = new Page();
                WebView view = new WebView(getContext());
                page.view = view;
                WebSettings settings = view.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                // The embed's own player must be allowed to start (that is what requests the stream);
                // the boot script mutes every media element, so nothing is heard.
                settings.setMediaPlaybackRequiresUserGesture(false);
                settings.setBlockNetworkImage(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
                view.setWebViewClient(new WebViewClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (MEDIA_URL.matcher(url).find() && !PLACEHOLDER_URL.matcher(url).find()) {
                            JSObject capture = new JSObject();
                            capture.put("url", url);
                            JSObject headers = new JSObject();
                            for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) headers.put(header.getKey(), header.getValue());
                            capture.put("headers", headers);
                            page.captures.add(capture);
                        }
                        if (page.documentOnly && !request.isForMainFrame()) {
                            return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
                        }
                        return null;
                    }
                });
                Set<String> everywhere = Collections.singleton("*");
                WebViewCompat.addWebMessageListener(view, "HibikiHost", everywhere, (source, message, sourceOrigin, isMainFrame, replyProxy) -> {
                    onFrameMessage(page, message, sourceOrigin, isMainFrame, replyProxy);
                });
                page.scripts.add(WebViewCompat.addDocumentStartJavaScript(view, bootScript, everywhere));
                // Separate from the boot script: a resolver script that does not parse must not take
                // the bridge down with it (the boot script then falls back to running it with eval).
                page.resolverScript = WebViewCompat.addDocumentStartJavaScript(view, resolverScript, everywhere);

                ViewGroup parent = (ViewGroup) bridge.getWebView().getParent();
                parent.addView(view, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                pages.put(key, page);
                call.resolve();
            } catch (Exception e) {
                call.reject(String.valueOf(e), e);
            }
        });
    }

    private void onFrameMessage(Page page, WebMessageCompat message, Uri sourceOrigin, boolean isMainFrame, JavaScriptReplyProxy replyProxy) {
        String data = message.getData();
        if (data == null) return;
        try {
            JSONObject parsed = new JSONObject(data);
            String type = parsed.optString("type");
            if ("hello".equals(type)) {
                synchronized (page.frames) {
                    page.frames.add(new Frame(page.nextFrameId++, parsed.optString("url"), String.valueOf(sourceOrigin), isMainFrame, replyProxy));
                }
            } else if ("result".equals(type)) {
                PluginCall call = page.pending.remove(parsed.optString("id"));
                if (call != null) {
                    JSObject result = new JSObject();
                    result.put("raw", data);
                    call.resolve(result);
                }
            }
        } catch (Exception ignored) {
            // Not ours, or malformed: frames run third-party code that may post anything.
        }
    }

    /** Replaces the resolver script for documents loaded from now on - a pooled page reused by another resolver. */
    @SuppressLint("RequiresFeature")
    @PluginMethod
    public void setResolverScript(PluginCall call) {
        String key = call.getString("key");
        String resolverScript = call.getString("resolverScript", "");
        getActivity().runOnUiThread(() -> {
            Page page = pages.get(key);
            if (page == null) {
                call.reject("no resolver page " + key);
                return;
            }
            if (page.resolverScript != null) page.resolverScript.remove();
            page.resolverScript = WebViewCompat.addDocumentStartJavaScript(page.view, resolverScript, Collections.singleton("*"));
            page.captures.clear();
            call.resolve();
        });
    }

    @PluginMethod
    public void navigate(PluginCall call) {
        String key = call.getString("key");
        String url = call.getString("url");
        boolean documentOnly = call.getBoolean("documentOnly", false);
        JSObject headersObject = call.getObject("headers", new JSObject());
        getActivity().runOnUiThread(() -> {
            Page page = pages.get(key);
            if (page == null) {
                call.reject("no resolver page " + key);
                return;
            }
            page.documentOnly = documentOnly;
            Map<String, String> headers = new HashMap<>();
            Iterator<String> names = headersObject.keys();
            while (names.hasNext()) {
                String name = names.next();
                headers.put(name, headersObject.optString(name));
            }
            page.view.loadUrl(url, headers);
            call.resolve();
        });
    }

    @PluginMethod
    public void setDocumentOnly(PluginCall call) {
        Page page = pages.get(call.getString("key"));
        if (page != null) page.documentOnly = call.getBoolean("value", false);
        call.resolve();
    }

    /** Evaluates in the top frame (WebView's own evaluateJavascript). */
    @PluginMethod
    public void evalTop(PluginCall call) {
        String key = call.getString("key");
        String js = call.getString("js", "undefined");
        getActivity().runOnUiThread(() -> {
            Page page = pages.get(key);
            if (page == null) {
                call.reject("no resolver page " + key);
                return;
            }
            page.view.evaluateJavascript(js, value -> {
                JSObject result = new JSObject();
                result.put("value", value);
                result.put("url", page.view.getUrl());
                call.resolve(result);
            });
        });
    }

    @PluginMethod
    public void frames(PluginCall call) {
        Page page = pages.get(call.getString("key"));
        JSArray list = new JSArray();
        if (page != null) {
            synchronized (page.frames) {
                for (Frame frame : page.frames) {
                    JSObject item = new JSObject();
                    item.put("id", frame.id);
                    item.put("url", frame.url);
                    item.put("origin", frame.origin);
                    item.put("main", frame.main);
                    list.put(item);
                }
            }
        }
        JSObject result = new JSObject();
        result.put("frames", list);
        call.resolve(result);
    }

    /** Asks one frame's boot script to run an action; resolves with the frame's raw JSON reply. */
    @PluginMethod
    public void frameCall(PluginCall call) {
        String key = call.getString("key");
        int frameId = call.getInt("frameId", -1);
        String requestId = call.getString("id");
        String payload = call.getString("payload");
        getActivity().runOnUiThread(() -> {
            Page page = pages.get(key);
            Frame target = null;
            if (page != null) {
                synchronized (page.frames) {
                    for (Frame frame : page.frames) if (frame.id == frameId) target = frame;
                }
            }
            if (target == null) {
                call.reject("no such frame " + frameId);
                return;
            }
            page.pending.put(requestId, call);
            try {
                target.proxy.postMessage(payload);
            } catch (Exception e) {
                page.pending.remove(requestId);
                call.reject(String.valueOf(e), e);
            }
        });
    }

    /** Media requests seen since the last call (URL + request headers), then forgotten. */
    @PluginMethod
    public void takeCaptures(PluginCall call) {
        Page page = pages.get(call.getString("key"));
        JSArray list = new JSArray();
        if (page != null) {
            synchronized (page.captures) {
                for (JSObject capture : page.captures) list.put(capture);
                page.captures.clear();
            }
        }
        JSObject result = new JSObject();
        result.put("captures", list);
        call.resolve(result);
    }

    @PluginMethod
    public void userAgent(PluginCall call) {
        JSObject result = new JSObject();
        result.put("value", WebSettings.getDefaultUserAgent(getContext()));
        call.resolve(result);
    }

    @PluginMethod
    public void cookies(PluginCall call) {
        String value = CookieManager.getInstance().getCookie(call.getString("url"));
        JSObject result = new JSObject();
        result.put("value", value == null ? "" : value);
        call.resolve(result);
    }

    @PluginMethod
    public void close(PluginCall call) {
        String key = call.getString("key");
        getActivity().runOnUiThread(() -> {
            destroy(pages.remove(key));
            call.resolve();
        });
    }

    /** UI thread only. */
    @SuppressLint("RequiresFeature")
    private void destroy(Page page) {
        if (page == null) return;
        for (ScriptHandler script : page.scripts) script.remove();
        if (page.resolverScript != null) page.resolverScript.remove();
        for (PluginCall pending : page.pending.values()) {
            pending.reject("resolver page closed");
        }
        page.pending.clear();
        if (page.view != null) {
            WebViewCompat.removeWebMessageListener(page.view, "HibikiHost");
            ViewGroup parent = (ViewGroup) page.view.getParent();
            if (parent != null) parent.removeView(page.view);
            page.view.stopLoading();
            page.view.destroy();
        }
    }
}
