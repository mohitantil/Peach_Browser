package com.example.peachbrowser.webview

import androidx.compose.runtime.*

@Stable
class PeachWebViewController {
    var goBackAction: (() -> Unit)? = null
    var goForwardAction: (() -> Unit)? = null
    var reloadAction: (() -> Unit)? = null
    var stopLoadingAction: (() -> Unit)? = null
    var loadUrlAction: ((String) -> Unit)? = null
    var hideCustomViewAction: (() -> Unit)? = null
    var pauseMediaAction: (() -> Unit)? = null

    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var currentUrl by mutableStateOf("")
    var currentTitle by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var pageContent by mutableStateOf("")
    var isCustomViewShowing by mutableStateOf(false)

    fun pauseMedia() {
        pauseMediaAction?.invoke()
    }

    fun goBack() {
        goBackAction?.invoke()
    }

    fun goForward() {
        goForwardAction?.invoke()
    }

    fun reload() {
        reloadAction?.invoke()
    }

    fun stopLoading() {
        stopLoadingAction?.invoke()
    }

    fun hideCustomView() {
        hideCustomViewAction?.invoke()
    }

    fun loadUrl(url: String) {
        val target = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        loadUrlAction?.invoke(target)
    }

    fun reset() {
        currentUrl = ""
        currentTitle = ""
        canGoBack = false
        canGoForward = false
        isLoading = false
        progress = 0f
        pageContent = ""
        isCustomViewShowing = false
    }
}

@Composable
fun rememberPeachWebViewController(): PeachWebViewController {
    return remember { PeachWebViewController() }
}
