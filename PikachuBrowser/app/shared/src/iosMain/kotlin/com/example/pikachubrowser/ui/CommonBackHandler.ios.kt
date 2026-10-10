package com.example.pikachubrowser.ui

import androidx.compose.runtime.Composable

@Composable
actual fun CommonBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation via native edge swipe gestures
}
