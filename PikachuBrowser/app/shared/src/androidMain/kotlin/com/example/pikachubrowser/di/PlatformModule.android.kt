package com.example.pikachubrowser.di

import com.example.pikachubrowser.data.db.DriverFactory
import org.koin.dsl.module

actual fun platformModule() = module {
    single { DriverFactory(get()) }
}
