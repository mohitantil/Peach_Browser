package com.example.pikachubrowser

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

import com.example.pikachubrowser.di.initKoin

fun main() {
    initKoin()
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PikachuBrowser",
    ) {
        App()
    }
    }
}