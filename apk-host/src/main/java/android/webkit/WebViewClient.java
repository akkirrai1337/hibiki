package android.webkit;

import android.net.http.SslError;

public class WebViewClient {
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return null; }

    public void onPageFinished(WebView view, String url) {}

    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {}

    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {}

    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { return false; }
}
