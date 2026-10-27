import Foundation

final class TorrentioService {
    static let shared = TorrentioService()
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 20
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        ]
        self.session = URLSession(configuration: config)
    }

    func fetchStreams(imdbId: String, isSeries: Bool, season: Int? = nil, episode: Int? = nil) async throws -> [AdvancedStreamSource] {
        let token = SettingsManager.shared.effectiveDebridToken
        let type = (isSeries && season != nil && episode != nil) ? "series" : "movie"
        let queryId: String
        if let s = season, let e = episode, isSeries {
            queryId = "\(imdbId):\(s):\(e)"
        } else {
            queryId = imdbId
        }

        let urlStr = "https://torrentio.strem.fun/alldebrid=\(token)/stream/\(type)/\(queryId).json"
        guard let url = URL(string: urlStr) else {
            throw URLError(.badURL)
        }

        let (data, response) = try await session.data(from: url)
        if let httpResp = response as? HTTPURLResponse, !(200...299).contains(httpResp.statusCode) {
            throw NSError(domain: "Torrentio", code: httpResp.statusCode, userInfo: [NSLocalizedDescriptionKey: "שגיאת שרת Torrentio (\(httpResp.statusCode))"])
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let streamsArr = json["streams"] as? [[String: Any]] else {
            return []
        }

        var parsed: [AdvancedStreamSource] = []
        for (idx, item) in streamsArr.enumerated() {
            let name = item["name"] as? String
            let title = item["title"] as? String
            let directUrl = item["url"] as? String
            let infoHash = item["infoHash"] as? String
            if let source = Self.parseStream(index: idx, name: name, title: title, url: directUrl, infoHash: infoHash) {
                parsed.append(source)
            }
        }

        // Sort: Cached AD+ first, MP4/DirectPlay bonus, then Quality priority, then Size & Seeders
        return parsed.sorted { a, b in
            if a.isCachedAd != b.isCachedAd { return a.isCachedAd && !b.isCachedAd }
            if a.quality.priority != b.quality.priority { return a.quality.priority > b.quality.priority }
            if a.sizeBytes != b.sizeBytes { return a.sizeBytes > b.sizeBytes }
            return a.seeders > b.seeders
        }
    }

    // ── Exact port of TorrentioStreamParser.kt ──────────────────────────────
    static func parseStream(index: Int, name: String?, title: String?, url: String?, infoHash: String?) -> AdvancedStreamSource? {
        guard let titleSafe = title, !titleSafe.isEmpty else { return nil }
        let nameSafe = name ?? "Torrentio"
        let hasHash = !(infoHash ?? "").isEmpty
        let hasUrl = !(url ?? "").isEmpty
        guard hasHash || hasUrl else { return nil }

        let filename = titleSafe.components(separatedBy: "\n").first ?? titleSafe
        let sizeBytes = parseSizeBytes(titleSafe)
        let seeders = parseSeeders(titleSafe)
        let leechers = parseLeechers(titleSafe)
        let tracker = parseTracker(title: titleSafe, name: nameSafe)

        return AdvancedStreamSource(
            id: "str_\(index)",
            releaseGroup: nameSafe.replacingOccurrences(of: "\n", with: " "),
            filename: filename,
            infoHash: infoHash,
            directUrl: url,
            sizeBytes: sizeBytes,
            isCachedAd: nameSafe.contains("AD+") || nameSafe.contains("RD+"),
            quality: StreamQuality.fromString(titleSafe),
            videoCodec: VideoCodec.fromString(titleSafe),
            rawTitle: titleSafe,
            trackerName: tracker,
            audioLanguages: AudioLanguage.detectFromTitle("\(titleSafe) \(nameSafe)"),
            seeders: seeders,
            leechers: leechers
        )
    }

    private static func parseSizeBytes(_ title: String) -> Int64 {
        let pattern = "([0-9.]+)\\s*(GB|MB)"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
              let valRange = Range(match.range(at: 1), in: title),
              let unitRange = Range(match.range(at: 2), in: title),
              let val = Double(title[valRange]) else {
            return 0
        }
        let unit = title[unitRange].uppercased()
        if unit == "GB" {
            return Int64(val * 1_073_741_824.0)
        } else {
            return Int64(val * 1_048_576.0)
        }
    }

    private static func parseSeeders(_ title: String) -> Int {
        let pattern = "👤\\s*(\\d+)"
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
              let r = Range(match.range(at: 1), in: title) else {
            return 0
        }
        return Int(title[r]) ?? 0
    }

    private static func parseLeechers(_ title: String) -> Int {
        let pattern = "🌐\\s*(\\d+)"
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
              let r = Range(match.range(at: 1), in: title) else {
            return 0
        }
        return Int(title[r]) ?? 0
    }

    private static func parseTracker(title: String, name: String) -> String {
        let pattern = "⚙️\\s*([^\\n]+)"
        if let regex = try? NSRegularExpression(pattern: pattern),
           let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
           let r = Range(match.range(at: 1), in: title) {
            return String(title[r]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return TrackerParser.extractTracker(name)
    }
}
