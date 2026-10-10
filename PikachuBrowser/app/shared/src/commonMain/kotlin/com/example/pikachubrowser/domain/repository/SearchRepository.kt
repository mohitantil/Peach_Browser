package com.example.pikachubrowser.domain.repository

import com.example.pikachubrowser.domain.model.SearchQuery
import com.example.pikachubrowser.domain.model.SearchResult

interface SearchRepository {
    suspend fun performSearch(query: SearchQuery): Result<List<SearchResult>>
}
