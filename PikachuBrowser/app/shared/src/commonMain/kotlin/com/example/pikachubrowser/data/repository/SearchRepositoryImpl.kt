package com.example.pikachubrowser.data.repository

import com.example.pikachubrowser.data.network.SearchApiService
import com.example.pikachubrowser.domain.model.SearchQuery
import com.example.pikachubrowser.domain.model.SearchResult
import com.example.pikachubrowser.domain.repository.SearchRepository

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
