package org.akkirrai.hibiki.core.discord

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.HibikiSettingsProvider
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import org.akkirrai.hibiki.core.account.DiscordTokenStore
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.ui.theme.HibikiTheme

class DiscordAuthActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var errorView: ComposeView
    private val appPreferences by lazy(LazyThreadSafetyMode.NONE) { AppPreferences(this) }
    private val tokenStore by lazy(LazyThreadSafetyMode.NONE) { DiscordTokenStore(this) }
    private var tokenCaptured = false
    private var pageProgress by mutableIntStateOf(0)
    private var currentUrl by mutableStateOf("")
    private var currentPageTrusted by mutableStateOf(false)

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
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = USER_AGENT
            WebView.setWebContentsDebuggingEnabled(
                applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
            webViewClient = DiscordWebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    pageProgress = newProgress.coerceIn(0, 100)
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        val addressBarView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DiscordAuthTheme(appPreferences) {
                    DiscordAddressBar(
                        pageProgress = pageProgress,
                        currentUrl = currentUrl,
                        currentPageTrusted = currentPageTrusted,
                        onClose = ::finish,
                    )
                }
            }
        }
        errorView = ComposeView(this).apply {
            visibility = View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DiscordAuthTheme(appPreferences) {
                    DiscordLoadError(onRetry = ::loadDiscordLogin)
                }
            }
        }
        val webContainer = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                errorView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    addressBarView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    webContainer,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            },
        )

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        val restored = savedInstanceState != null && webView.restoreState(savedInstanceState) != null
        webView.post {
            if (!restored || webView.url.isNullOrBlank()) loadDiscordLogin()
            webView.post(tokenPoll)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
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
        if (!isTrustedDiscordUri(Uri.parse(webView.url ?: return))) return
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

    private fun loadDiscordLogin() {
        if (::errorView.isInitialized) errorView.visibility = View.GONE
        pageProgress = 0
        webView.loadUrl(DISCORD_LOGIN_URL)
    }

    private fun clearBrowserSession() {
        webView.clearHistory()
        webView.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    private fun openExternal(uri: Uri) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure { error ->
            if (error !is ActivityNotFoundException) throw error
        }
    }

    private inner class DiscordWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            errorView.visibility = View.GONE
            updateDisplayedUrl(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            updateDisplayedUrl(url)
            checkToken()
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            super.onPageCommitVisible(view, url)
            updateDisplayedUrl(url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
            if (!request.isForMainFrame) return
            errorView.visibility = View.VISIBLE
            AppLogger.w(TAG, "Discord sign-in page failed to load: ${error.errorCode}")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame || isTrustedDiscordUri(request.url)) return false
            openExternal(request.url)
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val uri = Uri.parse(url)
            if (isTrustedDiscordUri(uri)) return false
            openExternal(uri)
            return true
        }

        private fun updateDisplayedUrl(url: String?) {
            val uri = url?.let(Uri::parse) ?: return
            currentUrl = uri.toString()
            currentPageTrusted = isTrustedDiscordUri(uri)
        }
    }

    private companion object {
        const val DISCORD_HOST = "discord.com"
        const val TAG = "DiscordAuth"
        const val DISCORD_LOGIN_URL = "https://discord.com/login"
        const val TOKEN_POLL_INTERVAL_MS = 1_000L
        const val MIN_TOKEN_LENGTH = 20
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363"

        fun isTrustedDiscordUri(uri: Uri): Boolean {
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase() ?: return false
            return host == DISCORD_HOST || host.endsWith(".$DISCORD_HOST") ||
                host == "discordapp.com" || host.endsWith(".discordapp.com")
        }
    }
}

@Composable
private fun DiscordAuthTheme(
    appPreferences: AppPreferences,
    content: @Composable () -> Unit,
) {
    HibikiSettingsProvider(appPreferences = appPreferences) {
        val preferences = LocalAppPreferencesState.current
        HibikiTheme(
            themeMode = preferences.themeMode,
            dynamicColor = preferences.useSystemColorScheme,
            amoled = preferences.useAmoledTheme,
            content = content,
        )
    }
}

@Composable
private fun DiscordAddressBar(
    pageProgress: Int,
    currentUrl: String,
    currentPageTrusted: Boolean,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.discord_auth_close),
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (currentPageTrusted) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = currentUrl.ifBlank {
                                stringResource(R.string.discord_auth_loading)
                            },
                            modifier = Modifier.padding(start = if (currentPageTrusted) 8.dp else 0.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (pageProgress in 1..99) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
    }
}

@Composable
private fun DiscordLoadError(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.discord_auth_load_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.discord_auth_retry))
            }
        }
    }
}
