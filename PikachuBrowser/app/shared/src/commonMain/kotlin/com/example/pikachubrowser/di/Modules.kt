package com.example.pikachubrowser.di

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
    
    single { com.example.pikachubrowser.data.network.SearchApiService(get()) }
    single { com.example.pikachubrowser.data.network.LlmApiService(get()) }
}

val databaseModule = module {
    single { 
        val driverFactory = get<com.example.pikachubrowser.data.db.DriverFactory>()
        com.example.pikachubrowser.db.PikachuDatabase(driverFactory.createDriver())
    }
}

val repositoryModule = module {
    single<com.example.pikachubrowser.domain.repository.SearchRepository> { 
        com.example.pikachubrowser.data.repository.SearchRepositoryImpl(get()) 
    }
    single<com.example.pikachubrowser.domain.repository.LlmRepository> { 
        com.example.pikachubrowser.data.repository.LlmRepositoryImpl(get()) 
    }
}

val useCaseModule = module {
    factory { com.example.pikachubrowser.domain.usecase.PerformSearchUseCase(get()) }
    factory { com.example.pikachubrowser.domain.usecase.ChatWithLlmUseCase(get()) }
}

val viewModelModule = module {
    single { com.example.pikachubrowser.presentation.viewmodel.ChatViewModel(get()) }
    single { com.example.pikachubrowser.presentation.viewmodel.BrowserViewModel(getOrNull()) }
}
