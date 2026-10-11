package com.example.pikachubrowser.webview

import androidx.compose.runtime.*

@Stable
class PikachuWebViewController {
    var goBackAction: (() -> Unit)? = null
    var goForwardAction: (() -> Unit)? = null
    var reloadAction: (() -> Unit)? = null
    var stopLoadingAction: (() -> Unit)? = null
    var loadUrlAction: ((String) -> Unit)? = null

    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var currentUrl by mutableStateOf("")
    var currentTitle by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var pageContent by mutableStateOf("")

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
    }
}

@Composable
fun rememberPikachuWebViewController(): PikachuWebViewController {
    return remember { PikachuWebViewController() }
}
