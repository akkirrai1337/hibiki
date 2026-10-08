package app.hibiki;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

/**
 * Three jobs on top of Capacitor's client (the third: /_hibiki/image, APK sources' pictures - see
 * apk/ApkImageProxy):
 *  - serves /_hibiki/stream (the header-injecting stream proxy for the player);
 *  - serves /_hibiki/bridge/&lt;id&gt; (how extension workers make synchronous host calls: Android
 *    WebView never becomes crossOriginIsolated, so SharedArrayBuffer/Atomics are not available).
 * Runs on WebView's IO threads, so blocking here (network, waiting on the bridge) is allowed.
 */
public class HibikiWebViewClient extends BridgeWebViewClient {

    private final Bridge bridge;

    public HibikiWebViewClient(Bridge bridge) {
        super(bridge);
        this.bridge = bridge;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        boolean local = bridge.getHost().equals(url.getHost());
        String path = url.getPath() == null ? "" : url.getPath();
        if (local && path.startsWith("/_hibiki/stream")) return StreamProxy.handle(request);
        if (local && path.startsWith("/_hibiki/bridge/")) return BridgeQueue.await(path.substring("/_hibiki/bridge/".length()));
        if (local && path.equals(app.hibiki.apk.ApkImageProxy.PATH)) return app.hibiki.apk.ApkImageProxy.INSTANCE.handle(request);

        return super.shouldInterceptRequest(view, request);
    }
}
