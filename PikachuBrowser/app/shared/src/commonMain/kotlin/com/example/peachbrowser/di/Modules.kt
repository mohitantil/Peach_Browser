package com.example.peachbrowser.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
    }
    
    single { com.example.peachbrowser.data.network.SearchApiService(get()) }
    single { com.example.peachbrowser.data.network.LlmApiService(get()) }
}

val databaseModule = module {
    single { 
        val driverFactory = get<com.example.peachbrowser.data.db.DriverFactory>()
        com.example.peachbrowser.db.PeachDatabase(driverFactory.createDriver())
    }
}

val repositoryModule = module {
    single<com.example.peachbrowser.domain.repository.SearchRepository> { 
        com.example.peachbrowser.data.repository.SearchRepositoryImpl(get()) 
    }
    single<com.example.peachbrowser.domain.repository.LlmRepository> { 
        com.example.peachbrowser.data.repository.LlmRepositoryImpl(get()) 
    }
}

val useCaseModule = module {
    factory { com.example.peachbrowser.domain.usecase.PerformSearchUseCase(get()) }
    factory { com.example.peachbrowser.domain.usecase.ChatWithLlmUseCase(get()) }
}

val viewModelModule = module {
    single { com.example.peachbrowser.presentation.viewmodel.ChatViewModel(get()) }
    single { com.example.peachbrowser.presentation.viewmodel.BrowserViewModel(getOrNull()) }
}
