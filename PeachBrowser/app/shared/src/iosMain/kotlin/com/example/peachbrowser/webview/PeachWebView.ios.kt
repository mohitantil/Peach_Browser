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
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.NSUTF8StringEncoding
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
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
    var lastLoadedUrl by remember(tabId) { mutableStateOf<String?>(null) }

    val wkWebView = remember(tabId) {
        IosTabWebViewPool.getOrCreate(tabId) {
            val config = WKWebViewConfiguration()
            val userContentController = WKUserContentController()

            // Strict Privacy: Ephemeral Session
            config.websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()

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

            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
                allowsBackForwardNavigationGestures = true
                customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
            }
        }
    }

    DisposableEffect(tabId) {
        onDispose {
            com.example.peachbrowser.util.SessionPermissionManager.clearTabPermissions(tabId)
        }
    }

    // Rebind controller actions to this active WKWebView
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
