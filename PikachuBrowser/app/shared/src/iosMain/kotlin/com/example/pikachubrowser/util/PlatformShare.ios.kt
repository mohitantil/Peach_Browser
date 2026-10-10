package com.example.pikachubrowser.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberShareLauncher(): (url: String, title: String) -> Unit {
    return remember {
        { url, title ->
            // In iOS, share via UIActivityViewController
        }
    }
}
