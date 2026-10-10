package com.example.pikachubrowser.domain.usecase

import com.example.pikachubrowser.domain.model.SearchQuery
import com.example.pikachubrowser.domain.model.SearchResult
import com.example.pikachubrowser.domain.repository.SearchRepository

class PerformSearchUseCase(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String): Result<List<SearchResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return searchRepository.performSearch(SearchQuery(query))
    }
}
