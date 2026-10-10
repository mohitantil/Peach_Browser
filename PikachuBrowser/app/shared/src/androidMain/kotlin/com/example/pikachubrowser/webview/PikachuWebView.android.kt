package com.example.pikachubrowser.webview

import android.graphics.Bitmap
import android.webkit.WebChromeClient
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
import org.koin.compose.koinInject

@Composable
actual fun PikachuWebView(
    url: String,
    controller: PikachuWebViewController?,
    modifier: Modifier
) {
    val browserViewModel = koinInject<BrowserViewModel>()
    val browserState by browserViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
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

                if (browserState.isPrivateMode) {
                    android.webkit.CookieManager.getInstance().setAcceptCookie(false)
                } else {
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
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
                        val shieldJs = context.assets.open("privacy_shield.bundle.js").bufferedReader().use { it.readText() }
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            shieldJs,
                            setOf("*")
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        },
        update = { webView ->
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // If background play is enabled, let media keep playing
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
