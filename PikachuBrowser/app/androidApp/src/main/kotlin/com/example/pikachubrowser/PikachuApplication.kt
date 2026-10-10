package com.example.pikachubrowser

import android.app.Application
import com.example.pikachubrowser.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class PikachuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        initKoin {
            androidLogger()
            androidContext(this@PikachuApplication)
        }
    }
}
