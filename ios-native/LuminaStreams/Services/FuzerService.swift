import Foundation

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

    /// Desktop UA shared with the sign-in web view, so the session Fuzer issues
    /// there is presented with the same client identity here.
    static let desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36"
    private static let cookieStoreKey = "fuzer_session_cookies_v1"

    static let notSignedInError = NSError(domain: "Fuzer", code: 401, userInfo: [
        NSLocalizedDescriptionKey: SettingsManager.shared.tr("יש להתחבר ל-Fuzer", "Sign in to Fuzer to continue")
    ])

    private func defaultHeaders(referer: String) -> [String: String] {
        [
            "User-Agent": Self.desktopUserAgent,
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

    // ── Session ────────────────────────────────────────────────────────────
    // Fuzer's login page is protected by reCAPTCHA, so the app cannot sign in on
    // its own. The user signs in once in FuzerLoginView; its cookies are copied
    // here and persisted, and every request reuses them.

    /// Copy the cookies from the sign-in web view and keep them across launches.
    func importSession(_ cookies: [HTTPCookie]) {
        let fuzerCookies = cookies.filter { $0.domain.contains("fuzer.xyz") }
        fuzerCookies.forEach { HTTPCookieStorage.shared.setCookie($0) }
        let saved = fuzerCookies.compactMap { cookie -> [String: Any]? in
            guard let props = cookie.properties else { return nil }
            // Property-list safe copy (originURL may be a URL object).
            return Dictionary(uniqueKeysWithValues: props.map { key, value -> (String, Any) in
                (key.rawValue, (value as? URL)?.absoluteString ?? value)
            })
        }
        UserDefaults.standard.set(saved, forKey: Self.cookieStoreKey)
        isLoggedIn = true
    }

    /// Session cookies are not persisted by iOS; restore ours on first use.
    private func restoreSavedSession() {
        guard let saved = UserDefaults.standard.array(forKey: Self.cookieStoreKey) as? [[String: Any]] else { return }
        for dict in saved {
            let props = Dictionary(uniqueKeysWithValues: dict.map { (HTTPCookiePropertyKey($0.key), $0.value) })
            if let cookie = HTTPCookie(properties: props) {
                HTTPCookieStorage.shared.setCookie(cookie)
            }
        }
    }

    func signOut() {
        isLoggedIn = false
        UserDefaults.standard.removeObject(forKey: Self.cookieStoreKey)
        HTTPCookieStorage.shared.cookies?
            .filter { $0.domain.contains("fuzer.xyz") }
            .forEach { HTTPCookieStorage.shared.deleteCookie($0) }
    }

    /// True when the stored session is accepted: a signed-out request to
    /// index.php is redirected to login.php.
    func ensureLogin() async -> Bool {
        if isLoggedIn { return true }
        restoreSavedSession()
        do {
            var checkReq = URLRequest(url: URL(string: "\(Constants.fuzerBaseUrl)/index.php")!)
            defaultHeaders(referer: Constants.fuzerBaseUrl).forEach { checkReq.setValue($1, forHTTPHeaderField: $0) }
            let (_, response) = try await session.data(for: checkReq)
            let finalPath = response.url?.path.lowercased() ?? ""
            isLoggedIn = !finalPath.contains("login")
        } catch {
            isLoggedIn = false
        }
        return isLoggedIn
    }

    func fetchCategory(catId: Int = 7, page: Int = 1) async throws -> [Movie] {
        guard await ensureLogin() else { throw Self.notSignedInError }
        let urlStr = "\(Constants.fuzerBaseUrl)/browse.php?cat=\(catId)&page=\(page)"
        var req = URLRequest(url: URL(string: urlStr)!)
        defaultHeaders(referer: "\(Constants.fuzerBaseUrl)/browse.php").forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let (data, _) = try await session.data(for: req)
        let html = decodeHebrewData(data)
        return parseHtmlToMovies(html)
    }

    func search(query: String) async throws -> [Movie] {
        guard await ensureLogin() else { throw Self.notSignedInError }
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        let urlStr = "\(Constants.fuzerBaseUrl)/browse.php?ref_=basic&query=\(encoded)&matchquery=any"
        var req = URLRequest(url: URL(string: urlStr)!)
        defaultHeaders(referer: "\(Constants.fuzerBaseUrl)/browse.php").forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let (data, _) = try await session.data(for: req)
        let html = decodeHebrewData(data)
        return parseHtmlToMovies(html)
    }

    func downloadTorrentFile(urlStr: String) async throws -> Data {
        guard await ensureLogin() else { throw Self.notSignedInError }
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
