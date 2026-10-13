package com.example.peachbrowser.webview

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.mvi.DownloadItem
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel
import com.example.peachbrowser.util.SensorType
import com.example.peachbrowser.util.SessionPermissionManager
import com.example.peachbrowser.util.clearBrowserSession
import org.koin.compose.koinInject

private fun loadMediaDetectorScript(context: Context): String {
    return try {
        context.assets.open("media_detector.bundle.js").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ""
    }
}

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
                if (window.__PEACH_BG_PLAYBACK_ACTIVE__) return;
                window.__PEACH_BG_PLAYBACK_ACTIVE__ = true;
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

class PeachNativeWebView(context: Context) : WebView(context) {
    var isBackgroundPlayActive: Boolean = false
    var customView: View? = null
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var customViewContainer: FrameLayout? = null
    var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    var onFullscreenChanged: ((Boolean) -> Unit)? = null

    fun hideCustomViewIfShowing() {
        val cv = customView ?: return
        val act = context as? Activity
        val decorView = act?.window?.decorView as? ViewGroup

        customViewContainer?.let { container ->
            container.removeView(cv)
            decorView?.removeView(container)
        }

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        customViewContainer = null

        act?.let { a ->
            a.requestedOrientation = originalOrientation
            val insetsController = WindowCompat.getInsetsController(a.window, a.window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }

        onFullscreenChanged?.invoke(false)
    }

    private fun isSamePageOrRedirect(url1: String, url2: String): Boolean {
        if (url1.isBlank() || url2.isBlank()) return false
        if (url1 == url2) return true
        val clean1 = url1.removeSuffix("/").removePrefix("https://").removePrefix("http://")
        val clean2 = url2.removeSuffix("/").removePrefix("https://").removePrefix("http://")
        if (clean1 == clean2) return true
        val base1 = clean1.substringBefore("#").substringBefore("?")
        val base2 = clean2.substringBefore("#").substringBefore("?")
        if (base1 == base2) return true
        if (base1.removePrefix("m.").removePrefix("www.") == base2.removePrefix("m.").removePrefix("www.")) return true
        return false
    }

    fun canGoBackSmartly(): Boolean {
        if (!canGoBack()) return false
        val list = copyBackForwardList()
        val currIndex = list.currentIndex
        if (currIndex <= 0) return false

        val currUrl = list.getItemAtIndex(currIndex)?.url ?: ""
        var targetIndex = currIndex - 1
        while (targetIndex >= 0) {
            val targetUrl = list.getItemAtIndex(targetIndex)?.url ?: ""
            if (!isSamePageOrRedirect(currUrl, targetUrl)) {
                return true
            }
            targetIndex--
        }
        return false
    }

    fun goBackSmartly(): Boolean {
        if (!canGoBack()) return false
        val list = copyBackForwardList()
        val currIndex = list.currentIndex
        if (currIndex <= 0) return false

        val currUrl = list.getItemAtIndex(currIndex)?.url ?: ""
        var targetIndex = currIndex - 1
        while (targetIndex >= 0) {
            val targetUrl = list.getItemAtIndex(targetIndex)?.url ?: ""
            if (!isSamePageOrRedirect(currUrl, targetUrl)) {
                break
            }
            targetIndex--
        }

        if (targetIndex >= 0) {
            val steps = targetIndex - currIndex
            goBackOrForward(steps)
            return true
        }
        return false
    }

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
actual fun PeachWebView(
    tabId: String,
    url: String,
    controller: PeachWebViewController?,
    modifier: Modifier
) {
    val browserViewModel = koinInject<BrowserViewModel>()
    val browserState by browserViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as? Activity

    val backgroundScript = remember(context) { loadBackgroundScript(context) }
    val farblingScript = remember(context) { loadFarblingScript(context) }
    val mediaDetectorScript = remember(context) { loadMediaDetectorScript(context) }
    var lastLoadedUrl by remember(tabId) { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AndroidTabWebViewPool.getOrCreate(tabId, ctx) { c ->
                PeachNativeWebView(c).apply {
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
                        // Ephemerality: purge any sensor permissions granted to previous origin on navigation
                        SessionPermissionManager.clearTabPermissions(tabId)
                        val canBack = if (view is PeachNativeWebView) view.canGoBackSmartly() else (view?.canGoBack() == true)
                        controller?.isLoading = true
                        controller?.canGoBack = canBack
                        controller?.canGoForward = view?.canGoForward() == true
                        if (pageUrl != null) {
                            lastLoadedUrl = pageUrl
                            controller?.currentUrl = pageUrl
                            browserViewModel.processIntent(BrowserIntent.UrlChanged(pageUrl))
                        }
                        browserViewModel.processIntent(
                            BrowserIntent.UpdateTabNavState(
                                tabId = tabId,
                                canGoBack = canBack,
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
                        val canBack = if (view is PeachNativeWebView) view.canGoBackSmartly() else (view?.canGoBack() == true)
                        controller?.isLoading = false
                        controller?.canGoBack = canBack
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
                                canGoBack = canBack,
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

                        // Extract readable page text for Peach AI assistant
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

                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view == null) return
                        val act = context as? Activity ?: return
                        val decorView = act.window.decorView as? ViewGroup ?: return

                        if (customView != null) {
                            hideCustomViewIfShowing()
                        }

                        customView = view
                        customViewCallback = callback
                        originalOrientation = act.requestedOrientation

                        val container = FrameLayout(act).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                        customViewContainer = container
                        decorView.addView(
                            container,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )

                        val windowInsetsController = WindowCompat.getInsetsController(act.window, decorView)
                        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                        onFullscreenChanged?.invoke(true)
                    }

                    override fun onHideCustomView() {
                        hideCustomViewIfShowing()
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        if (request == null) return
                        val originStr = request.origin.toString()
                        val resources = request.resources
                        val grantedResources = mutableListOf<String>()

                        for (res in resources) {
                            if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                SessionPermissionManager.grantPermission(tabId, originStr, SensorType.MICROPHONE)
                                grantedResources.add(res)
                            } else if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                SessionPermissionManager.grantPermission(tabId, originStr, SensorType.CAMERA)
                                grantedResources.add(res)
                            }
                        }

                        if (grantedResources.isNotEmpty()) {
                            request.grant(grantedResources.toTypedArray())
                        } else {
                            request.deny()
                        }
                    }

                    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                        if (request != null) {
                            SessionPermissionManager.revokeSitePermissions(tabId, request.origin.toString())
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

                // JavaScript Bridge for reporting live audio/video state
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onMediaStateChanged(isPlaying: Boolean) {
                            browserViewModel.processIntent(BrowserIntent.SetAudioPlaying(isPlaying))
                        }
                    },
                    "PeachNativeBridge"
                )

                // Native Download Listener with notification updates
                setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                    try {
                        val guessedFileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                            setMimeType(mimetype)
                            val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                            if (!cookies.isNullOrBlank()) {
                                addRequestHeader("cookie", cookies)
                            }
                            addRequestHeader("User-Agent", userAgent)
                            setDescription("Downloading with Peach Browser...")
                            setTitle(guessedFileName)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessedFileName)
                        }
                        val downloadId = dm.enqueue(request)
                        browserViewModel.processIntent(
                            BrowserIntent.AddDownload(
                                DownloadItem(
                                    id = downloadId,
                                    fileName = guessedFileName,
                                    url = downloadUrl,
                                    progress = 0,
                                    isCompleted = false,
                                    timestamp = com.example.peachbrowser.currentTimeMillis()
                                )
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                onFullscreenChanged = { isFullscreen ->
                    controller?.isCustomViewShowing = isFullscreen
                }
                controller?.hideCustomViewAction = {
                    hideCustomViewIfShowing()
                }
                controller?.pauseMediaAction = {
                    post {
                        evaluateJavascript(
                            """
                            (function() {
                                if (window.__PEACH_PAUSE_ALL_MEDIA__) {
                                    window.__PEACH_PAUSE_ALL_MEDIA__();
                                } else {
                                    document.querySelectorAll('audio, video').forEach(function(el) {
                                        try {
                                            el.__peach_sleep_timer_paused = true;
                                            el.__peach_user_paused = true;
                                            el.pause();
                                        } catch(e) {}
                                    });
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }

                // Wire controller actions to this native WebView
                controller?.goBackAction = {
                    if (!goBackSmartly()) {
                        controller?.canGoBack = false
                        browserViewModel.processIntent(BrowserIntent.GoHome)
                    }
                }
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
                // Media Detector Script Injection
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    try {
                        if (mediaDetectorScript.isNotBlank()) {
                            WebViewCompat.addDocumentStartJavaScript(
                                this,
                                mediaDetectorScript,
                                setOf("*")
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    },
    update = { webView ->
        if (webView is PeachNativeWebView) {
                webView.onFullscreenChanged = { isFullscreen ->
                    controller?.isCustomViewShowing = isFullscreen
                }
                controller?.hideCustomViewAction = {
                    webView.hideCustomViewIfShowing()
                }
                controller?.pauseMediaAction = {
                    webView.post {
                        webView.evaluateJavascript(
                            """
                            (function() {
                                if (window.__PEACH_PAUSE_ALL_MEDIA__) {
                                    window.__PEACH_PAUSE_ALL_MEDIA__();
                                } else {
                                    document.querySelectorAll('audio, video').forEach(function(el) {
                                        try {
                                            el.__peach_sleep_timer_paused = true;
                                            el.__peach_user_paused = true;
                                            el.pause();
                                        } catch(e) {}
                                    });
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }
                webView.isBackgroundPlayActive = browserState.isBackgroundPlayEnabled
            }

            // Sync controller states on update
            val canBack = if (webView is PeachNativeWebView) webView.canGoBackSmartly() else webView.canGoBack()
            controller?.canGoBack = canBack
            controller?.canGoForward = webView.canGoForward()

            // Re-bind controller actions on update
            controller?.goBackAction = {
                if (webView is PeachNativeWebView) {
                    if (!webView.goBackSmartly()) {
                        controller?.canGoBack = false
                        browserViewModel.processIntent(BrowserIntent.GoHome)
                    }
                } else {
                    if (webView.canGoBack()) webView.goBack() else browserViewModel.processIntent(BrowserIntent.GoHome)
                }
            }
            controller?.goForwardAction = { if (webView.canGoForward()) webView.goForward() }
            controller?.reloadAction = { webView.reload() }
            controller?.stopLoadingAction = { webView.stopLoading() }
            controller?.loadUrlAction = { newUrl ->
                lastLoadedUrl = newUrl
                webView.loadUrl(newUrl)
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
            AndroidTabWebViewPool.get(tabId)?.hideCustomViewIfShowing()
            SessionPermissionManager.clearTabPermissions(tabId)
            if (activity?.isFinishing == true && (browserState.isClearSessionOnExitEnabled || browserState.isPrivateMode)) {
                clearBrowserSession()
                SessionPermissionManager.clearAllPermissions()
            }
        }
    }
}
