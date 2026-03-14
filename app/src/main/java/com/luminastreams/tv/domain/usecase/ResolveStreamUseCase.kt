package com.luminastreams.tv.domain.usecase

import com.luminastreams.tv.domain.model.StreamSource

class ResolveStreamUseCase {
    suspend operator fun invoke(torrentId: String): Result<StreamSource> {
        // הלוגיקה שתקח את המזהה מ-Torrentio, תעביר ל-RealDebrid ותחזיר לינק ישיר
        return Result.failure(Exception("Not implemented yet"))
    }
}