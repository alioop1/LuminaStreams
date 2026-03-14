package com.luminastreams.tv.domain.usecase

import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.MediaRepository

class GetHomeFeedUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(): Result<List<Movie>> {
        return repository.getTrendingMovies()
    }
}