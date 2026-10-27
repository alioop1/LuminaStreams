import Foundation
import Compression

final class SubtitleService {
    static let shared = SubtitleService()
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 12
        self.session = URLSession(configuration: config)
    }

    // ── Fetch Subtitles (OpenSubtitles Stremio Addon + SubDL) & Parse SRT ──
    func fetchSubtitles(imdbId: String, season: Int? = nil, episode: Int? = nil, langCode: String = "heb") async -> [SubtitleCue] {
        guard !imdbId.isEmpty else { return [] }
        let cleanImdb = imdbId.hasPrefix("tt") ? imdbId : "tt\(imdbId)"

        // 1. Try OpenSubtitles v3 Stremio JSON endpoint (returns direct .srt URLs, no zip needed!)
        let type = (season != nil && episode != nil) ? "series" : "movie"
        let queryId = (season != nil && episode != nil) ? "\(cleanImdb):\(season!):\(episode!)" : cleanImdb
        let osUrlStr = "https://opensubtitles-v3.strem.io/subtitles/\(type)/\(queryId).json"

        if let osUrl = URL(string: osUrlStr),
           let (data, _) = try? await session.data(from: osUrl),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let subsArr = json["subtitles"] as? [[String: Any]] {
            let matching = subsArr.filter {
                let l = (($0["lang"] as? String) ?? "").lowercased()
                return l.hasPrefix(langCode.lowercased().prefix(2))
            }
            if let chosen = matching.first ?? subsArr.first,
               let srtUrlStr = chosen["url"] as? String,
               let srtUrl = URL(string: srtUrlStr),
               let (srtData, _) = try? await session.data(from: srtUrl) {
                let text = decodeSubtitleText(srtData)
                let cues = parseSRT(text)
                if !cues.isEmpty { return cues }
            }
        }

        // 2. Fallback to SubDL API (matches SubtitleScraper.kt)
        let sdLang: String
        switch langCode.lowercased().prefix(2) {
        case "he", "iw": sdLang = "HE"
        case "ru": sdLang = "RU"
        case "ar": sdLang = "AR"
        default: sdLang = "EN"
        }

        var searchUrlStr = "https://api.subdl.com/api/v1/subtitles?imdb_id=\(cleanImdb)&lang=\(sdLang)&subs_per_page=5"
        if let s = season, let e = episode {
            searchUrlStr += "&season_number=\(s)&episode_number=\(e)"
        }

        if let searchUrl = URL(string: searchUrlStr),
           let (data, _) = try? await session.data(from: searchUrl),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let subs = json["subtitles"] as? [[String: Any]],
           let firstPath = subs.first?["url"] as? String,
           let zipUrl = URL(string: "https://dl.subdl.com\(firstPath)"),
           let (zipData, _) = try? await session.data(from: zipUrl),
           let srtData = extractFirstSrtFromZip(zipData) {
            let text = decodeSubtitleText(srtData)
            return parseSRT(text)
        }

        return []
    }

    private func decodeSubtitleText(_ data: Data) -> String {
        if let utf8 = String(data: data, encoding: .utf8) { return utf8 }
        let win1255 = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.windowsHebrew.rawValue))
        if let heb = String(data: data, encoding: String.Encoding(rawValue: win1255)) { return heb }
        return String(data: data, encoding: .isoLatin1) ?? ""
    }

    // ── Lightweight Pure-Swift ZIP Extractor for .srt/.vtt entries ──────────
    private func extractFirstSrtFromZip(_ zipData: Data) -> Data? {
        let bytes = [UInt8](zipData)
        var offset = 0
        while offset + 30 < bytes.count {
            // Local file header signature 0x04034b50
            guard bytes[offset] == 0x50, bytes[offset+1] == 0x4B, bytes[offset+2] == 0x03, bytes[offset+3] == 0x04 else {
                offset += 1
                continue
            }
            let compressionMethod = UInt16(bytes[offset+8]) | (UInt16(bytes[offset+9]) << 8)
            let compressedSize = Int(UInt32(bytes[offset+18]) | (UInt32(bytes[offset+19]) << 8) | (UInt32(bytes[offset+20]) << 16) | (UInt32(bytes[offset+21]) << 24))
            let uncompressedSize = Int(UInt32(bytes[offset+22]) | (UInt32(bytes[offset+23]) << 8) | (UInt32(bytes[offset+24]) << 16) | (UInt32(bytes[offset+25]) << 24))
            let nameLen = Int(UInt16(bytes[offset+26]) | (UInt16(bytes[offset+27]) << 8))
            let extraLen = Int(UInt16(bytes[offset+28]) | (UInt16(bytes[offset+29]) << 8))

            let nameStart = offset + 30
            let dataStart = nameStart + nameLen + extraLen
            guard dataStart + compressedSize <= bytes.count else { break }

            let nameData = Data(bytes[nameStart..<(nameStart + nameLen)])
            let fileName = (String(data: nameData, encoding: .utf8) ?? "").lowercased()

            if fileName.hasSuffix(".srt") || fileName.hasSuffix(".vtt") {
                let payload = Data(bytes[dataStart..<(dataStart + compressedSize)])
                if compressionMethod == 0 {
                    return payload
                } else if compressionMethod == 8 && uncompressedSize > 0 {
                    var dst = Data(count: uncompressedSize)
                    let decompressedBytes = payload.withUnsafeBytes { srcPtr -> Int in
                        dst.withUnsafeMutableBytes { dstPtr -> Int in
                            guard let sBase = srcPtr.bindMemory(to: UInt8.self).baseAddress,
                                  let dBase = dstPtr.bindMemory(to: UInt8.self).baseAddress else { return 0 }
                            return compression_decode_buffer(dBase, uncompressedSize, sBase, compressedSize, nil, COMPRESSION_ZLIB)
                        }
                    }
                    if decompressedBytes > 0 {
                        return dst.prefix(decompressedBytes)
                    }
                }
            }
            offset = dataStart + max(compressedSize, 1)
        }
        return nil
    }

    // ── SRT / VTT Parser ────────────────────────────────────────────────────
    func parseSRT(_ raw: String) -> [SubtitleCue] {
        let normalized = raw.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        let blocks = normalized.components(separatedBy: "\n\n")
        var cues: [SubtitleCue] = []
        var index = 0

        for block in blocks {
            let lines = block.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
            guard lines.count >= 2 else { continue }

            var timeLineIdx = 0
            if !lines[0].contains("-->") && lines.count >= 3 && lines[1].contains("-->") {
                timeLineIdx = 1
            }
            guard lines[timeLineIdx].contains("-->") else { continue }

            let parts = lines[timeLineIdx].components(separatedBy: "-->")
            guard parts.count == 2,
                  let start = parseTimestamp(parts[0].trimmingCharacters(in: .whitespaces)),
                  let end = parseTimestamp(parts[1].trimmingCharacters(in: .whitespaces).components(separatedBy: " ").first ?? "") else {
                continue
            }

            let textLines = lines[(timeLineIdx + 1)...]
            let cleanText = textLines.joined(separator: "\n")
                .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            if !cleanText.isEmpty {
                index += 1
                cues.append(SubtitleCue(id: index, startTime: start, endTime: end, text: cleanText))
            }
        }
        return cues
    }

    private func parseTimestamp(_ str: String) -> Double? {
        let clean = str.replacingOccurrences(of: ",", with: ".")
        let comps = clean.components(separatedBy: ":")
        if comps.count == 3 {
            let h = Double(comps[0]) ?? 0
            let m = Double(comps[1]) ?? 0
            let s = Double(comps[2]) ?? 0
            return h * 3600.0 + m * 60.0 + s
        } else if comps.count == 2 {
            let m = Double(comps[0]) ?? 0
            let s = Double(comps[1]) ?? 0
            return m * 60.0 + s
        }
        return nil
    }
}
