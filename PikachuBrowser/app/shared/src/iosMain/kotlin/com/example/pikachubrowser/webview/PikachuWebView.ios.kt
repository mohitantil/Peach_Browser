package com.example.pikachubrowser.webview

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
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
actual fun PikachuWebView(
    url: String,
    controller: PikachuWebViewController?,
    modifier: Modifier
) {
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    val wkWebView = remember {
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
        config.userContentController = userContentController
        config.allowsInlineMediaPlayback = true

        WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config).apply {
            allowsBackForwardNavigationGestures = true
            customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
        }
    }

    controller?.goBackAction = { if (wkWebView.canGoBack) wkWebView.goBack() }
    controller?.goForwardAction = { if (wkWebView.canGoForward) wkWebView.goForward() }
    controller?.reloadAction = { wkWebView.reload() }
    controller?.stopLoadingAction = { wkWebView.stopLoading() }
    controller?.loadUrlAction = { newUrl -> 
        lastLoadedUrl = newUrl
        wkWebView.loadRequest(NSURLRequest(NSURL(string = newUrl)))
    }

    UIKitView(
        factory = { wkWebView },
        modifier = modifier,
        update = { view ->
            controller?.canGoBack = view.canGoBack
            controller?.canGoForward = view.canGoForward
            if (url.isNotBlank() && url != lastLoadedUrl) {
                lastLoadedUrl = url
                val request = NSURLRequest(NSURL(string = url))
                view.loadRequest(request)
            }
        }
    )
}
