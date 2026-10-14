package com.example.peachbrowser.webview

import android.Manifest
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
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.mvi.DownloadItem
import com.example.peachbrowser.presentation.mvi.SiteShieldConfig
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel
import com.example.peachbrowser.util.SessionPermissionManager
import com.example.peachbrowser.util.UrlSanitizer
import com.example.peachbrowser.util.clearBrowserSession
import org.koin.compose.koinInject
import java.io.ByteArrayInputStream

private val ESSENTIAL_STATIC_AND_SPA_DOMAINS = setOf(
    "gstatic.com",
    "oaiusercontent.com",
    "oaistatic.com",
    "ytimg.com",
    "googleapis.com",
    "googlevideo.com",
    "ggpht.com",
    "googleusercontent.com",
    "cloudflare.com",
    "challenges.cloudflare.com",
    "alicdn.com",
    "aliyun.com",
    "chatgpt.com",
    "openai.com",
    "qwen.ai",
    "qwenlm.ai",
    "z.ai",
    "gemini.google.com",
    "aistudio.google.com",
    "music.youtube.com",
    "youtube.com",
    "youtu.be"
)

private val BLOCKED_TRACKER_DOMAINS = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "google-analytics.com",
    "googletagmanager.com",
    "taboola.com",
    "outbrain.com",
    "scorecardresearch.com",
    "criteo.com",
    "criteo.net",
    "adnxs.com",
    "adsrvr.org",
    "moatads.com",
    "quantserve.com"
)

private fun isDomainMatching(host: String, domainSet: Set<String>): Boolean {
    if (host.isBlank()) return false
    val clean = host.lowercase()
    return domainSet.any { clean == it || clean.endsWith(".$it") }
}

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
        ""
    }
}

class PeachNativeWebView(context: Context) : WebView(context) {
    @Volatile var isBackgroundPlayActive: Boolean = false
    @Volatile var isShieldEnabled: Boolean = true
    @Volatile var isFarblingEnabled: Boolean = true
    @Volatile var isTrackingProtectionEnabled: Boolean = true
    @Volatile var isDeAmpEnabled: Boolean = true
    @Volatile var siteShieldOverrides: Map<String, SiteShieldConfig> = emptyMap()
    @Volatile var appliedReloadTrigger: Int = 0

    var customView: View? = null
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var customViewContainer: FrameLayout? = null
    var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    var onFullscreenChanged: ((Boolean) -> Unit)? = null
    var onPermissionRequestHandler: ((PermissionRequest) -> Unit)? = null
    var onPermissionRequestCanceledHandler: ((PermissionRequest) -> Unit)? = null

    fun isShieldActiveForHost(host: String): Boolean {
        val cleanHost = host.trim().lowercase()
        val override = siteShieldOverrides.entries.find {
            cleanHost == it.key || cleanHost.endsWith(".${it.key}")
        }?.value
        return if (override != null) {
            override.shieldsUp && override.blockTrackersAndAds
        } else {
            isShieldEnabled
        }
    }

    fun isFarblingActiveForHost(host: String): Boolean {
        val cleanHost = host.trim().lowercase()
        val override = siteShieldOverrides.entries.find {
            cleanHost == it.key || cleanHost.endsWith(".${it.key}")
        }?.value
        return if (override != null) {
            override.shieldsUp && override.blockFingerprinting
        } else {
            isFarblingEnabled
        }
    }

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

    override fun onPause() {
        if (isBackgroundPlayActive) {
            return
        }
        super.onPause()
    }

