package com.example.peachbrowser.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(
            platformModule(),
            databaseModule,
            networkModule,
            repositoryModule,
            useCaseModule,
            viewModelModule
        )
    }

// Called by iOS, etc
fun initKoin() = initKoin {}
