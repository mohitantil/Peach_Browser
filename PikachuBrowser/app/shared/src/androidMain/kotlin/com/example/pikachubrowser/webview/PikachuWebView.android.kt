package com.example.pikachubrowser.webview

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.pikachubrowser.presentation.mvi.BrowserIntent
import com.example.pikachubrowser.presentation.viewmodel.BrowserViewModel
import com.example.pikachubrowser.util.clearBrowserSession
import org.koin.compose.koinInject

private const val BACKGROUND_PLAY_JS = """
(function() {
    try {
        if (window.__pikachu_bg_installed) return;
        window.__pikachu_bg_installed = true;

        Object.defineProperty(document, 'hidden', {
            get: function() { return false; },
            configurable: true
        });
        Object.defineProperty(document, 'visibilityState', {
            get: function() { return 'visible'; },
            configurable: true
        });
        Object.defineProperty(document, 'webkitHidden', {
            get: function() { return false; },
            configurable: true
        });
        Object.defineProperty(document, 'webkitVisibilityState', {
            get: function() { return 'visible'; },
            configurable: true
        });

        ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'].forEach(function(evt) {
            window.addEventListener(evt, function(e) {
                e.stopImmediatePropagation();
            }, true);
            document.addEventListener(evt, function(e) {
                e.stopImmediatePropagation();
            }, true);
        });

        var originalPause = HTMLMediaElement.prototype.pause;
        HTMLMediaElement.prototype.pause = function() {
            if (document.hidden || !document.hasFocus()) {
                return;
            }
            return originalPause.apply(this, arguments);
        };
    } catch(e) {}
})();
"""

class PikachuNativeWebView(context: Context) : WebView(context) {
    var isBackgroundPlayActive: Boolean = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundPlayActive && (visibility == View.GONE || visibility == View.INVISIBLE)) {
            // Keep Chromium's media pipeline believing the window is visible so it does not suspend audio/video decoders
            super.onWindowVisibilityChanged(View.VISIBLE)
            return
        }
        super.onWindowVisibilityChanged(visibility)
    }
}

@Composable
actual fun PikachuWebView(
    url: String,
    controller: PikachuWebViewController?,
    modifier: Modifier
) {
    val browserViewModel = koinInject<BrowserViewModel>()
    val browserState by browserViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as? Activity

    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PikachuNativeWebView(ctx).apply {
                isBackgroundPlayActive = browserState.isBackgroundPlayEnabled
                isNestedScrollingEnabled = true
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = !browserState.isPrivateMode
                    databaseEnabled = !browserState.isPrivateMode
                    setSupportMultipleWindows(false)
                    allowFileAccess = false
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                // If clear session on exit or private mode is active, enforce clean session on launch
                if (browserState.isPrivateMode || browserState.isClearSessionOnExitEnabled) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().removeSessionCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    clearCache(true)
                    clearFormData()
                    clearHistory()
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        controller?.isLoading = true
                        controller?.canGoBack = view?.canGoBack() == true
                        controller?.canGoForward = view?.canGoForward() == true
                        if (pageUrl != null) {
                            controller?.currentUrl = pageUrl
                            browserViewModel.processIntent(BrowserIntent.UrlChanged(pageUrl))
                        }
                        if (browserState.isBackgroundPlayEnabled) {
                            view?.evaluateJavascript(BACKGROUND_PLAY_JS, null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        controller?.isLoading = false
                        controller?.canGoBack = view?.canGoBack() == true
                        controller?.canGoForward = view?.canGoForward() == true
                        val pageTitle = view?.title ?: ""
                        if (pageUrl != null) {
                            controller?.currentUrl = pageUrl
                            controller?.currentTitle = pageTitle
                            browserViewModel.processIntent(BrowserIntent.PageLoaded(pageUrl, pageTitle))
                        }

                        if (browserState.isBackgroundPlayEnabled) {
                            view?.evaluateJavascript(BACKGROUND_PLAY_JS, null)
                        }

                        // Extract readable page text for Pikachu AI assistant
                        view?.evaluateJavascript(
                            "(function() { return (document.title || '') + '\\n\\n' + (document.body ? document.body.innerText.substring(0, 3000) : ''); })();"
                        ) { rawResult ->
                            if (!rawResult.isNullOrBlank() && rawResult != "null") {
                                val cleanText = rawResult.trim('\"')
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"")
                                    .replace("\\t", " ")
                                controller?.pageContent = cleanText
                                browserViewModel.processIntent(BrowserIntent.UpdatePageContent(cleanText))
                            }
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        controller?.progress = newProgress / 100f
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (title != null) {
                            controller?.currentTitle = title
                        }
                    }
                }

                // Wire controller actions to this native WebView
                controller?.goBackAction = { if (canGoBack()) goBack() }
                controller?.goForwardAction = { if (canGoForward()) goForward() }
                controller?.reloadAction = { reload() }
                controller?.stopLoadingAction = { stopLoading() }
                controller?.loadUrlAction = { newUrl -> 
                    lastLoadedUrl = newUrl
                    loadUrl(newUrl) 
                }

                if (url.isNotBlank()) {
                    lastLoadedUrl = url
                    loadUrl(url)
                }

                // Privacy Shield Script Injection
                if (browserState.isAdBlockEnabled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    try {
                        val shieldJs = ctx.assets.open("privacy_shield.bundle.js").bufferedReader().use { it.readText() }
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            shieldJs,
                            setOf("*")
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Background Playback Visibility Spoofing Script
                if (browserState.isBackgroundPlayEnabled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    try {
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            BACKGROUND_PLAY_JS,
                            setOf("*")
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        },
        update = { webView ->
            if (webView is PikachuNativeWebView) {
                webView.isBackgroundPlayActive = browserState.isBackgroundPlayEnabled
            }

            // Re-bind controller actions on update
            controller?.goBackAction = { if (webView.canGoBack()) webView.goBack() }
            controller?.goForwardAction = { if (webView.canGoForward()) webView.goForward() }
            controller?.reloadAction = { webView.reload() }
            controller?.stopLoadingAction = { webView.stopLoading() }
            controller?.loadUrlAction = { newUrl ->
                lastLoadedUrl = newUrl
                webView.loadUrl(newUrl)
            }

            // Only load if the requested URL has changed from what was previously requested
            if (url.isNotBlank() && url != lastLoadedUrl) {
                lastLoadedUrl = url
                webView.loadUrl(url)
            }
        }
    )

    DisposableEffect(lifecycleOwner, browserState.isClearSessionOnExitEnabled, browserState.isPrivateMode) {
        val observer = LifecycleEventObserver { _, event ->
            // Only clear when the app activity is actually finishing/destroyed, not when temporarily minimized
            if (event == Lifecycle.Event.ON_DESTROY && activity?.isFinishing == true) {
                if (browserState.isClearSessionOnExitEnabled || browserState.isPrivateMode) {
                    clearBrowserSession()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isFinishing == true && (browserState.isClearSessionOnExitEnabled || browserState.isPrivateMode)) {
                clearBrowserSession()
            }
        }
    }
}
