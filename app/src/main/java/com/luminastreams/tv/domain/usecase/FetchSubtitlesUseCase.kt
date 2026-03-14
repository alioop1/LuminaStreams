package com.luminastreams.tv.domain.usecase

class FetchSubtitlesUseCase(private val scraper: com.luminastreams.tv.data.remote.SubtitleScraper) {
    suspend operator fun invoke(imdbId: String, lang: String): Result<ByteArray> {
        return scraper.fetchSubtitleInMemory(imdbId, lang)
    }
}
