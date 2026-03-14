package com.luminastreams.tv.domain.usecase

import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.model.StreamSource
import com.luminastreams.tv.domain.repository.MediaRepository
import com.luminastreams.tv.data.remote.SubtitleScraper

// ── GetHomeFeedUseCase ─────────────────────────────────────────────────────
class GetHomeFeedUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(): Result<List<Movie>> = repository.getTrendingMovies()
}

// ── GetMediaDetailsUseCase ─────────────────────────────────────────────────
class GetMediaDetailsUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(movieId: String): Result<Movie> = repository.getMovieDetails(movieId)
}

// ── FetchSubtitlesUseCase ──────────────────────────────────────────────────
class FetchSubtitlesUseCase(private val scraper: SubtitleScraper) {
    suspend operator fun invoke(imdbId: String, lang: String): Result<ByteArray> =
        scraper.fetchSubtitleInMemory(imdbId, lang)
}

// ── ResolveStreamUseCase ───────────────────────────────────────────────────
class ResolveStreamUseCase {
    suspend operator fun invoke(torrentId: String): Result<StreamSource> =
        Result.failure(Exception("Not implemented yet"))
}
