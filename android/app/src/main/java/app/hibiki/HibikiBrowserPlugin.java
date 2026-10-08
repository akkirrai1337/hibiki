package app.hibiki;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hidden WebViews for challenge pages (and later browserFetch / BROWSER resolvers / web login) -
 * the counterpart of desktop's hidden BrowserWindows. Each one sits behind the app's WebView at
 * full size rather than being made invisible, so the page keeps running timers and animation
 * frames exactly as it would on screen.
 */
@CapacitorPlugin(name = "HibikiBrowser")
public class HibikiBrowserPlugin extends Plugin {

    private final Map<String, WebView> views = new HashMap<>();
    /** Pages currently shown to the user: Back dismisses them (and reports "closed") instead of leaving the app. */
    private final Map<String, OnBackPressedCallback> backHandlers = new HashMap<>();
    /** The browser bar around each shown page. */
    private final Map<String, Chrome> chromes = new HashMap<>();

    /**
     * A shown page is someone else's site inside the app - often a sign-in form - so it gets what a
     * browser tab has: where you are (the page's title and its address, with a lock for https), a
     * way out, reload, and a loading line. Laid out clear of the system bars and the keyboard.
     */
    private static final class Chrome {
        LinearLayout root;
        TextView title;
        TextView host;
        ImageView lock;
        ProgressBar progress;
    }

    private static final int BAR_COLOR = 0xFF141418;

