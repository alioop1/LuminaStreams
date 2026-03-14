package com.luminastreams.tv.presentation.player

import androidx.lifecycle.ViewModel
import com.luminastreams.tv.domain.model.StreamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class StreamPickerState(
    val isLoading: Boolean = false,
    val currentFilter: String = "All",
    val resolvingLinkId: String? = null,
    val filteredSources: List<StreamSource> = emptyList()
)

class StreamPickerViewModel : ViewModel() {
    private val _state = MutableStateFlow(StreamPickerState())
    val state: StateFlow<StreamPickerState> = _state

    init {
        // התאמת הנתונים לסוגי המשתנים הנכונים (Double במקום String וכו')
        _state.value = StreamPickerState(
            filteredSources = listOf(
                StreamSource(
                    id = "1",
                    groupName = "Tigole",
                    filename = "Movie.4K.HDR.mkv",
                    sizeGb = 15.2,
                    seeders = 142,
                    resolution = "4K",
                    codec = "HEVC",
                    audioFormat = "Atmos",
                    isCached = true,
                    isDV = false,
                    isHDR10 = true,
                    hasBuiltInSubs = true,
                    infoHash = null
                ),
                StreamSource(
                    id = "2",
                    groupName = "RARBG",
                    filename = "Movie.1080p.mkv",
                    sizeGb = 2.5,
                    seeders = 890,
                    resolution = "1080p",
                    codec = "AVC",
                    audioFormat = "5.1",
                    isCached = true,
                    isDV = false,
                    isHDR10 = false,
                    hasBuiltInSubs = false,
                    infoHash = null
                )
            )
        )
    }
}