    override fun pauseTimers() {
        if (isBackgroundPlayActive) {
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
    val audioFocusManager = koinInject<com.example.peachbrowser.util.PeachAudioFocusManager>()

    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val pendingReq = pendingPermissionRequest
        if (pendingReq != null) {
            val allGranted = permissions.isNotEmpty() && permissions.entries.all { it.value }
            if (allGranted) {
                val originStr = pendingReq.origin.toString()
                val resources = pendingReq.resources
                SessionPermissionManager.grantResources(tabId, originStr, resources)
                try {
                    pendingReq.grant(resources)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    pendingReq.deny()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            pendingPermissionRequest = null
        }
    }

    val handlePermissionRequest: (PermissionRequest) -> Unit = { request ->
        val originStr = request.origin.toString()
        if (SessionPermissionManager.hasAllResources(tabId, originStr, request.resources)) {
            try {
                request.grant(request.resources)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            pendingPermissionRequest = request
            val requiredOsPermissions = mutableListOf<String>()
            for (res in request.resources) {
                if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                    requiredOsPermissions.add(Manifest.permission.CAMERA)
                } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                    requiredOsPermissions.add(Manifest.permission.RECORD_AUDIO)
                }
            }

            if (requiredOsPermissions.isNotEmpty()) {
                permissionLauncher.launch(requiredOsPermissions.toTypedArray())
            } else {
                SessionPermissionManager.grantResources(tabId, originStr, request.resources)
                try {
                    request.grant(request.resources)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                pendingPermissionRequest = null
            }
        }
    }

    val handlePermissionRequestCanceled: (PermissionRequest) -> Unit = { request ->
        if (pendingPermissionRequest == request) {
            pendingPermissionRequest = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AndroidTabWebViewPool.getOrCreate(tabId, ctx) { c ->
                PeachNativeWebView(c).apply {
                    onPermissionRequestHandler = handlePermissionRequest
                    onPermissionRequestCanceledHandler = handlePermissionRequestCanceled
                    isBackgroundPlayActive = browserState.isBackgroundPlayEnabled
                    isShieldEnabled = browserState.isAdBlockEnabled
                    isFarblingEnabled = browserState.isFarblingEnabled
                    isTrackingProtectionEnabled = browserState.isTrackingProtectionEnabled
                    isDeAmpEnabled = browserState.isDeAmpEnabled
                    siteShieldOverrides = browserState.siteShieldOverrides
                    appliedReloadTrigger = browserState.shouldReloadTrigger
                    isNestedScrollingEnabled = true

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true
                        allowFileAccess = false
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        val defaultUa = userAgentString ?: ""
                        userAgentString = defaultUa
                            .replace("; wv)", ")")
                            .replace("; wv;", ";")
                            .replace("; wv", "")
                            .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
                    }

                    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                        try {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    try {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUri = request?.url ?: return super.shouldInterceptRequest(view, request)
                            val reqHost = (reqUri.host ?: "").lowercase()
                            if (reqHost.isBlank()) return super.shouldInterceptRequest(view, request)

                            if (isDomainMatching(reqHost, ESSENTIAL_STATIC_AND_SPA_DOMAINS)) {
                                return super.shouldInterceptRequest(view, request)
                            }

                            val nativeView = view as? PeachNativeWebView
                            val shieldActive = nativeView?.isShieldEnabled ?: true
                            if (shieldActive && isDomainMatching(reqHost, BLOCKED_TRACKER_DOMAINS)) {
                                return WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, pageUrl, favicon)
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
                            val currentHost = try { Uri.parse(pageUrl ?: "").host ?: "" } catch (e: Exception) { "" }
                            val isShieldActive = isShieldActiveForHost(currentHost)
                            val isFarblingActive = isFarblingActiveForHost(currentHost)
                            val isBgActive = isBackgroundPlayActive

                            view?.evaluateJavascript(
                                "window.__PEACH_CONFIG__ = { shieldActive: $isShieldActive, farblingActive: $isFarblingActive, bgPlayActive: $isBgActive };",
                                null
                            )

                            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                if (isFarblingActive && farblingScript.isNotBlank()) {
                                    view?.evaluateJavascript(farblingScript, null)
                                }
                                if (isBgActive && backgroundScript.isNotBlank()) {
                                    view?.evaluateJavascript(backgroundScript, null)
                                }
                                if (mediaDetectorScript.isNotBlank()) {
                                    view?.evaluateJavascript(mediaDetectorScript, null)
                                }
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

                            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                val currentHost = try { Uri.parse(pageUrl ?: "").host ?: "" } catch (e: Exception) { "" }
                                if (isFarblingActiveForHost(currentHost) && farblingScript.isNotBlank()) {
                                    view?.evaluateJavascript(farblingScript, null)
                                }
                                if (isBackgroundPlayActive && backgroundScript.isNotBlank()) {
                                    view?.evaluateJavascript(backgroundScript, null)
                                }
                                if (mediaDetectorScript.isNotBlank()) {
                                    view?.evaluateJavascript(mediaDetectorScript, null)
                                }
                            }

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
                                var targetUrl = reqUrl
                                if (isDeAmpEnabled) {
                                    targetUrl = UrlSanitizer.deAmp(targetUrl)
                                }
                                if (isTrackingProtectionEnabled) {
                                    targetUrl = UrlSanitizer.stripTrackingParams(targetUrl)
                                }
                                if (targetUrl != reqUrl) {
                                    view?.loadUrl(targetUrl)
                                    return true
                                }
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
                            val handler = onPermissionRequestHandler
                            if (handler != null) {
                                handler(request)
                            } else {
                                request.deny()
                            }
                        }

                        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                            if (request != null) {
                                onPermissionRequestCanceledHandler?.invoke(request)
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

                    val nativeWebView = this
                    addJavascriptInterface(
                        object {
                            @android.webkit.JavascriptInterface
                            fun onMediaStateChanged(isPlaying: Boolean) {
                                post {
                                    if (isPlaying) {
                                        audioFocusManager.requestAudioFocus()
                                    } else {
                                        audioFocusManager.abandonAudioFocus()
                                    }
                                    browserViewModel.processIntent(BrowserIntent.SetAudioPlaying(isPlaying))
                                }
                            }

                            @android.webkit.JavascriptInterface
                            fun isShieldActive(host: String): Boolean {
                                return nativeWebView.isShieldActiveForHost(host)
                            }

                            @android.webkit.JavascriptInterface
                            fun isFarblingActive(host: String): Boolean {
                                return nativeWebView.isFarblingActiveForHost(host)
                            }

                            @android.webkit.JavascriptInterface
                            fun isBackgroundPlayActive(): Boolean {
                                return nativeWebView.isBackgroundPlayActive
                            }
                        },
                        "PeachNativeBridge"
                    )

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
                        AndroidTabWebViewPool.pauseAllMedia()
                    }
                    controller?.resumeMediaAction = {
                        AndroidTabWebViewPool.resumeActiveMedia()
                    }

                    controller?.goBackAction = {
                        if (!goBackSmartly()) {
                            controller.canGoBack = false
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

                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        try {
                            val shieldJs = ctx.assets.open("privacy_shield.bundle.js").bufferedReader().use { it.readText() }
                            WebViewCompat.addDocumentStartJavaScript(this, shieldJs, setOf("*"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        try {
                            if (farblingScript.isNotBlank()) {
                                WebViewCompat.addDocumentStartJavaScript(this, farblingScript, setOf("*"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        try {
                            if (backgroundScript.isNotBlank()) {
                                WebViewCompat.addDocumentStartJavaScript(this, backgroundScript, setOf("*"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        try {
                            if (mediaDetectorScript.isNotBlank()) {
                                WebViewCompat.addDocumentStartJavaScript(this, mediaDetectorScript, setOf("*"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (url.isNotBlank()) {
                        lastLoadedUrl = url
                        loadUrl(url)
                    }
                }
            }
        },
        update = { webView ->
            webView.onPermissionRequestHandler = handlePermissionRequest
            webView.onPermissionRequestCanceledHandler = handlePermissionRequestCanceled
            webView.onFullscreenChanged = { isFullscreen ->
                controller?.isCustomViewShowing = isFullscreen
            }
            controller?.hideCustomViewAction = {
                webView.hideCustomViewIfShowing()
            }
            controller?.pauseMediaAction = {
                AndroidTabWebViewPool.pauseAllMedia()
            }
            controller?.resumeMediaAction = {
                AndroidTabWebViewPool.resumeActiveMedia()
            }
            audioFocusManager.onPauseRequested = {
                controller?.pauseMedia()
            }
            audioFocusManager.onResumeRequested = {
                controller?.resumeMedia()
            }

            // Always sync live volatile settings onto PeachNativeWebView BEFORE checking reload trigger
            webView.isBackgroundPlayActive = browserState.isBackgroundPlayEnabled
            webView.isShieldEnabled = browserState.isAdBlockEnabled
            webView.isFarblingEnabled = browserState.isFarblingEnabled
            webView.isTrackingProtectionEnabled = browserState.isTrackingProtectionEnabled
            webView.isDeAmpEnabled = browserState.isDeAmpEnabled
            webView.siteShieldOverrides = browserState.siteShieldOverrides

            if (webView.isBackgroundPlayActive) {
                webView.resumeTimers()
            }

            // Per-site shield settings & JavaScript blocking
            val currentHost = try {
                Uri.parse(webView.url ?: "").host ?: ""
            } catch (e: Exception) { "" }
            val siteConfig = browserState.siteShieldOverrides.entries.find {
                currentHost == it.key || currentHost.endsWith(".${it.key}")
            }?.value
            val shouldBlockScripts = (siteConfig?.shieldsUp == true && siteConfig.blockScripts)
            webView.settings.javaScriptEnabled = !shouldBlockScripts
            webView.settings.domStorageEnabled = true
            webView.settings.databaseEnabled = true

            // Trap 1 Fix: Compare against webView.appliedReloadTrigger which survives navigation
            if (webView.appliedReloadTrigger != browserState.shouldReloadTrigger) {
                webView.appliedReloadTrigger = browserState.shouldReloadTrigger
                val isShieldActive = webView.isShieldActiveForHost(currentHost)
                val isFarblingActive = webView.isFarblingActiveForHost(currentHost)
                val isBgActive = webView.isBackgroundPlayActive
                webView.evaluateJavascript(
                    "window.__PEACH_CONFIG__ = { shieldActive: $isShieldActive, farblingActive: $isFarblingActive, bgPlayActive: $isBgActive };",
                    null
                )
                val targetReloadUrl = browserState.currentUrl
                if (targetReloadUrl.isNotBlank() && targetReloadUrl != webView.url) {
                    webView.loadUrl(targetReloadUrl)
                } else {
                    webView.reload()
                }
            }

            val canBack = webView.canGoBackSmartly()
            controller?.canGoBack = canBack
            controller?.canGoForward = webView.canGoForward()

            controller?.goBackAction = {
                if (!webView.goBackSmartly()) {
                    controller.canGoBack = false
                    browserViewModel.processIntent(BrowserIntent.GoHome)
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
            try {
                pendingPermissionRequest?.deny()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            pendingPermissionRequest = null
            SessionPermissionManager.clearTabPermissions(tabId)
            if (activity?.isFinishing == true && (browserState.isClearSessionOnExitEnabled || browserState.isPrivateMode)) {
                clearBrowserSession()
                SessionPermissionManager.clearAllPermissions()
            }
        }
    }
}
