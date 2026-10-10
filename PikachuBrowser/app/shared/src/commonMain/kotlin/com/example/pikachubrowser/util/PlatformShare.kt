package com.example.pikachubrowser.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberShareLauncher(): (url: String, title: String) -> Unit
