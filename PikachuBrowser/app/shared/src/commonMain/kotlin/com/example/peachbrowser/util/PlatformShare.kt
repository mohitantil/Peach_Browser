package com.example.peachbrowser.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberShareLauncher(): (url: String, title: String) -> Unit
