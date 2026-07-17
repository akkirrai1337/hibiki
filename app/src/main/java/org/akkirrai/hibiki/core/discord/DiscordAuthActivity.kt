package org.akkirrai.hibiki.core.discord

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import org.akkirrai.hibiki.core.account.DiscordTokenStore

class DiscordAuthActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private val tokenStore by lazy(LazyThreadSafetyMode.NONE) { DiscordTokenStore(this) }
    private var tokenCaptured = false

    private val tokenPoll = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed && !tokenCaptured) {
                checkToken()
                webView.postDelayed(this, TOKEN_POLL_INTERVAL_MS)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppPreferencesLanguage())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = USER_AGENT
            WebView.setWebContentsDebuggingEnabled(false)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    checkToken()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    progressBar,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        resources.displayMetrics.density.toInt().coerceAtLeast(1) * 3,
                    ),
                )
            },
        )

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        if (savedInstanceState == null) {
            webView.loadUrl(DISCORD_LOGIN_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
        webView.post(tokenPoll)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeCallbacks(tokenPoll)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun checkToken() {
        webView.evaluateJavascript("window.localStorage.token") { rawResult ->
            if (tokenCaptured) return@evaluateJavascript
            val token = rawResult
                ?.replace("\\\"", "")
                ?.removeSurrounding("\"")
                ?.takeUnless { it == "null" }
                ?.trim()
                ?.takeIf { it.length >= MIN_TOKEN_LENGTH }
                ?: return@evaluateJavascript
            tokenCaptured = true
            tokenStore.saveToken(token)
            setResult(RESULT_OK)
            clearBrowserSession()
            finish()
        }
    }

    private fun clearBrowserSession() {
        webView.clearHistory()
        webView.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    private companion object {
        const val DISCORD_LOGIN_URL = "https://discord.com/login"
        const val TOKEN_POLL_INTERVAL_MS = 1_000L
        const val MIN_TOKEN_LENGTH = 20
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
    }
}
