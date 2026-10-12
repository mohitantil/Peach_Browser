package com.example.peachbrowser.di

import com.example.peachbrowser.data.db.DriverFactory
import org.koin.dsl.module

actual fun platformModule() = module {
    single { DriverFactory(get()) }
}
