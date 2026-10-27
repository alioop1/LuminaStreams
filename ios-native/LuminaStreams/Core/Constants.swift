import Foundation

enum Constants {
    // ── TMDB ─────────────────────────────────────────────────────────────────
    static let tmdbApiKey  = "9ab4a284f0c028007b78925852196b79"
    static let tmdbBaseUrl = "https://api.themoviedb.org/3"

    // ── OMDB ─────────────────────────────────────────────────────────────────
    static let omdbApiKey  = "e61dc86b"
    static let omdbBaseUrl = "https://www.omdbapi.com"

    // ── AllDebrid Default Token (matches Android TV app) ─────────────────────
    static let defaultAllDebridToken = "GkRRCMwdGBSvjzWqnNP8"

    // ── Fuzer Credentials (matches Android TV FuzerEngine.kt) ────────────────
    static let fuzerUsername = "microxbox93"
    static let fuzerPassword = "AliooP93"
    static let fuzerBaseUrl  = "https://www.fuzer.xyz"

    // ── Image Base URLs ──────────────────────────────────────────────────────
    static let imageW300     = "https://image.tmdb.org/t/p/w300"
    static let imageW500     = "https://image.tmdb.org/t/p/w500"
    static let imageW780     = "https://image.tmdb.org/t/p/w780"
    static let imageW1280    = "https://image.tmdb.org/t/p/w1280"
    static let imageOriginal = "https://image.tmdb.org/t/p/original"

    static func backdropUrl(_ path: String?) -> String {
        guard let path = path, !path.isEmpty, path != "null" else { return "" }
        if path.hasPrefix("http") { return path }
        return "\(imageOriginal)\(path)"
    }

    static func posterUrl(_ path: String?) -> String {
        guard let path = path, !path.isEmpty, path != "null" else { return "" }
        if path.hasPrefix("http") { return path }
        return "\(imageW780)\(path)"
    }
}
