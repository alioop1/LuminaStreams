import Foundation
import SwiftUI

// ── Movie / Card Model ──────────────────────────────────────────────────────
struct Movie: Identifiable, Codable, Hashable {
    let id: String
    let title: String
    let backdropUrl: String
    let posterUrl: String
    let overview: String
    let rating: Float
    var year: Int = 0
    var genre: String = "דרמה"
    var mediaType: String = "movie"
    var genreIds: [Int] = []
    var is4K: Bool = false
    var resolutionBadge: String = "1080p"
    var progress: Float? = nil
    var seeders: Int = 0
    var leechers: Int = 0
    var size: String = ""

    var displayId: String {
        if id.hasPrefix("movie_") || id.hasPrefix("tv_") || id.hasPrefix("http") {
            return id
        }
        return "\(mediaType == "tv" ? "tv" : "movie")_\(id)"
    }
}

struct HomeRowDef: Identifiable {
    let id: String
    let title: String
    var items: [Movie]
    var isLandscape: Bool = false
}

struct StudioCategory: Identifiable {
    let id: String
    let titleEn: String
    let titleHe: String
    let movies: [Movie]
}

// ── Details Models ──────────────────────────────────────────────────────────
struct CastMember: Identifiable, Hashable {
    let id: String
    let name: String
    let character: String
    let imageUrl: String
}

struct ActorFullDetails: Identifiable {
    let id: String
    let name: String
    let biography: String
    let birthday: String
    let placeOfBirth: String
    let profileUrl: String
    let knownFor: [Movie]
}

struct Episode: Identifiable, Hashable {
    let id: String
    let episodeNumber: Int
    let seasonNumber: Int
    let title: String
    let overview: String
    let stillUrl: String
    var hasWatched: Bool = false
    var progress: Float = 0
    var airDateIso: String = ""
}

struct Recommendation: Identifiable, Hashable {
    let id: String
    let title: String
    let posterUrl: String
}

struct Review: Identifiable, Hashable {
    var id: String { author + content.prefix(24) }
    let author: String
    let content: String
    let rating: Double?
}

struct NextEpisodeInfo: Hashable {
    let seasonNumber: Int
    let episodeNumber: Int
    let name: String
    let airDateIso: String
    let stillUrl: String
}

struct MediaDetailsInfo {
    var id: String = ""
    var imdbId: String = ""
    var title: String = ""
    var overview: String = ""
    var posterUrl: String = ""
    var backdropUrl: String = ""
    var logoUrl: String = ""
    var isSeries: Bool = false
    var releaseDate: String = ""
    var runtimeMinutes: Int = 0
    var tmdbRating: Double = 0.0
    var imdbRating: Double = 0.0
    var ageRating: String = "TV-MA"
    var studios: [String] = []
    var originalLanguage: String = ""
    var genres: [String] = []
    var director: String = ""
    var cast: [CastMember] = []
    var totalSeasons: Int = 0
    var isFavorite: Bool = false
    var trailerKey: String? = nil
    var collectionName: String? = nil
    var collectionItems: [Recommendation] = []
    var directorName: String? = nil
    var directorItems: [Recommendation] = []
    var rottenTomatoesScore: String? = nil
    var metacriticScore: String? = nil
    var reviews: [Review] = []
    var seriesStatus: String = ""
    var nextEpisode: NextEpisodeInfo? = nil
}

// ── Stream Quality & Codec & Language (matches DetailsContract.kt) ──────────
enum StreamQuality: String, CaseIterable {
    case uhd4K = "4K UHD"
    case fhd1080p = "1080p"
    case hd720p = "720p"
    case sd480p = "480p"
    case unknown = "HD"

    var priority: Int {
        switch self {
        case .uhd4K: return 9
        case .fhd1080p: return 7
        case .hd720p: return 6
        case .sd480p: return 5
        case .unknown: return 0
        }
    }

