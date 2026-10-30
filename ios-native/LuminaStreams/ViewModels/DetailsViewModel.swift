import Foundation
import SwiftUI

@MainActor
final class DetailsViewModel: ObservableObject {
    @Published var mediaInfo = MediaDetailsInfo()
    @Published var recommendations: [Recommendation] = []
    @Published var episodes: [Episode] = []
    @Published var selectedSeason: Int = 1
    @Published var isLoadingData: Bool = false
    @Published var isEpisodesLoading: Bool = false
    @Published var errorMessage: String? = nil

    // Scraping & Streams State
    @Published var allStreams: [AdvancedStreamSource] = []
    @Published var filteredStreams: [AdvancedStreamSource] = []
    @Published var isScraping: Bool = false
    @Published var isResolvingStream: Bool = false
    @Published var resolvingMessage: String = ""
    @Published var scrapeError: String? = nil
    @Published var showSourcesSheet: Bool = false

    // Stream Filters & Sort (matches DetailsViewModel.kt)
    @Published var sortMode: StreamSortMode = .defaultSort
    @Published var qualityFilter: StreamQuality? = nil
    @Published var showCachedOnly: Bool = false
    @Published var showDvOnly: Bool = false
    @Published var selectedLanguageFilter: AudioLanguage? = nil
    @Published var selectedTrackerFilter: String? = nil

    // Actor Biography Popup
    @Published var selectedActor: ActorFullDetails? = nil
    @Published var isLoadingActor: Bool = false
    @Published var showActorSheet: Bool = false

    // Active Playback Request
    @Published var activePlayback: PlaybackRequest? = nil

    private var activeSeasonForScrape: Int? = nil
    private var activeEpisodeForScrape: Int? = nil

    func load(fullId: String) async {
        // Direct Fuzer URL handling
        if fullId.hasPrefix("http") {
            mediaInfo = MediaDetailsInfo(
                id: fullId,
                imdbId: fullId,
                title: "Fuzer Release",
                overview: "מוריד קובץ טורנט מ-Fuzer ומפעיל ישירות דרך AllDebrid...",
                isSeries: false,
                ageRating: "IL",
                studios: ["Fuzer Israel"],
                genres: ["Direct Download"]
            )
            await resolveAndPlayFuzerDirect(urlStr: fullId)
            return
        }

        isLoadingData = true
        errorMessage = nil
        do {
            let (info, recs) = try await TmdbService.shared.fetchFullDetails(fullId: fullId)
            self.mediaInfo = info
            self.recommendations = recs
            self.isLoadingData = false

            if info.isSeries && info.totalSeasons > 0 {
                await selectSeason(1)
            }
        } catch {
            self.isLoadingData = false
            self.errorMessage = error.localizedDescription
        }
    }

    func selectSeason(_ seasonNumber: Int) async {
        selectedSeason = seasonNumber
        isEpisodesLoading = true
        let eps = await TmdbService.shared.fetchSeasonEpisodes(
            tmdbId: mediaInfo.id,
            imdbId: mediaInfo.imdbId,
            seasonNumber: seasonNumber
        )
        self.episodes = eps
        self.isEpisodesLoading = false
    }

    func toggleFavorite() {
        let movie = Movie(
            id: mediaInfo.id,
            title: mediaInfo.title,
            backdropUrl: mediaInfo.backdropUrl,
            posterUrl: mediaInfo.posterUrl,
            overview: mediaInfo.overview,
            rating: Float(mediaInfo.tmdbRating),
            year: Int(mediaInfo.releaseDate.prefix(4)) ?? 0,
            genre: mediaInfo.genres.first ?? "",
            mediaType: mediaInfo.isSeries ? "tv" : "movie"
        )
        let isFav = WatchProgressManager.shared.toggleWatchlist(movie)
        mediaInfo.isFavorite = isFav
    }

    // ── Scrape Streams via Torrentio ────────────────────────────────────────
    func startScraping(season: Int? = nil, episode: Int? = nil, autoPlayTop: Bool = false) {
        activeSeasonForScrape = season
        activeEpisodeForScrape = episode
        showSourcesSheet = true
        isScraping = true
        scrapeError = nil
        allStreams = []
        filteredStreams = []

        Task {
            do {
                let streams = try await TorrentioService.shared.fetchStreams(
                    imdbId: mediaInfo.imdbId,
                    isSeries: mediaInfo.isSeries,
                    season: season,
                    episode: episode
                )
                self.allStreams = streams
                self.applyFiltersAndSort()
                self.isScraping = false

                if streams.isEmpty {
                    self.scrapeError = SettingsManager.shared.tr("לא נמצאו מקורות עבור תוכן זה.", "No sources found for this title.")
                } else if autoPlayTop, let top = self.filteredStreams.first {
                    await selectAndPlayStream(top)
                }
            } catch {
                self.isScraping = false
                self.scrapeError = error.localizedDescription
            }
        }
    }

