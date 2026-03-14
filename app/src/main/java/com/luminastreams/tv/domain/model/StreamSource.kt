package com.luminastreams.tv.domain.model

data class StreamSource(
    val id: String,
    val groupName: String,
    val filename: String,
    val sizeGb: Double,
    val seeders: Int,
    val resolution: String,
    val codec: String,
    val audioFormat: String,
    val isCached: Boolean,
    val isDV: Boolean,
    val isHDR10: Boolean,
    val hasBuiltInSubs: Boolean,
    val infoHash: String? = null // הלינק האמיתי שילך ל-RD
)