    static func fromString(_ title: String) -> StreamQuality {
        let t = title.uppercased()
        if t.contains("4K") || t.contains("2160P") { return .uhd4K }
        if t.contains("1080P") { return .fhd1080p }
        if t.contains("720P") { return .hd720p }
        if t.contains("480P") { return .sd480p }
        return .unknown
    }
}

enum VideoCodec: String {
    case hevc = "HEVC"
    case avc  = "H.264"
    case av1  = "AV1"
    case unknown = ""

    static func fromString(_ title: String) -> VideoCodec {
        let t = title.uppercased()
        if t.contains("HEVC") || t.contains("X265") || t.contains("H265") { return .hevc }
        if t.contains("AVC") || t.contains("X264") || t.contains("H264") { return .avc }
        if t.contains("AV1") { return .av1 }
        return .unknown
    }
}

enum AudioLanguage: String, CaseIterable, Hashable {
    case hebrew     = "HEB"
    case english    = "ENG"
    case russian    = "RUS"
    case ukrainian  = "UKR"
    case spanish    = "SPA"
    case french     = "FRE"
    case german     = "GER"
    case italian    = "ITA"
    case japanese   = "JPN"
    case korean     = "KOR"
    case arabic     = "ARA"
    case multi      = "Multi"

    var flag: String {
        switch self {
        case .hebrew: return "🇮🇱"
        case .english: return "🇬🇧"
        case .russian: return "🇷🇺"
        case .ukrainian: return "🇺🇦"
        case .spanish: return "🇪🇸"
        case .french: return "🇫🇷"
        case .german: return "🇩🇪"
        case .italian: return "🇮🇹"
        case .japanese: return "🇯🇵"
        case .korean: return "🇰🇷"
        case .arabic: return "🇸🇦"
        case .multi: return "🌐"
        }
    }

    static func detectFromTitle(_ title: String) -> [AudioLanguage] {
        let t = title.uppercased()
        var langs: [AudioLanguage] = []
        if t.contains("HEBREW") || t.contains("HEB") || t.contains("HEBDUB") { langs.append(.hebrew) }
        if t.contains("RUSSIAN") || t.contains("RUS") || t.contains("ДУБ") || t.contains("ОЗВУЧ") || t.contains("DVO") { langs.append(.russian) }
        if t.contains("ENGLISH") || t.contains("ENG") { langs.append(.english) }
        if t.contains("MULTI") || t.contains("DUAL") { langs.append(.multi) }
        if t.contains("SPANISH") || t.contains("SPA") || t.contains("LATINO") { langs.append(.spanish) }
        if t.contains("FRENCH") || t.contains("FRE") { langs.append(.french) }
        if langs.isEmpty { langs.append(.english) }
        return langs
    }
}

enum TrackerParser {
    static func extractTracker(_ rawName: String) -> String {
        if let range = rawName.range(of: "\\[([^\\]]+)\\]", options: .regularExpression) {
            return String(rawName[range]).replacingOccurrences(of: "[", with: "").replacingOccurrences(of: "]", with: "").trimmingCharacters(in: .whitespaces)
        }
        let known = ["RuTracker", "RuTor", "YTS", "RARBG", "1337x", "TorrentGalaxy", "ThePirateBay", "TPB", "EZTV", "Nyaa"]
        for k in known {
            if rawName.localizedCaseInsensitiveContains(k) { return k }
        }
        let lines = rawName.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if lines.count >= 2 && !["torrentio", "unknown"].contains(lines[1].lowercased()) {
            return lines[1]
        }
        return "Torrentio"
    }

    static func trackerColor(_ tracker: String) -> Color {
        switch tracker.lowercased() {
        case "rutracker": return Color(red: 0.48, green: 0.23, blue: 0.93)
        case "rutor": return Color(red: 0.98, green: 0.45, blue: 0.09)
        case "yts": return Color(red: 0.13, green: 0.77, blue: 0.37)
        case "rarbg": return Color(red: 0.94, green: 0.27, blue: 0.27)
        case "torrentgalaxy": return Color(red: 0.23, green: 0.51, blue: 0.96)
        case "1337x": return Color(red: 0.91, green: 0.47, blue: 0.98)
        case "thepiratebay", "tpb": return Color(red: 0.92, green: 0.70, blue: 0.03)
        case "eztv": return Color(red: 0.02, green: 0.71, blue: 0.83)
        default: return Color(red: 0.58, green: 0.64, blue: 0.72)
        }
    }
}

