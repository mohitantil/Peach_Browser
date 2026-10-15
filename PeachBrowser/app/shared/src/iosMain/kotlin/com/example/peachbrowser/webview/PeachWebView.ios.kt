package com.example.peachbrowser.webview

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.compose.koinInject
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PeachWebView(
    tabId: String,
    url: String,
    controller: PeachWebViewController?,
    modifier: Modifier
) {
    val browserViewModel = koinInject<BrowserViewModel>()
    val browserState by browserViewModel.state.collectAsState()
    var lastLoadedUrl by remember(tabId) { mutableStateOf<String?>(null) }
    var lastReloadTrigger by remember(tabId) { mutableStateOf(browserState.shouldReloadTrigger) }

    val wkWebView = remember(tabId) {
        IosTabWebViewPool.getOrCreate(tabId) {
            val config = WKWebViewConfiguration()
            val userContentController = WKUserContentController()

            // Session-persistent store for active OAuth/Turnstile compatibility, shredded on app exit
            config.websiteDataStore = WKWebsiteDataStore.defaultDataStore()

            // Inject Privacy Shield
            val bundlePath = NSBundle.mainBundle.pathForResource("privacy_shield.bundle", "js")
            if (bundlePath != null) {
                val shieldJs = NSString.stringWithContentsOfFile(bundlePath, NSUTF8StringEncoding, null)
                if (shieldJs != null) {
                    val userScript = WKUserScript(
                        source = shieldJs,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = false
                    )
                    userContentController.addUserScript(userScript)
                }
            }

            // Inject Background Playback Engine
            val bgPath = NSBundle.mainBundle.pathForResource("background_playback.bundle", "js")
            if (bgPath != null) {
                val bgJs = NSString.stringWithContentsOfFile(bgPath, NSUTF8StringEncoding, null)
                if (bgJs != null) {
                    val bgScript = WKUserScript(
                        source = bgJs,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = false
                    )
                    userContentController.addUserScript(bgScript)
                }
            }

            // Inject Farbling Anti-Fingerprinting Engine
            val farblingPath = NSBundle.mainBundle.pathForResource("farbling.bundle", "js")
            if (farblingPath != null) {
                val farblingJs = NSString.stringWithContentsOfFile(farblingPath, NSUTF8StringEncoding, null)
                if (farblingJs != null) {
                    val farblingScript = WKUserScript(
                        source = farblingJs,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = false
                    )
                    userContentController.addUserScript(farblingScript)
                }
            }

            // Inject Media Detector Engine
            val mediaDetectorPath = NSBundle.mainBundle.pathForResource("media_detector.bundle", "js")
            if (mediaDetectorPath != null) {
                val mdJs = NSString.stringWithContentsOfFile(mediaDetectorPath, NSUTF8StringEncoding, null)
                if (mdJs != null) {
                    val mdScript = WKUserScript(
                        source = mdJs,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                        forMainFrameOnly = false
                    )
                    userContentController.addUserScript(mdScript)
                }
            }
            config.userContentController = userContentController
            config.allowsInlineMediaPlayback = true
            config.mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone

            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
                allowsBackForwardNavigationGestures = true
            }
        }
    }

    DisposableEffect(tabId) {
        onDispose {
            com.example.peachbrowser.util.SessionPermissionManager.clearTabPermissions(tabId)
        }
    }

    controller?.goBackAction = {
        if (wkWebView.canGoBack) {
            wkWebView.goBack()
        } else {
            controller?.canGoBack = false
            browserViewModel.processIntent(BrowserIntent.GoHome)
        }
    }
    controller?.goForwardAction = { if (wkWebView.canGoForward) wkWebView.goForward() }
    controller?.reloadAction = { wkWebView.reload() }
    controller?.stopLoadingAction = { wkWebView.stopLoading() }
    controller?.pauseMediaAction = {
        wkWebView.evaluateJavaScript("if (window.__PEACH_PAUSE_ALL_MEDIA__) window.__PEACH_PAUSE_ALL_MEDIA__(); else document.querySelectorAll('video, audio').forEach(function(e){e.pause();});", null)
    }
    controller?.resumeMediaAction = {
        wkWebView.evaluateJavaScript("if (window.__PEACH_RESUME_ALL_MEDIA__) window.__PEACH_RESUME_ALL_MEDIA__(); else document.querySelectorAll('video, audio').forEach(function(e){e.play();});", null)
    }
    controller?.loadUrlAction = { newUrl ->
        lastLoadedUrl = newUrl
        wkWebView.loadRequest(NSURLRequest(NSURL(string = newUrl)))
    }

    if (url.isNotBlank() && lastLoadedUrl == null) {
        lastLoadedUrl = url
        wkWebView.loadRequest(NSURLRequest(NSURL(string = url)))
    }

    UIKitView(
        factory = { wkWebView },
        modifier = modifier,
        update = { view ->
            controller?.canGoBack = view.canGoBack
            controller?.canGoForward = view.canGoForward

            val currentHost = (view.URL?.host ?: "").lowercase()
            val isException = browserState.siteExceptions.any { currentHost == it || currentHost.endsWith(".$it") }
            val siteConfig = browserState.siteShieldOverrides.entries.find { currentHost == it.key || currentHost.endsWith(".${it.key}") }?.value
            val isShieldActive = if (isException) false else if (siteConfig != null) (siteConfig.shieldsUp && siteConfig.blockTrackersAndAds) else browserState.isAdBlockEnabled
            val isFarblingActive = if (isException) false else if (siteConfig != null) (siteConfig.shieldsUp && siteConfig.blockFingerprinting) else browserState.isFarblingEnabled
            val isBgActive = browserState.isBackgroundPlayEnabled

            view.evaluateJavaScript(
                "window.__PEACH_CONFIG__ = { shieldActive: $isShieldActive, farblingActive: $isFarblingActive, bgPlayActive: $isBgActive, isSiteException: $isException };",
                null
            )

            if (lastReloadTrigger != browserState.shouldReloadTrigger) {
                lastReloadTrigger = browserState.shouldReloadTrigger
                view.reload()
            }

            browserViewModel.processIntent(
                BrowserIntent.UpdateTabNavState(
                    tabId = tabId,
                    canGoBack = view.canGoBack,
                    canGoForward = view.canGoForward,
                    url = view.URL?.absoluteString ?: "",
                    title = view.title ?: ""
                )
            )
        }
    )
}