    @SuppressLint("SetJavaScriptEnabled")
    @PluginMethod
    public void open(PluginCall call) {
        String key = call.getString("key");
        String url = call.getString("url");
        JSObject headersObject = call.getObject("headers", new JSObject());
        boolean clearOrigin = call.getBoolean("clearOrigin", false);
        getActivity().runOnUiThread(() -> {
            try {
                dismiss(key);

                CookieManager cookies = CookieManager.getInstance();
                if (clearOrigin) expireCookies(cookies, url);

                WebView view = new WebView(getContext());
                WebSettings settings = view.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setMediaPlaybackRequiresUserGesture(true);
                cookies.setAcceptCookie(true);
                cookies.setAcceptThirdPartyCookies(view, true);
                view.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView page, String pageUrl, Bitmap favicon) {
                        updateAddress(key, pageUrl);
                    }

                    @Override
                    public void doUpdateVisitedHistory(WebView page, String pageUrl, boolean isReload) {
                        updateAddress(key, pageUrl);
                    }
                });

                ViewGroup parent = (ViewGroup) bridge.getWebView().getParent();
                parent.addView(view, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                views.put(key, view);

                Map<String, String> headers = new HashMap<>();
                Iterator<String> names = headersObject.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    headers.put(name, headersObject.optString(name));
                }
                view.loadUrl(url, headers);
                call.resolve();
            } catch (Exception e) {
                call.reject(String.valueOf(e), e);
            }
        });
    }

    @PluginMethod
    public void eval(PluginCall call) {
        String key = call.getString("key");
        String js = call.getString("js", "undefined");
        getActivity().runOnUiThread(() -> {
            WebView view = views.get(key);
            if (view == null) {
                call.reject("no hidden page " + key);
                return;
            }
            view.evaluateJavascript(js, value -> {
                JSObject result = new JSObject();
                result.put("value", value);
                call.resolve(result);
            });
        });
    }

    /** Brings a hidden page in front of the app, for a page that needs a person (captcha, sign-in). */
    @PluginMethod
    public void show(PluginCall call) {
        String key = call.getString("key");
        getActivity().runOnUiThread(() -> {
            WebView view = views.get(key);
            if (view == null) {
                call.reject("no hidden page " + key);
                return;
            }
            if (!chromes.containsKey(key)) attachChrome(key, view);
            if (!backHandlers.containsKey(key)) {
                OnBackPressedCallback back = new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (view.canGoBack()) {
                            view.goBack();
                            return;
                        }
                        closeByUser(key);
                    }
                };
                backHandlers.put(key, back);
                getActivity().getOnBackPressedDispatcher().addCallback(back);
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void cookies(PluginCall call) {
        String value = CookieManager.getInstance().getCookie(call.getString("url"));
        JSObject result = new JSObject();
        result.put("value", value == null ? "" : value);
        call.resolve(result);
    }

    @PluginMethod
    public void userAgent(PluginCall call) {
        JSObject result = new JSObject();
        result.put("value", WebSettings.getDefaultUserAgent(getContext()));
        call.resolve(result);
    }

    @PluginMethod
    public void close(PluginCall call) {
        String key = call.getString("key");
        getActivity().runOnUiThread(() -> {
            dismiss(key);
            call.resolve();
        });
    }

    /** The person closed the page themselves (Back on its first page, or the bar's close button). */
    private void closeByUser(String key) {
        dismiss(key);
        JSObject event = new JSObject();
        event.put("key", key);
        notifyListeners("closed", event);
    }

    /** UI thread only. */
    private void dismiss(String key) {
        OnBackPressedCallback back = backHandlers.remove(key);
        if (back != null) back.remove();
        WebView view = views.remove(key);
        if (view != null) destroy(view);
        Chrome chrome = chromes.remove(key);
        if (chrome != null && chrome.root.getParent() != null) ((ViewGroup) chrome.root.getParent()).removeView(chrome.root);
    }

    /** UI thread only: moves the page from behind the app into a browser layout in front of it. */
    private void attachChrome(String key, WebView view) {
        Context context = getContext();
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent == null) return;
        parent.removeView(view);

        Chrome chrome = new Chrome();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BAR_COLOR);
        root.setClickable(true); // the app behind it takes no taps

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);

        ImageButton close = iconButton(R.drawable.ic_browser_close);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> closeByUser(key));
        bar.addView(close);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(8), 0, dp(8), 0);
        chrome.title = new TextView(context);
        chrome.title.setTextColor(Color.WHITE);
        chrome.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chrome.title.setTypeface(Typeface.DEFAULT_BOLD);
        chrome.title.setSingleLine(true);
        chrome.title.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(chrome.title);
        LinearLayout address = new LinearLayout(context);
        address.setOrientation(LinearLayout.HORIZONTAL);
        address.setGravity(Gravity.CENTER_VERTICAL);
        chrome.lock = new ImageView(context);
        chrome.lock.setImageResource(R.drawable.ic_browser_lock);
        LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        lockParams.setMarginEnd(dp(4));
        address.addView(chrome.lock, lockParams);
        chrome.host = new TextView(context);
        chrome.host.setTextColor(0xFFA1A1AA);
        chrome.host.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        chrome.host.setSingleLine(true);
        chrome.host.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        address.addView(chrome.host);
        text.addView(address);
        bar.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton reload = iconButton(R.drawable.ic_browser_reload);
        reload.setContentDescription("Reload");
        reload.setOnClickListener(v -> view.reload());
        bar.addView(reload);

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        chrome.progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        chrome.progress.setMax(100);
        chrome.progress.setIndeterminate(false);
        chrome.progress.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        chrome.progress.setProgressBackgroundTintList(ColorStateList.valueOf(BAR_COLOR));
        root.addView(chrome.progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        parent.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The app draws edge to edge, so the bar keeps itself below the status bar, and the page
        // ends above the navigation bar - or above the keyboard while one is open, so the field
        // being typed into stays on screen.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);

        // Only once shown: a hidden page has no chrome client, so its alerts never pop up over the app.
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView page, String title) {
                updateTitle(key, title);
            }

            @Override
            public void onProgressChanged(WebView page, int progress) {
                Chrome current = chromes.get(key);
                if (current == null) return;
                current.progress.setProgress(progress);
                current.progress.setVisibility(progress >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });

        chrome.root = root;
        chromes.put(key, chrome);
        updateAddress(key, view.getUrl());
        updateTitle(key, view.getTitle());
        chrome.progress.setProgress(view.getProgress());
        chrome.progress.setVisibility(view.getProgress() >= 100 ? View.INVISIBLE : View.VISIBLE);
    }

    private void updateAddress(String key, String url) {
        Chrome chrome = chromes.get(key);
        if (chrome == null || url == null) return;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        chrome.host.setText(host != null ? host : url);
        chrome.lock.setVisibility("https".equalsIgnoreCase(uri.getScheme()) ? View.VISIBLE : View.GONE);
    }

    /** A page without a title of its own (or one that is just its address) shows its site instead. */
    private void updateTitle(String key, String title) {
        Chrome chrome = chromes.get(key);
        if (chrome == null) return;
        boolean useful = title != null && !title.trim().isEmpty() && !title.startsWith("http");
        chrome.title.setText(useful ? title.trim() : chrome.host.getText());
    }

    private ImageButton iconButton(int drawable) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(drawable);
        TypedValue ripple = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        button.setBackgroundResource(ripple.resourceId);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private int dp(float value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private static void destroy(WebView view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
        view.stopLoading();
        view.destroy();
    }

    /** CookieManager has no per-origin clear; expire each cookie the URL currently sees instead. */
    private static void expireCookies(CookieManager cookies, String url) {
        String current = cookies.getCookie(url);
        if (current == null) return;
        for (String part : current.split(";")) {
            int index = part.indexOf('=');
            if (index <= 0) continue;
            cookies.setCookie(url, part.substring(0, index).trim() + "=; Max-Age=0");
        }
        cookies.flush();
    }
}
