import Foundation
import CryptoKit

final class FuzerService {
    static let shared = FuzerService()
    private let session: URLSession
    private var isLoggedIn = false

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.httpCookieStorage = HTTPCookieStorage.shared
        config.timeoutIntervalForRequest = 20
        self.session = URLSession(configuration: config)
    }

    private var md5Password: String {
        let digest = Insecure.MD5.hash(data: Data(Constants.fuzerPassword.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private func defaultHeaders(referer: String) -> [String: String] {
        [
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "he-IL,he;q=0.9,en-US;q=0.8,en;q=0.7",
            "Referer": referer,
            "Origin": Constants.fuzerBaseUrl
        ]
    }

    private func decodeHebrewData(_ data: Data) -> String {
        let win1255 = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.windowsHebrew.rawValue))
        if let str = String(data: data, encoding: String.Encoding(rawValue: win1255)) {
            return str
        }
        return String(data: data, encoding: .utf8) ?? ""
    }

    func ensureLogin() async -> Bool {
        if isLoggedIn { return true }
        do {
            let base = Constants.fuzerBaseUrl
            var checkReq = URLRequest(url: URL(string: "\(base)/index.php")!)
            defaultHeaders(referer: base).forEach { checkReq.setValue($1, forHTTPHeaderField: $0) }
            let (checkData, _) = try await session.data(for: checkReq)
            let checkHtml = decodeHebrewData(checkData)
            if checkHtml.localizedCaseInsensitiveContains("logout") || checkHtml.localizedCaseInsensitiveContains("loggout") {
                isLoggedIn = true
                return true
            }

            var loginPageReq = URLRequest(url: URL(string: "\(base)/login.php")!)
            defaultHeaders(referer: base).forEach { loginPageReq.setValue($1, forHTTPHeaderField: $0) }
            let (loginPageData, _) = try await session.data(for: loginPageReq)
            let loginPageHtml = decodeHebrewData(loginPageData)

            var secToken = "guest"
            if let tokenMatch = loginPageHtml.range(of: "name=\"securitytoken\"\\s+value=\"([^\"]+)\"", options: .regularExpression) {
                let fragment = String(loginPageHtml[tokenMatch])
                if let valRange = fragment.range(of: "value=\"([^\"]+)\"", options: .regularExpression) {
                    secToken = String(fragment[valRange]).replacingOccurrences(of: "value=\"", with: "").replacingOccurrences(of: "\"", with: "")
                }
            }

            var postReq = URLRequest(url: URL(string: "\(base)/login.php?do=login")!)
            postReq.httpMethod = "POST"
            defaultHeaders(referer: "\(base)/login.php").forEach { postReq.setValue($1, forHTTPHeaderField: $0) }
            postReq.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

            let form = [
                "vb_login_username=\(Constants.fuzerUsername)",
                "vb_login_password=",
                "vb_login_md5password=\(md5Password)",
                "vb_login_md5password_utf=\(md5Password)",
                "do=login",
                "securitytoken=\(secToken)",
                "cookieuser=1"
            ].joined(separator: "&")
            postReq.httpBody = form.data(using: .utf8)

            let (respData, _) = try await session.data(for: postReq)
            let respHtml = decodeHebrewData(respData)
            if respHtml.localizedCaseInsensitiveContains("logout") || respHtml.localizedCaseInsensitiveContains("loggout") || respHtml.contains(Constants.fuzerUsername) {
                isLoggedIn = true
                return true
            }
        } catch {}
        return false
    }

    func fetchCategory(catId: Int = 7, page: Int = 1) async throws -> [Movie] {
        guard await ensureLogin() else {
            throw NSError(domain: "Fuzer", code: 401, userInfo: [NSLocalizedDescriptionKey: "התחברות ל-Fuzer נכשלה"])
        }
        let urlStr = "\(Constants.fuzerBaseUrl)/browse.php?cat=\(catId)&page=\(page)"
        var req = URLRequest(url: URL(string: urlStr)!)
        defaultHeaders(referer: "\(Constants.fuzerBaseUrl)/browse.php").forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let (data, _) = try await session.data(for: req)
        let html = decodeHebrewData(data)
        return parseHtmlToMovies(html)
    }

    func search(query: String) async throws -> [Movie] {
        guard await ensureLogin() else { return [] }
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        let urlStr = "\(Constants.fuzerBaseUrl)/browse.php?ref_=basic&query=\(encoded)&matchquery=any"
        var req = URLRequest(url: URL(string: urlStr)!)
        defaultHeaders(referer: "\(Constants.fuzerBaseUrl)/browse.php").forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let (data, _) = try await session.data(for: req)
        let html = decodeHebrewData(data)
        return parseHtmlToMovies(html)
    }

    func downloadTorrentFile(urlStr: String) async throws -> Data {
        guard await ensureLogin() else {
            throw NSError(domain: "Fuzer", code: 401, userInfo: [NSLocalizedDescriptionKey: "התחברות ל-Fuzer נכשלה"])
        }
        var finalUrl = urlStr
        if urlStr.contains("showthread.php") {
            var threadReq = URLRequest(url: URL(string: urlStr)!)
            defaultHeaders(referer: "\(Constants.fuzerBaseUrl)/browse.php").forEach { threadReq.setValue($1, forHTTPHeaderField: $0) }
            let (tData, _) = try await session.data(for: threadReq)
            let html = decodeHebrewData(tData)
            if let range = html.range(of: "href=\"([^\"]*attachment\\.php[^\"]*)\"", options: .regularExpression) {
                let raw = String(html[range])
                    .replacingOccurrences(of: "href=\"", with: "")
                    .replacingOccurrences(of: "\"", with: "")
                    .replacingOccurrences(of: "&amp;", with: "&")
                finalUrl = raw.hasPrefix("http") ? raw : "\(Constants.fuzerBaseUrl)/\(raw)"
            }
        }
        var dlReq = URLRequest(url: URL(string: finalUrl)!)
        defaultHeaders(referer: "\(Constants.fuzerBaseUrl)/browse.php").forEach { dlReq.setValue($1, forHTTPHeaderField: $0) }
        let (bytes, _) = try await session.data(for: dlReq)
        return bytes
    }

    private func parseHtmlToMovies(_ html: String) -> [Movie] {
        var results: [Movie] = []
        let pattern = "<a[^>]+href=\"([^\"]*showthread\\.php\\?t=\\d+[^\"]*)\"[^>]*>([^<]+)</a>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return [] }
        let matches = regex.matches(in: html, range: NSRange(html.startIndex..., in: html))

        var seenUrls = Set<String>()
        for match in matches.prefix(40) {
            guard let hrefRange = Range(match.range(at: 1), in: html),
                  let titleRange = Range(match.range(at: 2), in: html) else { continue }
            let rawHref = String(html[hrefRange]).replacingOccurrences(of: "&amp;", with: "&")
            let rawTitle = String(html[titleRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            guard rawTitle.count > 4 else { continue }

            let fullUrl = rawHref.hasPrefix("http") ? rawHref : "\(Constants.fuzerBaseUrl)/\(rawHref)"
            guard !seenUrls.contains(fullUrl) else { continue }
            seenUrls.insert(fullUrl)

            let is4K = rawTitle.uppercased().contains("2160P") || rawTitle.uppercased().contains("4K")
            results.append(Movie(
                id: fullUrl,
                title: rawTitle,
                backdropUrl: "",
                posterUrl: "",
                overview: "שחרור ישיר מ-Fuzer • ניגון מיידי דרך AllDebrid",
                rating: 8.5,
                year: 2025,
                genre: "Fuzer IL",
                mediaType: "fuzer",
                is4K: is4K,
                resolutionBadge: is4K ? "4K UHD" : "1080p"
            ))
        }
        return results
    }
}
