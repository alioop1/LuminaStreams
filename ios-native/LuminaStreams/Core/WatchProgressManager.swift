import Foundation
import SwiftUI

/// Snapshot of how far through a title the user has watched.
/// Mirrors Android's `WatchProgress` (data/local/WatchProgressManager.kt).
struct WatchProgress {
    let positionMs: Int64
    let durationMs: Int64
    /// Epoch milliseconds of the most recent watch event.
    let lastWatchedAt: Int64

    var fraction: Double {
        guard durationMs > 0 else { return 0 }
        return min(max(Double(positionMs) / Double(durationMs), 0), 1)
    }
    var positionSeconds: Double { Double(positionMs) / 1000 }
    var durationSeconds: Double { Double(durationMs) / 1000 }
    /// ≥ 90 % watched.
    var isFinished: Bool { fraction >= 0.90 }
    /// ≥ 2 % watched — filters out accidental starts.
    var hasStarted: Bool { fraction >= 0.02 }
}

/// Display info for Continue Watching (local only; not part of the synced record).
struct WatchProgressMeta: Codable {
    let tmdbId: String      // "movie_123" / "tv_123"
    let title: String
    let posterUrl: String
    let backdropUrl: String
}

/// Watch progress store shared in format with Android and Web:
///   key   → "movie_{imdbId}" or "ep_{imdbId}_s{S}e{E}"
///   value → JSON string {"p": ms, "d": ms, "t": epochMs, "tmdb": "movie_123"}
/// An empty string marks a deleted entry (cloud tombstone).
final class WatchProgressManager: ObservableObject {
    static let shared = WatchProgressManager()

    @Published private(set) var progress: [String: String] = [:]
    @Published private(set) var meta: [String: WatchProgressMeta] = [:]
    @Published private(set) var watchlist: [Movie] = []

    private let progressStorageKey = "lumina_watch_v2"
    private let metaStorageKey = "lumina_watch_meta_v1"
    private let legacyProgressKey = "lumina_watch_progress_v1"
    private let watchlistStorageKey = "lumina_watchlist_v1"

    init() {
        load()
        migrateLegacyProgress()
    }

    // ── Persistence ────────────────────────────────────────────────────────
    private func load() {
        let defaults = UserDefaults.standard
        if let stored = defaults.dictionary(forKey: progressStorageKey) as? [String: String] {
            progress = stored
        }
        if let data = defaults.data(forKey: metaStorageKey),
           let decoded = try? JSONDecoder().decode([String: WatchProgressMeta].self, from: data) {
            meta = decoded
        }
        if let data = defaults.data(forKey: watchlistStorageKey),
           let decoded = try? JSONDecoder().decode([Movie].self, from: data) {
            watchlist = decoded
        }
    }

    private func persistProgress() {
        UserDefaults.standard.set(progress, forKey: progressStorageKey)
        if let data = try? JSONEncoder().encode(meta) {
            UserDefaults.standard.set(data, forKey: metaStorageKey)
        }
    }

    private func persistWatchlist() {
        if let data = try? JSONEncoder().encode(watchlist) {
            UserDefaults.standard.set(data, forKey: watchlistStorageKey)
        }
    }

    /// Older builds stored seconds under "ep_{imdb}_s{S}_e{E}"; convert once.
    private func migrateLegacyProgress() {
        struct LegacyEntry: Decodable {
            let key: String
            let tmdbId: String
            let title: String
            let posterUrl: String
            let backdropUrl: String
            let positionSeconds: Double
            let durationSeconds: Double
            let updatedAt: TimeInterval
        }
        let defaults = UserDefaults.standard
        guard let data = defaults.data(forKey: legacyProgressKey) else { return }
        defer { defaults.removeObject(forKey: legacyProgressKey) }
        guard let legacy = try? JSONDecoder().decode([String: LegacyEntry].self, from: data) else { return }

        for entry in legacy.values {
            let key = entry.key.replacingOccurrences(of: #"_s(\d+)_e(\d+)$"#, with: "_s$1e$2", options: .regularExpression)
            guard Self.isValidKey(key), progress[key] == nil else { continue }
            progress[key] = Self.encode(
                positionMs: Int64(entry.positionSeconds * 1000),
                durationMs: Int64(entry.durationSeconds * 1000),
                lastWatchedAt: Int64(entry.updatedAt * 1000),
                tmdbId: entry.tmdbId
            )
            meta[key] = WatchProgressMeta(tmdbId: entry.tmdbId, title: entry.title,
                                          posterUrl: entry.posterUrl, backdropUrl: entry.backdropUrl)
        }
        persistProgress()
    }

    // ── Keys ───────────────────────────────────────────────────────────────
    func movieKey(_ imdbId: String) -> String { "movie_\(imdbId)" }
    func episodeKey(_ imdbId: String, season: Int, episode: Int) -> String { "ep_\(imdbId)_s\(season)e\(episode)" }

    /// Keys must carry a real IMDb id so Android and Web can resolve them.
    static func isValidKey(_ key: String) -> Bool {
        key.range(of: #"^(movie_tt\d+|ep_tt\d+_s\d+e\d+)$"#, options: .regularExpression) != nil
    }

    private static func encode(positionMs: Int64, durationMs: Int64, lastWatchedAt: Int64, tmdbId: String?) -> String {
        var obj: [String: Any] = ["p": positionMs, "d": durationMs, "t": lastWatchedAt]
        if let tmdbId = tmdbId, !tmdbId.isEmpty { obj["tmdb"] = tmdbId }
        guard let data = try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys]),
              let str = String(data: data, encoding: .utf8) else { return "" }
        return str
    }

