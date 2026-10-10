package com.example.pikachubrowser.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PikachuWebView(
    url: String,
    controller: PikachuWebViewController? = null,
    modifier: Modifier = Modifier
)
