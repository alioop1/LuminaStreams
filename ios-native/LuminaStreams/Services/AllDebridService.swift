import Foundation

final class AllDebridService {
    static let shared = AllDebridService()
    private let session: URLSession
    private let baseUrl = "https://api.alldebrid.com"
    private let videoExtensions: Set<String> = ["mkv", "mp4", "avi", "ts", "mov", "m4v", "webm", "flv"]

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 18
        config.timeoutIntervalForResource = 30
        self.session = URLSession(configuration: config)
    }

    private var token: String {
        SettingsManager.shared.effectiveDebridToken
    }

    private static func adError(_ code: Int, _ message: String) -> NSError {
        NSError(domain: "AllDebrid", code: code, userInfo: [NSLocalizedDescriptionKey: message])
    }

    /// application/x-www-form-urlencoded value encoding (magnets and links contain & = : ?).
    private static func formEncode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    /// POST to the AllDebrid API (POST is the documented default since 15/01/2025).
    /// Returns the `data` object, or throws with AllDebrid's error message.
    private func post(_ path: String, form: [(String, String)] = []) async throws -> [String: Any] {
        var req = URLRequest(url: URL(string: "\(baseUrl)\(path)?agent=LuminaStreams")!)
        req.httpMethod = "POST"
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        req.httpBody = form
            .map { "\(Self.formEncode($0.0))=\(Self.formEncode($0.1))" }
            .joined(separator: "&")
            .data(using: .utf8)

        let (data, _) = try await session.data(for: req)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw Self.adError(-1, "AllDebrid: invalid response")
        }
        guard (json["status"] as? String) == "success" else {
            let message = ((json["error"] as? [String: Any])?["message"] as? String) ?? "AllDebrid error"
            throw Self.adError(-1, message)
        }
        return (json["data"] as? [String: Any]) ?? [:]
    }

    private static func idString(_ value: Any?) -> String? {
        if let n = value as? NSNumber { return n.stringValue }
        if let s = value as? String, !s.isEmpty { return s }
        return nil
    }

    /// `magnets` is an object for a single id and an array/object map for lists.
    private static func firstMagnet(in data: [String: Any]) -> [String: Any]? {
        if let arr = data["magnets"] as? [[String: Any]] { return arr.first }
        if let dict = data["magnets"] as? [String: Any] {
            if dict["id"] != nil { return dict }
            return dict.values.first as? [String: Any]
        }
        return nil
    }

    /// Upload a magnet (or .torrent upload id) and wait until AllDebrid has the files.
    private func waitForFiles(magnetId: String, attempts: Int) async throws -> [DebridFileItem] {
        for _ in 0..<attempts {
            let data = try await post("/v4.1/magnet/status", form: [("id", magnetId)])
            if let m = Self.firstMagnet(in: data) {
                let statusCode = (m["statusCode"] as? NSNumber)?.intValue ?? 0
                if statusCode == 4 {
                    var files = extractFiles(from: m)
                    if files.isEmpty {
                        // Files moved to their own endpoint in v4.1 for some responses.
                        let filesData = try await post("/v4/magnet/files", form: [("id[]", magnetId)])
                        if let first = (filesData["magnets"] as? [[String: Any]])?.first {
                            files = extractFiles(from: first)
                        }
                    }
                    if !files.isEmpty { return files }
                } else if statusCode > 4 {
                    let statusText = (m["status"] as? String) ?? "Error"
                    throw Self.adError(statusCode, "הטורנט נכשל ב-AllDebrid (\(statusText))")
                }
            }
            try await Task.sleep(nanoseconds: 1_500_000_000)
        }
        return []
    }

    // ── PIN Device Authentication Flow ──────────────────────────────────────
    func requestPin() async throws -> (pin: String, check: String, userUrl: String) {
        let url = URL(string: "\(baseUrl)/v4/pin/get?agent=LuminaStreams")!
        let (data, _) = try await session.data(from: url)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              (json["status"] as? String) == "success",
              let dataObj = json["data"] as? [String: Any],
              let pin = dataObj["pin"] as? String,
              let check = dataObj["check"] as? String,
              let base = dataObj["base_url"] as? String else {
            throw NSError(domain: "AllDebrid", code: -1, userInfo: [NSLocalizedDescriptionKey: "שגיאה בקבלת קוד אימות מ-AllDebrid"])
        }
        return (pin, check, "\(base)?pin=\(pin)")
    }

    func checkPinStatus(pin: String, check: String) async throws -> String? {
        let url = URL(string: "\(baseUrl)/v4/pin/check?agent=LuminaStreams&check=\(check)&pin=\(pin)")!
        let (data, _) = try await session.data(from: url)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              (json["status"] as? String) == "success",
              let dataObj = json["data"] as? [String: Any] else {
            return nil
        }
        if (dataObj["activated"] as? Bool) == true, let apikey = dataObj["apikey"] as? String, !apikey.isEmpty {
            return apikey
        }
        return nil
    }

    // ── Resolve Stream (Magnet InfoHash OR Direct URL) ──────────────────────
    /// Android order: Torrentio's /resolve/alldebrid/... URL plays directly (Torrentio
    /// uploads and redirects); only a bare infoHash needs upload → poll → unlock.
    func resolveStream(source: AdvancedStreamSource, season: Int? = nil, episode: Int? = nil) async throws -> String {
        if let directUrl = source.directUrl, directUrl.hasPrefix("http") {
            return try await resolveDirectTorrentioUrl(directUrl)
        }
        if let infoHash = source.infoHash, !infoHash.isEmpty {
            let magnet = "magnet:?xt=urn:btih:\(infoHash)"
            return try await resolveMagnetToStream(magnetUri: magnet, season: season, episode: episode)
        }
        throw Self.adError(-2, "מקור לא תקין להפעלה")
    }

    /// Range probe (Android DetailsViewModel): an HTML reply means the link expired;
    /// a file of 20 MB or less is a DMCA placeholder. On timeout, play anyway.
    private func resolveDirectTorrentioUrl(_ urlStr: String) async throws -> String {
        guard let url = URL(string: urlStr) else { throw URLError(.badURL) }
        var req = URLRequest(url: url)
        req.setValue("bytes=0-1", forHTTPHeaderField: "Range")
        req.timeoutInterval = 6

        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse {
                let cType = (http.value(forHTTPHeaderField: "Content-Type") ?? "").lowercased()
                if cType.contains("text/html") {
                    throw Self.adError(410, "הקישור פג תוקף ב-AllDebrid. בחר מקור אחר.")
                }
                // "Content-Range: bytes 0-1/123456789" → total size.
                if let range = http.value(forHTTPHeaderField: "Content-Range"),
                   let total = Int64(range.split(separator: "/").last ?? ""),
                   total > 0, total <= 20 * 1024 * 1024 {
                    throw Self.adError(451, "המקור הוסר (DMCA). בחר מקור אחר.")
                }
                if let finalUrl = http.url?.absoluteString, finalUrl.hasPrefix("http") {
                    return finalUrl
                }
            }
        } catch let err as NSError where err.domain == "AllDebrid" {
            throw err
        } catch {
            // Probe timed out or failed — let the player try the original URL.
        }
        return urlStr
    }

    // ── Upload Magnet -> Poll Status -> Select Best File -> Unlock Link ─────
    func resolveMagnetToStream(magnetUri: String, season: Int? = nil, episode: Int? = nil) async throws -> String {
        guard !token.isEmpty else {
            throw Self.adError(401, "טוקן AllDebrid חסר בהגדרות")
        }

        let uploadData = try await post("/v4/magnet/upload", form: [("magnets[]", magnetUri)])
        guard let firstMagnet = (uploadData["magnets"] as? [[String: Any]])?.first else {
            throw Self.adError(-3, "שגיאת AllDebrid: Upload failed")
        }
        if let errObj = firstMagnet["error"] as? [String: Any], let msg = errObj["message"] as? String {
            throw Self.adError(-4, msg)
        }
        guard let magnetId = Self.idString(firstMagnet["id"]) else {
            throw Self.adError(-5, "לא התקבל מזהה מגנט")
        }

        // ~25 s, like Android's overall timeout for the infoHash flow.
        let readyFiles = try await waitForFiles(magnetId: magnetId, attempts: 17)

        guard !readyFiles.isEmpty,
              let bestFile = selectBestFile(files: readyFiles, season: season, episode: episode) else {
            throw NSError(domain: "AllDebrid", code: -6, userInfo: [NSLocalizedDescriptionKey: "לא נמצאו קבצי וידאו מוכנים בטורנט"])
        }

        return try await unlockLink(bestFile.link)
    }

    // ── Upload Raw .torrent Bytes (For Fuzer Direct Releases) ───────────────
    func resolveTorrentFileToStream(torrentBytes: Data, season: Int? = nil, episode: Int? = nil) async throws -> String {
        let apiToken = token
        let boundary = "Boundary-\(UUID().uuidString)"
        let url = URL(string: "\(baseUrl)/v4/magnet/upload/file?agent=LuminaStreams")!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization")
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"files[]\"; filename=\"upload.torrent\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: application/x-bittorrent\r\n\r\n".data(using: .utf8)!)
        body.append(torrentBytes)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body

        let (data, _) = try await session.data(for: req)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              (json["status"] as? String) == "success",
              let dataObj = json["data"] as? [String: Any],
              let filesArr = dataObj["files"] as? [[String: Any]],
              let firstFile = filesArr.first else {
            throw NSError(domain: "AllDebrid", code: -7, userInfo: [NSLocalizedDescriptionKey: "העלאת קובץ הטורנט ל-AllDebrid נכשלה"])
        }

        if let errObj = firstFile["error"] as? [String: Any], let msg = errObj["message"] as? String {
            throw Self.adError(-8, msg)
        }
        guard let magnetId = Self.idString(firstFile["id"]) else {
            throw Self.adError(-8, "מזהה טורנט חסר")
        }

        // Android polls torrent-file uploads for up to 150 × 2 s.
        let readyFiles = try await waitForFiles(magnetId: magnetId, attempts: 200)

        guard let target = selectBestFile(files: readyFiles, season: season, episode: episode) else {
            throw NSError(domain: "AllDebrid", code: -9, userInfo: [NSLocalizedDescriptionKey: "הטורנט עדיין בהורדה או שאין בו וידאו זמין"])
        }
        return try await unlockLink(target.link)
    }

    // ── Unlock AllDebrid Link ───────────────────────────────────────────────
    func unlockLink(_ lockedLink: String) async throws -> String {
        let data: [String: Any]
        do {
            data = try await post("/v4/link/unlock", form: [("link", lockedLink)])
        } catch {
            throw Self.adError(-10, "פתיחת הקישור נכשלה: \(error.localizedDescription)")
        }
        guard let streamLink = data["link"] as? String, !streamLink.isEmpty else {
            throw Self.adError(-10, "פתיחת הקישור נכשלה: Unlock error")
        }
        return streamLink
    }

    // ── Extract Recursive Files Tree (`files` or `links`) ───────────────────
    func extractFiles(from magnetDict: [String: Any]) -> [DebridFileItem] {
        var result: [DebridFileItem] = []

        func walk(_ node: Any) {
            if let dict = node as? [String: Any] {
                if let children = dict["e"] as? [Any] {
                    for child in children { walk(child) }
                }
                let link = (dict["l"] as? String) ?? (dict["link"] as? String) ?? ""
                let name = (dict["n"] as? String) ?? (dict["filename"] as? String) ?? ""
                let size: Int64
                if let s64 = dict["s"] as? Int64 { size = s64 }
                else if let sInt = dict["s"] as? Int { size = Int64(sInt) }
                else if let s2 = dict["size"] as? Int64 { size = s2 }
                else if let s2Int = dict["size"] as? Int { size = Int64(s2Int) }
                else { size = 0 }

                if !link.isEmpty {
                    result.append(DebridFileItem(name: name.isEmpty ? "Video" : name, size: size, link: link))
                }
            } else if let arr = node as? [Any] {
                for item in arr { walk(item) }
            }
        }

        if let filesNode = magnetDict["files"] { walk(filesNode) }
        else if let linksNode = magnetDict["links"] { walk(linksNode) }
        return result
    }

    private func selectBestFile(files: [DebridFileItem], season: Int?, episode: Int?) -> DebridFileItem? {
        guard !files.isEmpty else { return nil }
        if files.count == 1 { return files[0] }

        let nonSamples = files.filter { f in
            let l = f.name.lowercased()
            return !l.contains("sample") && !l.hasSuffix(".nfo") && !l.hasSuffix(".txt") && !l.hasSuffix(".jpg") && !l.hasSuffix(".srt")
        }
        let candidates = nonSamples.isEmpty ? files : nonSamples

        if let s = season, let e = episode {
            let p1 = String(format: "s%02de%02d", s, e)
            let p2 = String(format: "%dx%02d", s, e)
            let p3 = String(format: "e%02d", e)
            let matched = candidates.filter { f in
                let l = f.name.lowercased()
                return l.contains(p1) || l.contains(p2) || l.contains(p3)
            }
            if !matched.isEmpty {
                return matched.max(by: { $0.size < $1.size })
            }
        }

        let videos = candidates.filter { f in
            let ext = (f.name as NSString).pathExtension.lowercased()
            return videoExtensions.contains(ext)
        }
        return (videos.isEmpty ? candidates : videos).max(by: { $0.size < $1.size }) ?? files[0]
    }

    // ── Debrid Cloud: Fetch User Magnets & Delete Magnet ────────────────────
    func fetchUserMagnets() async throws -> [DebridMagnetItem] {
        // /v4/magnet/status is discontinued; v4.1 lists magnets without file trees.
        let data = try await post("/v4.1/magnet/status")
        let magnetsArr: [[String: Any]]
        if let arr = data["magnets"] as? [[String: Any]] {
            magnetsArr = arr
        } else if let dict = data["magnets"] as? [String: Any] {
            magnetsArr = dict.values.compactMap { $0 as? [String: Any] }
        } else {
            magnetsArr = []
        }

        // File trees for ready magnets come from /v4/magnet/files (batched id[]).
        var filesById: [String: [DebridFileItem]] = [:]
        let readyIds = magnetsArr
            .filter { ($0["statusCode"] as? NSNumber)?.intValue == 4 }
            .compactMap { Self.idString($0["id"]) }
        var start = 0
        while start < readyIds.count {
            let chunk = Array(readyIds[start..<min(start + 50, readyIds.count)])
            start += 50
            if let filesData = try? await post("/v4/magnet/files", form: chunk.map { ("id[]", $0) }),
               let entries = filesData["magnets"] as? [[String: Any]] {
                for entry in entries {
                    if let id = Self.idString(entry["id"]) {
                        filesById[id] = extractFiles(from: entry)
                    }
                }
            }
        }

        return magnetsArr.compactMap { m -> DebridMagnetItem? in
            guard let idStr = Self.idString(m["id"]), let id = Int64(idStr) else { return nil }

            let filename = (m["filename"] as? String) ?? "Unknown"
            let size = (m["size"] as? Int64) ?? Int64((m["size"] as? Int) ?? 0)
            let status = (m["status"] as? String) ?? ""
            let statusCode = (m["statusCode"] as? Int) ?? 0
            let downloaded = (m["downloaded"] as? Int64) ?? Int64((m["downloaded"] as? Int) ?? 0)
            let seeders = (m["seeders"] as? Int) ?? 0
            let uploadDate = (m["uploadDate"] as? Int64) ?? Int64((m["uploadDate"] as? Int) ?? 0)
            let files = filesById[idStr] ?? extractFiles(from: m)

            let clean = filename
                .replacingOccurrences(of: ".", with: " ")
                .replacingOccurrences(of: "_", with: " ")
            return DebridMagnetItem(
                id: id,
                filename: filename,
                displayTitle: clean,
                size: size,
                status: status,
                statusCode: statusCode,
                downloaded: downloaded,
                seeders: seeders,
                uploadDate: uploadDate,
                files: files
            )
        }.sorted { a, b in
            if a.isReady != b.isReady { return a.isReady && !b.isReady }
            return a.uploadDate > b.uploadDate
        }
    }

    func deleteMagnet(id: Int64) async throws {
        _ = try await post("/v4/magnet/delete", form: [("id", "\(id)")])
    }
}
