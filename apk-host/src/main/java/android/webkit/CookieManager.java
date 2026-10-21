package android.webkit;

import java.util.ArrayList;
import java.util.List;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

/**
 * The cookie store the host's network client and a page loaded by an extension share, like the
 * WebView one on Android: a challenge solved in a page also lets plain requests through.
 */
public class CookieManager {
    private static final CookieManager INSTANCE = new CookieManager();

    private final List<Cookie> cookies = new ArrayList<>();
    private boolean accept = true;

    public static CookieManager getInstance() { return INSTANCE; }

    public synchronized void setAcceptCookie(boolean accept) { this.accept = accept; }

    public void setAcceptThirdPartyCookies(WebView view, boolean accept) {}

    public void flush() {}

    public synchronized void setCookie(String url, String value) {
        if (!accept) return;
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return;
        Cookie cookie = Cookie.parse(parsed, value);
        if (cookie == null) return;
        long now = System.currentTimeMillis();
        cookies.removeIf(existing -> existing.expiresAt() <= now);
        // A replaced cookie keeps its place (RFC 6265 orders by creation, and a refresh is not a new cookie).
        int index = -1;
        for (int i = 0; i < cookies.size(); i++) {
            Cookie existing = cookies.get(i);
            if (existing.name().equals(cookie.name()) && existing.domain().equals(cookie.domain()) && existing.path().equals(cookie.path())) { index = i; break; }
        }
        if (cookie.expiresAt() <= now) {
            if (index >= 0) cookies.remove(index);
        } else if (index >= 0) {
            cookies.set(index, cookie);
        } else {
            cookies.add(cookie);
        }
    }

    /** The "name=value; name=value" header for [url], or null when no cookie applies. */
    public synchronized String getCookie(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return null;
        long now = System.currentTimeMillis();
        cookies.removeIf(existing -> existing.expiresAt() <= now);
        StringBuilder out = new StringBuilder();
        for (Cookie cookie : cookies) {
            if (!cookie.matches(parsed)) continue;
            if (out.length() > 0) out.append("; ");
            out.append(cookie.name()).append('=').append(cookie.value());
        }
        return out.length() == 0 ? null : out.toString();
    }

    public synchronized void removeAllCookies(ValueCallback<Boolean> callback) {
        cookies.clear();
        if (callback != null) callback.onReceiveValue(true);
    }
}
