package com.example.peachbrowser.data.repository

import com.example.peachbrowser.data.network.SearchApiService
import com.example.peachbrowser.domain.model.SearchQuery
import com.example.peachbrowser.domain.model.SearchResult
import com.example.peachbrowser.domain.repository.SearchRepository

class SearchRepositoryImpl(
    private val apiService: SearchApiService
) : SearchRepository {
    override suspend fun performSearch(query: SearchQuery): Result<List<SearchResult>> {
        return try {
            val response = apiService.search(query.query)
            val mapped = response.results.map { dto ->
                SearchResult(
                    title = dto.title,
                    url = dto.url,
                    content = dto.content ?: "",
                    engine = dto.engine ?: "unknown"
                )
            }
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
