package com.example.pikachubrowser.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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

private fun loadFarblingScript(context: Context): String {
    return try {
        context.assets.open("farbling.bundle.js").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ""
    }
}

private fun loadBackgroundScript(context: Context): String {
    return try {
        context.assets.open("background_playback.bundle.js").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        """
        (function() {
            try {
                if (window.__PIKACHU_BG_PLAYBACK_ACTIVE__) return;
                window.__PIKACHU_BG_PLAYBACK_ACTIVE__ = true;
                Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
                Object.defineProperty(document, 'webkitHidden', { get: () => false, configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible', configurable: true });
                document.hasFocus = () => true;
                Document.prototype.hasFocus = () => true;
                ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'].forEach(evt => {
                    window.addEventListener(evt, e => e.stopImmediatePropagation(), true);
                    document.addEventListener(evt, e => e.stopImmediatePropagation(), true);
                });
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

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

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundPlayActive && (visibility == View.GONE || visibility == View.INVISIBLE)) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE)
            return
        }
        super.dispatchWindowVisibilityChanged(visibility)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (isBackgroundPlayActive && (visibility == View.GONE || visibility == View.INVISIBLE)) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
            return
        }
        super.onVisibilityChanged(changedView, visibility)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (isBackgroundPlayActive && !hasWindowFocus) {
            super.onWindowFocusChanged(true)
            return
        }
        super.onWindowFocusChanged(hasWindowFocus)
    }

    override fun onPause() {
        if (isBackgroundPlayActive) {
            // Do not pause video/audio decoders in background
            return
        }
        super.onPause()
    }

    override fun pauseTimers() {
        if (isBackgroundPlayActive) {
            // Keep JavaScript timers alive for media chunk streaming
            return
        }
        super.pauseTimers()
    }
}

@Composable
actual fun PikachuWebView(
    tabId: String,
    url: String,
    controller: PikachuWebViewController?,
    modifier: Modifier
) {
    val browserViewModel = koinInject<BrowserViewModel>()
    val browserState by browserViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as? Activity

    val backgroundScript = remember(context) { loadBackgroundScript(context) }
    val farblingScript = remember(context) { loadFarblingScript(context) }
    var lastLoadedUrl by remember(tabId) { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AndroidTabWebViewPool.getOrCreate(tabId, ctx) { c ->
                PikachuNativeWebView(c).apply {
                isBackgroundPlayActive = browserState.isBackgroundPlayEnabled
                isNestedScrollingEnabled = true
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = !browserState.isPrivateMode
                    databaseEnabled = !browserState.isPrivateMode
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
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
                            lastLoadedUrl = pageUrl
                            controller?.currentUrl = pageUrl
                            browserViewModel.processIntent(BrowserIntent.UrlChanged(pageUrl))
                        }
                        browserViewModel.processIntent(
                            BrowserIntent.UpdateTabNavState(
                                tabId = tabId,
                                canGoBack = view?.canGoBack() == true,
                                canGoForward = view?.canGoForward() == true,
                                url = pageUrl ?: "",
                                title = view?.title ?: ""
                            )
                        )
                        if (browserState.isFarblingEnabled && farblingScript.isNotBlank()) {
                            view?.evaluateJavascript(farblingScript, null)
                        }
                        if (browserState.isBackgroundPlayEnabled) {
                            view?.evaluateJavascript(backgroundScript, null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        controller?.isLoading = false
                        controller?.canGoBack = view?.canGoBack() == true
                        controller?.canGoForward = view?.canGoForward() == true
                        val pageTitle = view?.title ?: ""
                        if (pageUrl != null) {
                            lastLoadedUrl = pageUrl
                            controller?.currentUrl = pageUrl
                            controller?.currentTitle = pageTitle
                            browserViewModel.processIntent(BrowserIntent.PageLoaded(pageUrl, pageTitle))
                        }
                        browserViewModel.processIntent(
                            BrowserIntent.UpdateTabNavState(
                                tabId = tabId,
                                canGoBack = view?.canGoBack() == true,
                                canGoForward = view?.canGoForward() == true,
                                url = pageUrl ?: "",
                                title = pageTitle
                            )
                        )

                        if (browserState.isFarblingEnabled && farblingScript.isNotBlank()) {
                            view?.evaluateJavascript(farblingScript, null)
                        }

                        if (browserState.isBackgroundPlayEnabled) {
                            view?.evaluateJavascript(backgroundScript, null)
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

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val reqUrl = request?.url?.toString() ?: return false
                        return handleUrlLoading(view, reqUrl)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, reqUrl: String?): Boolean {
                        if (reqUrl.isNullOrBlank()) return false
                        return handleUrlLoading(view, reqUrl)
                    }

                    private fun handleUrlLoading(view: WebView?, reqUrl: String): Boolean {
                        if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                            return false
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
                            view?.context?.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return true
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

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        if (resultMsg == null || view == null) return false
                        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                        val tempWebView = WebView(view.context).apply {
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString()
                                    if (!targetUrl.isNullOrBlank()) {
                                        lastLoadedUrl = targetUrl
                                        view.loadUrl(targetUrl)
                                    }
                                    v?.destroy()
                                    return true
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(v: WebView?, targetUrl: String?): Boolean {
                                    if (!targetUrl.isNullOrBlank()) {
                                        lastLoadedUrl = targetUrl
                                        view.loadUrl(targetUrl)
                                    }
                                    v?.destroy()
                                    return true
                                }
                            }
                        }
                        transport.webView = tempWebView
                        resultMsg.sendToTarget()
                        return true
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

                // Farbling Anti-Fingerprinting Script Injection
                if (browserState.isFarblingEnabled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    try {
                        if (farblingScript.isNotBlank()) {
                            WebViewCompat.addDocumentStartJavaScript(
                                this,
                                farblingScript,
                                setOf("*")
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Background Playback Visibility Spoofing Script
                if (browserState.isBackgroundPlayEnabled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    try {
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            backgroundScript,
                            setOf("*")
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            }
        },
        update = { webView ->
            if (webView is PikachuNativeWebView) {
                webView.isBackgroundPlayActive = browserState.isBackgroundPlayEnabled
                if (browserState.isFarblingEnabled && farblingScript.isNotBlank()) {
                    webView.evaluateJavascript(farblingScript, null)
                }
                if (browserState.isBackgroundPlayEnabled) {
                    webView.evaluateJavascript(backgroundScript, null)
                }
            }

            // Sync controller states on update
            controller?.canGoBack = webView.canGoBack()
            controller?.canGoForward = webView.canGoForward()

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
            // AND does not match the URL already loaded or currently loading in the WebView!
            val currentWebUrl = webView.url
            if (url.isNotBlank() && url != lastLoadedUrl && url != currentWebUrl) {
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
