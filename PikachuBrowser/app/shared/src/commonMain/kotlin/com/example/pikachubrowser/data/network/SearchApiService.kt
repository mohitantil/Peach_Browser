package com.example.pikachubrowser.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearxngResponse(
    val query: String,
    val results: List<SearxngResultDto>
)

@Serializable
data class SearxngResultDto(
    val url: String,
    val title: String,
    val content: String? = null,
    val engine: String? = null
)

class SearchApiService(private val httpClient: HttpClient) {
    // Round-robin public pool based on Pikachu architecture
    private val pool = listOf(
        "https://priv.au/search",
        "https://searx.be/search",
        "https://search.ononoki.org/search"
    )
    private var poolIndex = 0

    suspend fun search(query: String): SearxngResponse {
        val endpoint = pool[poolIndex]
        poolIndex = (poolIndex + 1) % pool.size

        return httpClient.get(endpoint) {
            parameter("q", query)
            parameter("format", "json")
        }.body()
    }
}
