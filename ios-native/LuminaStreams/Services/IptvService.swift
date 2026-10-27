import Foundation

final class IptvService {
    static let shared = IptvService()
    private let session = URLSession.shared

    // Default curated public playlist (Israeli & International FTA news/sports/entertainment)
    static let defaultM3uUrl = "https://iptv-org.github.io/iptv/countries/il.m3u"

    func fetchAndParseM3u(urlStr: String) async throws -> [IptvChannel] {
        let target = urlStr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Self.defaultM3uUrl : urlStr.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: target) else {
            throw URLError(.badURL)
        }
        let (data, _) = try await session.data(from: url)
        guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
            return []
        }
        return parseM3uContent(text)
    }

    func parseM3uContent(_ content: String) -> [IptvChannel] {
        let lines = content.components(separatedBy: .newlines)
        var channels: [IptvChannel] = []

        var currentName = ""
        var currentLogo = ""
        var currentGroup = "כללי"
        var currentTvgId = ""

        for rawLine in lines {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty { continue }

            if line.hasPrefix("#EXTINF:") {
                currentName = line.components(separatedBy: ",").last?.trimmingCharacters(in: .whitespaces) ?? "Channel"
                currentLogo = extractAttr("tvg-logo", from: line)
                let grp = extractAttr("group-title", from: line)
                currentGroup = grp.isEmpty ? "ערוצים חיים" : grp
                currentTvgId = extractAttr("tvg-id", from: line)
            } else if !line.hasPrefix("#") && (line.hasPrefix("http://") || line.hasPrefix("https://")) {
                if !currentName.isEmpty {
                    channels.append(IptvChannel(
                        id: "ch_\(channels.count)_\(currentName.hashValue)",
                        name: currentName,
                        logoUrl: currentLogo,
                        group: currentGroup,
                        streamUrl: line,
                        tvgId: currentTvgId
                    ))
                    currentName = ""
                    currentLogo = ""
                }
            }
        }
        return channels
    }

    private func extractAttr(_ attr: String, from line: String) -> String {
        let pattern = "\(attr)=\"([^\"]*)\""
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
              let range = Range(match.range(at: 1), in: line) else {
            return ""
        }
        return String(line[range])
    }
}
