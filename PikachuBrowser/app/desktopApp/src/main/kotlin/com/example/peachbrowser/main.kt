package com.example.peachbrowser

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

import com.example.peachbrowser.di.initKoin

fun main() {
    initKoin()
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PeachBrowser",
    ) {
        App()
    }
    }
}