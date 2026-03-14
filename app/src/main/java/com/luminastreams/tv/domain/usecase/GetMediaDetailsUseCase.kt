package com.luminastreams.tv.domain.usecase

import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.MediaRepository

class GetMediaDetailsUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(movieId: String): Result<Movie> {
        return repository.getMovieDetails(movieId)
    }
}