package com.example.peachbrowser

import android.app.Application
import com.example.peachbrowser.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class PeachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        com.example.peachbrowser.util.AndroidContextHolder.context = this

        initKoin {
            androidLogger()
            androidContext(this@PeachApplication)
        }
    }
}
