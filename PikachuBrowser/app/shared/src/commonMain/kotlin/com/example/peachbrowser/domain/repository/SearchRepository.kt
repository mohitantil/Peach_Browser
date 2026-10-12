package com.example.peachbrowser.domain.repository

import com.example.peachbrowser.domain.model.SearchQuery
import com.example.peachbrowser.domain.model.SearchResult

interface SearchRepository {
    suspend fun performSearch(query: SearchQuery): Result<List<SearchResult>>
}