enum StreamSortMode: String, CaseIterable {
    case defaultSort = "גודל ואיכות"
    case byTracker   = "לפי טראקר"
    case byLanguage  = "לפי שפה"
}

struct AdvancedStreamSource: Identifiable, Hashable {
    let id: String
    let releaseGroup: String
    let filename: String
    let infoHash: String?
    let directUrl: String?
    let sizeBytes: Int64
    let isCachedAd: Bool
    let quality: StreamQuality
    let videoCodec: VideoCodec
    let rawTitle: String
    let trackerName: String
    let audioLanguages: [AudioLanguage]
    let seeders: Int
    let leechers: Int

    var sizeGb: Double {
        Double(sizeBytes) / (1024.0 * 1024.0 * 1024.0)
    }

    var formattedSize: String {
        if sizeBytes <= 0 { return "" }
        if sizeGb >= 1.0 {
            return String(format: "%.2f GB", sizeGb)
        } else {
            let mb = Double(sizeBytes) / (1024.0 * 1024.0)
            return String(format: "%.0f MB", mb)
        }
    }

    var isDolbyVision: Bool {
        let u = filename.uppercased()
        return u.contains("DV") || u.contains("DOVI") || u.contains("DOLBY.VISION")
    }

    var isHDR: Bool {
        let u = filename.uppercased()
        return u.contains("HDR") || u.contains("HDR10")
    }

    var isDirectPlayFriendly: Bool {
        let lower = filename.lowercased()
        return lower.hasSuffix(".mp4") || lower.hasSuffix(".m4v") || lower.contains(".mp4")
    }
}

// ── Subtitle Cue Model ──────────────────────────────────────────────────────
struct SubtitleCue: Identifiable {
    let id: Int
    let startTime: Double
    let endTime: Double
    let text: String
}

// ── AllDebrid Cloud Models ──────────────────────────────────────────────────
struct DebridFileItem: Identifiable, Hashable {
    var id: String { link }
    let name: String
    let size: Int64
    let link: String

    var formattedSize: String {
        let gb = Double(size) / (1024.0 * 1024.0 * 1024.0)
        if gb >= 1.0 { return String(format: "%.2f GB", gb) }
        let mb = Double(size) / (1024.0 * 1024.0)
        return String(format: "%.0f MB", mb)
    }
}

struct DebridMagnetItem: Identifiable, Hashable {
    let id: Int64
    let filename: String
    let displayTitle: String
    let size: Int64
    let status: String
    let statusCode: Int
    let downloaded: Int64
    let seeders: Int
    let uploadDate: Int64
    let files: [DebridFileItem]
    var posterUrl: String? = nil

    var isReady: Bool { statusCode == 4 }
    var formattedSize: String {
        let gb = Double(size) / (1024.0 * 1024.0 * 1024.0)
        if gb >= 1.0 { return String(format: "%.2f GB", gb) }
        let mb = Double(size) / (1024.0 * 1024.0)
        return String(format: "%.0f MB", mb)
    }
}

// ── IPTV Models ─────────────────────────────────────────────────────────────
struct IptvChannel: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let logoUrl: String
    let group: String
    let streamUrl: String
    let tvgId: String
    var isFavorite: Bool = false
}

// ── Playback Target ─────────────────────────────────────────────────────────
struct PlaybackRequest: Identifiable {
    let id = UUID()
    let streamUrl: String
    let title: String
    let subtitle: String
    let imdbId: String
    let tmdbId: String
    let posterUrl: String
    let backdropUrl: String
    let isSeries: Bool
    let season: Int?
    let episode: Int?
    let resumePositionSeconds: Double
}
