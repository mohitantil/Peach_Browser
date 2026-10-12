package com.example.peachbrowser.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun PeachWebView(
    tabId: String,
    url: String,
    controller: PeachWebViewController?,
    modifier: Modifier
) {
    // Desktop KCEF implementation placeholder
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Desktop WebView Loading: $url (Tab: $tabId)")
    }
}
