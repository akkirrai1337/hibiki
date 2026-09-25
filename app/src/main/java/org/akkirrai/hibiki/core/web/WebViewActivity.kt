package org.akkirrai.hibiki.core.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.R

/**
 * A visible WebView for the pages a source's site asks a person to deal with, mainly a Cloudflare captcha
 * that the hidden WebView of the network client cannot solve. It sends what the source itself sends
 * (its User-Agent and headers) and shares the app's cookie store, so a clearance earned here is used by the
 * source's own requests afterwards. It finishes with RESULT_OK once a page has loaded with a
 * `cf_clearance` cookie for its site, so the caller can repeat what failed.
 */
class WebViewActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var startUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startUrl = intent.getStringExtra(EXTRA_URL)?.takeIf { it.toHttpUrlOrNull() != null } ?: run {
            finish()
            return
        }
        val headers = (intent.getBundleExtra(EXTRA_HEADERS)?.let { bundle ->
            bundle.keySet().associateWith { bundle.getString(it).orEmpty() }
        } ?: emptyMap()).filterKeys { isHeaderSafe(it) }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // The clearance cookie is tied to the User-Agent it was earned with, so use the one requests use.
            settings.userAgentString = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: NetworkHelper.instance().defaultUserAgentProvider()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val host = startUrl.toHttpUrlOrNull() ?: return
                    val cleared = CookieManager.getInstance().getCookie(host.toString())
                        ?.split(";")?.any { it.trim().startsWith("cf_clearance=") } == true
                    if (cleared) setResult(Activity.RESULT_OK)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = android.view.View.GONE
        }
        fun bar(label: Int, onClick: () -> Unit) = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            setText(label)
            setTextColor(Color.WHITE)
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#14181F"))
            addView(bar(R.string.webview_close) { finish() })
            addView(bar(R.string.webview_open_browser) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView.url ?: startUrl))) }
            })
            addView(bar(R.string.webview_clear_cookies) {
                SourceWebView.clearCookies(webView.url ?: startUrl)
                Toast.makeText(this@WebViewActivity, R.string.webview_cookies_cleared, Toast.LENGTH_SHORT).show()
                webView.reload()
            })
        }
        val page = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(progressBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
        if (savedInstanceState == null) webView.loadUrl(startUrl, headers) else webView.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_HEADERS = "headers"

        // Chromium rejects a few headers with net::ERR_INVALID_ARGUMENT.
        private val UNSAFE_HEADERS = setOf(
            "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie",
        )

        private fun isHeaderSafe(name: String): Boolean {
            val lower = name.lowercase()
            return lower !in UNSAFE_HEADERS && !lower.startsWith("proxy-")
        }

        /** An intent that opens [url] with [headers]; see [SourceWebView] for opening a source's own site. */
        fun newIntent(context: Context, url: String, headers: Map<String, String> = emptyMap()): Intent =
            Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_HEADERS, Bundle().apply { headers.forEach { (k, v) -> putString(k, v) } })
            }
    }
}

/** Opening a source's site in a [WebViewActivity], and the cookie housekeeping that goes with it. */
object SourceWebView {
    /**
     * An intent to open [url] (or, when null, the source's home page) the way [sourceId] would request it, or
     * null when the source has no site to open.
     */
    fun intent(context: Context, sourceId: SourceId, url: String? = null): Intent? {
        val target = AnimeSourceRegistry.webViewTarget(sourceId) ?: return null
        return WebViewActivity.newIntent(context, url ?: target.baseUrl, target.headers)
    }

    /** Forgets every cookie held for [url]'s site, including a stale clearance; returns how many were removed. */
    fun clearCookies(url: String): Int {
        val httpUrl = url.toHttpUrlOrNull() ?: return 0
        return (NetworkHelper.instance().cookieJar as? eu.kanade.tachiyomi.network.AndroidCookieJar)?.remove(httpUrl) ?: 0
    }
}