    func applyFiltersAndSort() {
        var result = allStreams

        if showCachedOnly {
            result = result.filter { $0.isCachedAd }
        }
        if showDvOnly {
            result = result.filter { $0.isDolbyVision }
        }
        if let q = qualityFilter {
            result = result.filter { $0.quality == q }
        }
        if let lang = selectedLanguageFilter {
            result = result.filter { $0.audioLanguages.contains(lang) }
        }
        if let tracker = selectedTrackerFilter {
            result = result.filter { $0.trackerName == tracker }
        }

        switch sortMode {
        case .defaultSort:
            result.sort { a, b in
                if a.isCachedAd != b.isCachedAd { return a.isCachedAd && !b.isCachedAd }
                if a.quality.priority != b.quality.priority { return a.quality.priority > b.quality.priority }
                if a.sizeBytes != b.sizeBytes { return a.sizeBytes > b.sizeBytes }
                return a.seeders > b.seeders
            }
        case .byTracker:
            result.sort { a, b in
                if a.trackerName != b.trackerName { return a.trackerName < b.trackerName }
                return a.sizeBytes > b.sizeBytes
            }
        case .byLanguage:
            result.sort { a, b in
                let aHe = a.audioLanguages.contains(.hebrew)
                let bHe = b.audioLanguages.contains(.hebrew)
                if aHe != bHe { return aHe && !bHe }
                return a.sizeBytes > b.sizeBytes
            }
        }

        filteredStreams = result
    }

    func clearFilters() {
        sortMode = .defaultSort
        qualityFilter = nil
        showCachedOnly = false
        showDvOnly = false
        selectedLanguageFilter = nil
        selectedTrackerFilter = nil
        applyFiltersAndSort()
    }

    // ── Resolve Selected Stream via AllDebrid & Launch Player ───────────────
    func selectAndPlayStream(_ source: AdvancedStreamSource) async {
        guard !isResolvingStream else { return }
        isResolvingStream = true
        resolvingMessage = SettingsManager.shared.tr("פותח קישור ישיר ב-AllDebrid...", "Resolving direct link via AllDebrid...")
        scrapeError = nil

        do {
            let streamUrl = try await AllDebridService.shared.resolveStream(
                source: source,
                season: activeSeasonForScrape,
                episode: activeEpisodeForScrape
            )
            isResolvingStream = false
            showSourcesSheet = false

            let subTitle: String
            if let s = activeSeasonForScrape, let e = activeEpisodeForScrape {
                subTitle = "S\(String(format: "%02d", s))E\(String(format: "%02d", e)) • \(source.quality.rawValue)"
            } else {
                subTitle = "\(mediaInfo.releaseDate) • \(source.quality.rawValue)"
            }

            let progKey = (activeSeasonForScrape != nil && activeEpisodeForScrape != nil)
                ? WatchProgressManager.shared.episodeKey(mediaInfo.imdbId, season: activeSeasonForScrape!, episode: activeEpisodeForScrape!)
                : WatchProgressManager.shared.movieKey(mediaInfo.imdbId)
            // Resume unless already finished (Android: ≥ 90 % counts as watched).
            let saved = WatchProgressManager.shared.getProgress(progKey)
            let resumePos = (saved?.isFinished == false) ? (saved?.positionSeconds ?? 0) : 0

            activePlayback = PlaybackRequest(
                streamUrl: streamUrl,
                title: mediaInfo.title,
                subtitle: subTitle,
                imdbId: mediaInfo.imdbId,
                tmdbId: mediaInfo.id,
                posterUrl: mediaInfo.posterUrl,
                backdropUrl: mediaInfo.backdropUrl,
                isSeries: mediaInfo.isSeries,
                season: activeSeasonForScrape,
                episode: activeEpisodeForScrape,
                resumePositionSeconds: resumePos
            )
        } catch {
            isResolvingStream = false
            scrapeError = error.localizedDescription
        }
    }

    func resolveAndPlayFuzerDirect(urlStr: String) async {
        isResolvingStream = true
        resolvingMessage = "מוריד טורנט מ-Fuzer ומעלה ל-AllDebrid..."
        do {
            let torrentBytes = try await FuzerService.shared.downloadTorrentFile(urlStr: urlStr)
            let streamUrl = try await AllDebridService.shared.resolveTorrentFileToStream(torrentBytes: torrentBytes)
            isResolvingStream = false
            activePlayback = PlaybackRequest(
                streamUrl: streamUrl,
                title: mediaInfo.title,
                subtitle: "Fuzer Direct • AD+",
                imdbId: "",
                tmdbId: urlStr,
                posterUrl: "",
                backdropUrl: "",
                isSeries: false,
                season: nil,
                episode: nil,
                resumePositionSeconds: 0
            )
        } catch {
            isResolvingStream = false
            errorMessage = error.localizedDescription
        }
    }

    func loadActor(actorId: String) {
        showActorSheet = true
        isLoadingActor = true
        selectedActor = nil
        Task {
            if let details = try? await TmdbService.shared.fetchActorDetails(personId: actorId) {
                self.selectedActor = details
            }
            self.isLoadingActor = false
        }
    }
}
