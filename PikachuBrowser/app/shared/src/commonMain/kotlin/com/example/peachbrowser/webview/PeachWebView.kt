package com.example.peachbrowser.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PeachWebView(
    tabId: String = "",
    url: String,
    controller: PeachWebViewController? = null,
    modifier: Modifier = Modifier
)
