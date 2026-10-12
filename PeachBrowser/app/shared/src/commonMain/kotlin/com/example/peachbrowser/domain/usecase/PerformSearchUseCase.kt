package com.example.peachbrowser.domain.usecase

import com.example.peachbrowser.domain.model.SearchQuery
import com.example.peachbrowser.domain.model.SearchResult
import com.example.peachbrowser.domain.repository.SearchRepository

class PerformSearchUseCase(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String): Result<List<SearchResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return searchRepository.performSearch(SearchQuery(query))
    }
}