    static func decode(_ raw: String) -> WatchProgress? {
        guard !raw.isEmpty,
              let data = raw.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let p = (obj["p"] as? NSNumber)?.int64Value,
              let d = (obj["d"] as? NSNumber)?.int64Value,
              let t = (obj["t"] as? NSNumber)?.int64Value else { return nil }
        return WatchProgress(positionMs: p, durationMs: d, lastWatchedAt: t)
    }

    // ── Write ──────────────────────────────────────────────────────────────
    /// Save playback progress. Returns the stored JSON (for cloud push), or nil if not saved.
    @discardableResult
    func save(key: String, positionSeconds: Double, durationSeconds: Double, meta info: WatchProgressMeta) -> String? {
        guard Self.isValidKey(key), durationSeconds > 0, positionSeconds.isFinite else { return nil }
        let json = Self.encode(
            positionMs: Int64(max(0, positionSeconds) * 1000),
            durationMs: Int64(durationSeconds * 1000),
            lastWatchedAt: Int64(Date().timeIntervalSince1970 * 1000),
            tmdbId: info.tmdbId
        )
        progress[key] = json
        meta[key] = info
        persistProgress()
        return json
    }

    func deleteProgress(_ key: String) {
        progress[key] = nil
        meta[key] = nil
        persistProgress()
    }

    // ── Read ───────────────────────────────────────────────────────────────
    func getProgress(_ key: String) -> WatchProgress? {
        guard let raw = progress[key] else { return nil }
        return Self.decode(raw)
    }

    /// Most recently watched episode of a series (Android getLatestEpisodeProgress).
    func latestEpisode(imdbId: String) -> (season: Int, episode: Int, progress: WatchProgress)? {
        let prefix = "ep_\(imdbId)_s"
        var best: (Int, Int, WatchProgress)? = nil
        for (key, raw) in progress where key.hasPrefix(prefix) {
            let rest = key.dropFirst(prefix.count)              // e.g. "1e3"
            let parts = rest.split(separator: "e")
            guard parts.count == 2, let s = Int(parts[0]), let e = Int(parts[1]),
                  let p = Self.decode(raw) else { continue }
            if best == nil || p.lastWatchedAt > best!.2.lastWatchedAt {
                best = (s, e, p)
            }
        }
        return best.map { (season: $0.0, episode: $0.1, progress: $0.2) }
    }

    /// One card per title (latest episode for series), 2–90 % watched, newest first, max 20.
    func continueWatchingMovies() -> [Movie] {
        var newestPerTitle: [String: (key: String, progress: WatchProgress)] = [:]
        for (key, raw) in progress {
            guard let p = Self.decode(raw) else { continue }
            let group: String
            if key.hasPrefix("ep_"), let range = key.range(of: #"_s\d+e\d+$"#, options: .regularExpression) {
                group = String(key[..<range.lowerBound])
            } else {
                group = key
            }
            if let current = newestPerTitle[group], current.progress.lastWatchedAt >= p.lastWatchedAt { continue }
            newestPerTitle[group] = (key, p)
        }

        return newestPerTitle.values
            .filter { $0.progress.hasStarted && !$0.progress.isFinished }
            .sorted { $0.progress.lastWatchedAt > $1.progress.lastWatchedAt }
            .prefix(20)
            .compactMap { item -> Movie? in
                guard let info = meta[item.key], !info.tmdbId.isEmpty else { return nil }
                var title = info.title
                if let range = item.key.range(of: #"_s(\d+)e(\d+)$"#, options: .regularExpression) {
                    let se = item.key[range].dropFirst(2).split(separator: "e")
                    if se.count == 2 { title += " (S\(se[0])E\(se[1]))" }
                }
                return Movie(
                    id: info.tmdbId,
                    title: title,
                    backdropUrl: info.backdropUrl,
                    posterUrl: info.posterUrl,
                    overview: "",
                    rating: 0,
                    mediaType: info.tmdbId.hasPrefix("tv_") ? "tv" : "movie",
                    progress: Float(item.progress.fraction)
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
            persistWatchlist()
            return false
        } else {
            watchlist.insert(movie, at: 0)
            persistWatchlist()
            return true
        }
    }
}
