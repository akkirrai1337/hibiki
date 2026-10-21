package android.webkit;

import android.content.Context;

/** Settings are remembered so the page backend can apply the ones it understands (the user agent above all). */
public class WebSettings {
    public static final int MIXED_CONTENT_ALWAYS_ALLOW = 0, MIXED_CONTENT_NEVER_ALLOW = 1, MIXED_CONTENT_COMPATIBILITY_MODE = 2;

    public static final String DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36";

    public volatile String userAgent = DEFAULT_USER_AGENT;
    public volatile boolean javaScriptEnabled;
    public volatile boolean blockNetworkImage;

    public static String getDefaultUserAgent(Context context) { return DEFAULT_USER_AGENT; }

    public void setUserAgentString(String value) { userAgent = value != null ? value : DEFAULT_USER_AGENT; }

    public String getUserAgentString() { return userAgent; }

    public void setJavaScriptEnabled(boolean value) { javaScriptEnabled = value; }

    public void setBlockNetworkImage(boolean value) { blockNetworkImage = value; }

    public void setDomStorageEnabled(boolean value) {}

    public void setDatabaseEnabled(boolean value) {}

    public void setUseWideViewPort(boolean value) {}

    public void setLoadWithOverviewMode(boolean value) {}

    public void setMixedContentMode(int mode) {}
}
