package com.example.pikachubrowser.ui

import androidx.compose.runtime.Composable

@Composable
actual fun CommonBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop handles back via key shortcuts / mouse back button
}
