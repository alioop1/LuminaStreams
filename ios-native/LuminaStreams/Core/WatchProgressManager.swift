import Foundation
import SwiftUI

struct WatchProgressEntry: Codable, Identifiable {
    var id: String { key }
    let key: String
    let tmdbId: String
    let title: String
    let posterUrl: String
    let backdropUrl: String
    let mediaType: String
    let season: Int?
    let episode: Int?
    var positionSeconds: Double
    var durationSeconds: Double
    var updatedAt: TimeInterval

    var fraction: Double {
        guard durationSeconds > 0 else { return 0 }
        return min(max(positionSeconds / durationSeconds, 0), 1)
    }

    var isFinished: Bool {
        fraction >= 0.90
    }
}

final class WatchProgressManager: ObservableObject {
    static let shared = WatchProgressManager()

    @Published private(set) var entries: [String: WatchProgressEntry] = [:]
    @Published private(set) var watchlist: [Movie] = []

    private let progressStorageKey = "lumina_watch_progress_v1"
    private let watchlistStorageKey = "lumina_watchlist_v1"

    init() {
        load()
    }

    private func load() {
        if let data = UserDefaults.standard.data(forKey: progressStorageKey),
           let decoded = try? JSONDecoder().decode([String: WatchProgressEntry].self, from: data) {
            entries = decoded
        }
        if let data = UserDefaults.standard.data(forKey: watchlistStorageKey),
           let decoded = try? JSONDecoder().decode([Movie].self, from: data) {
            watchlist = decoded
        }
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(entries) {
            UserDefaults.standard.set(data, forKey: progressStorageKey)
        }
        if let data = try? JSONEncoder().encode(watchlist) {
            UserDefaults.standard.set(data, forKey: watchlistStorageKey)
        }
    }

    func movieKey(_ imdbId: String) -> String { "movie_\(imdbId)" }
    func episodeKey(_ imdbId: String, season: Int, episode: Int) -> String { "ep_\(imdbId)_s\(season)_e\(episode)" }

    func saveProgress(
        key: String,
        tmdbId: String,
        title: String,
        posterUrl: String,
        backdropUrl: String,
        mediaType: String,
        season: Int? = nil,
        episode: Int? = nil,
        positionSeconds: Double,
        durationSeconds: Double
    ) {
        guard durationSeconds > 5 else { return }
        let entry = WatchProgressEntry(
            key: key,
            tmdbId: tmdbId,
            title: title,
            posterUrl: posterUrl,
            backdropUrl: backdropUrl,
            mediaType: mediaType,
            season: season,
            episode: episode,
            positionSeconds: positionSeconds,
            durationSeconds: durationSeconds,
            updatedAt: Date().timeIntervalSince1970
        )
        entries[key] = entry
        persist()
    }

    func getProgress(key: String) -> WatchProgressEntry? {
        entries[key]
    }

    func continueWatchingMovies() -> [Movie] {
        entries.values
            .filter { !$0.isFinished && $0.fraction > 0.02 }
            .sorted { $0.updatedAt > $1.updatedAt }
            .map { entry in
                Movie(
                    id: entry.tmdbId,
                    title: entry.season != nil ? "\(entry.title) (S\(entry.season!)E\(entry.episode ?? 1))" : entry.title,
                    backdropUrl: entry.backdropUrl,
                    posterUrl: entry.posterUrl,
                    overview: "",
                    rating: 0,
                    year: 0,
                    genre: "",
                    mediaType: entry.mediaType,
                    progress: Float(entry.fraction)
                )
            }
    }

    // ── Watchlist ──────────────────────────────────────────────────────────
    func isInWatchlist(_ id: String) -> Bool {
        watchlist.contains { $0.id == id }
    }

    @discardableResult
    func toggleWatchlist(_ movie: Movie) -> Bool {
        if let idx = watchlist.firstIndex(where: { $0.id == movie.id }) {
            watchlist.remove(at: idx)
            persist()
            return false
        } else {
            watchlist.insert(movie, at: 0)
            persist()
            return true
        }
    }
}
