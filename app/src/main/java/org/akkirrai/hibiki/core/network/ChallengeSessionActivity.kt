package org.akkirrai.hibiki.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import org.akkirrai.beakokit.api.ChallengeSession
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import org.akkirrai.hibiki.core.log.AppLogger

class ChallengeSessionActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var requestId: String
    private lateinit var pending: PendingChallengeSession
    private var completed = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppPreferencesLanguage())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        pending = ChallengeSessionCoordinator.pending(requestId) ?: run {
            finish()
            return
        }

        val defaultUserAgent = WebSettings.getDefaultUserAgent(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = defaultUserAgent
            WebView.setWebContentsDebuggingEnabled(
                applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
            webViewClient = ChallengeWebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@ChallengeSessionActivity.progress.progress = newProgress.coerceIn(0, 100)
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24.dp, 8.dp, 8.dp, 8.dp)
            addView(
                TextView(this@ChallengeSessionActivity).apply {
                    text = getString(R.string.source_verification_title)
                    textSize = 18f
                    setTextColor(Color.WHITE)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                Button(this@ChallengeSessionActivity).apply {
                    text = getString(R.string.action_cancel)
                    setOnClickListener { finish() }
                },
            )
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(16, 20, 22))
                addView(header, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                addView(progress, LinearLayout.LayoutParams.MATCH_PARENT, 3.dp)
                addView(
                    webView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            },
        )

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(pending.request.url)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        if (!completed && !isChangingConfigurations && ::requestId.isInitialized) {
            ChallengeSessionCoordinator.cancel(requestId)
        }
        super.onDestroy()
    }

    private fun tryComplete() {
        val cookies = parseBrowserCookies(CookieManager.getInstance().getCookie(pending.request.url))
        if (!cookies.keys.containsAll(pending.request.requiredCookieNames)) return
        CookieManager.getInstance().flush()
        completed = true
        ChallengeSessionCoordinator.complete(
            requestId,
            ChallengeSession(
                cookies = cookies,
                userAgent = webView.settings.userAgentString,
            ),
        )
        finish()
    }

    private inner class ChallengeWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val allowed = isAllowedHost(request.url, Uri.parse(pending.request.url).host)
            if (!allowed) AppLogger.w(TAG, "Blocked verification redirect to ${request.url}")
            return !allowed
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            tryComplete()
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            AppLogger.w(TAG, "SSL error ${error.primaryError} on ${error.url}")
            handler.cancel()
        }
    }

    private fun isAllowedHost(uri: Uri, expectedHost: String?): Boolean {
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        val expected = expectedHost?.lowercase() ?: return false
        return host == expected || host.endsWith(".$expected")
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_REQUEST_ID = "challenge_request_id"
        private const val TAG = "ChallengeSession"
    }
}
