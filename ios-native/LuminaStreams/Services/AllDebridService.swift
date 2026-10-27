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
    func resolveStream(source: AdvancedStreamSource, season: Int? = nil, episode: Int? = nil) async throws -> String {
        if let infoHash = source.infoHash, !infoHash.isEmpty {
            let magnet = "magnet:?xt=urn:btih:\(infoHash)"
            return try await resolveMagnetToStream(magnetUri: magnet, season: season, episode: episode)
        }
        if let directUrl = source.directUrl, directUrl.hasPrefix("http") {
            return try await resolveDirectTorrentioUrl(directUrl)
        }
        throw NSError(domain: "AllDebrid", code: -2, userInfo: [NSLocalizedDescriptionKey: "מקור לא תקין להפעלה"])
    }

    private func resolveDirectTorrentioUrl(_ urlStr: String) async throws -> String {
        guard let url = URL(string: urlStr) else { throw URLError(.badURL) }
        var req = URLRequest(url: url)
        req.setValue("bytes=0-1", forHTTPHeaderField: "Range")
        req.timeoutInterval = 8

        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse {
                let cType = (http.value(forHTTPHeaderField: "Content-Type") ?? "").lowercased()
                if cType.contains("text/html") {
                    throw NSError(domain: "AllDebrid", code: 410, userInfo: [NSLocalizedDescriptionKey: "הקישור פג תוקף ב-AllDebrid. בחר מקור אחר."])
                }
                if let finalUrl = http.url?.absoluteString, finalUrl.hasPrefix("http") {
                    return finalUrl
                }
            }
        } catch let err as NSError where err.domain == "AllDebrid" {
            throw err
        } catch {
            // Fallback to original direct URL if Range probe times out
        }
        return urlStr
    }

    // ── Upload Magnet -> Poll Status -> Select Best File -> Unlock Link ─────
    func resolveMagnetToStream(magnetUri: String, season: Int? = nil, episode: Int? = nil) async throws -> String {
        let apiToken = token
        guard !apiToken.isEmpty else {
            throw NSError(domain: "AllDebrid", code: 401, userInfo: [NSLocalizedDescriptionKey: "טוקן AllDebrid חסר בהגדרות"])
        }

        var comps = URLComponents(string: "\(baseUrl)/v4/magnet/upload")!
        comps.queryItems = [
            URLQueryItem(name: "agent", value: "LuminaStreams"),
            URLQueryItem(name: "magnets[]", value: magnetUri)
        ]
        var uploadReq = URLRequest(url: comps.url!)
        uploadReq.httpMethod = "POST"
        uploadReq.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization")

        let (uploadData, _) = try await session.data(for: uploadReq)
        guard let uploadJson = try JSONSerialization.jsonObject(with: uploadData) as? [String: Any],
              (uploadJson["status"] as? String) == "success",
              let dataObj = uploadJson["data"] as? [String: Any],
              let magnetsArr = dataObj["magnets"] as? [[String: Any]],
              let firstMagnet = magnetsArr.first else {
            let errMsg = ((try? JSONSerialization.jsonObject(with: uploadData) as? [String: Any])?["error"] as? [String: Any])?["message"] as? String ?? "Upload failed"
            throw NSError(domain: "AllDebrid", code: -3, userInfo: [NSLocalizedDescriptionKey: "שגיאת AllDebrid: \(errMsg)"])
        }

        if let errObj = firstMagnet["error"] as? [String: Any], let msg = errObj["message"] as? String {
            throw NSError(domain: "AllDebrid", code: -4, userInfo: [NSLocalizedDescriptionKey: msg])
        }

        let magnetId: String
        if let idInt = firstMagnet["id"] as? Int64 {
            magnetId = "\(idInt)"
        } else if let idNum = firstMagnet["id"] as? Int {
            magnetId = "\(idNum)"
        } else if let idStr = firstMagnet["id"] as? String {
            magnetId = idStr
        } else {
            throw NSError(domain: "AllDebrid", code: -5, userInfo: [NSLocalizedDescriptionKey: "לא התקבל מזהה מגנט"])
        }

        // Poll status until ready (statusCode == 4)
        var readyFiles: [DebridFileItem] = []
        for _ in 0..<25 {
            let statusUrl = URL(string: "\(baseUrl)/v4/magnet/status?agent=LuminaStreams&id=\(magnetId)")!
            var statusReq = URLRequest(url: statusUrl)
            statusReq.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization")

            let (statusData, _) = try await session.data(for: statusReq)
            if let statusJson = try JSONSerialization.jsonObject(with: statusData) as? [String: Any],
               (statusJson["status"] as? String) == "success",
               let sData = statusJson["data"] as? [String: Any] {
                let magnetObj: [String: Any]?
                if let mDict = sData["magnets"] as? [String: Any] {
                    magnetObj = mDict
                } else if let mArr = sData["magnets"] as? [[String: Any]] {
                    magnetObj = mArr.first
                } else {
                    magnetObj = nil
                }

                if let m = magnetObj {
                    let statusCode = (m["statusCode"] as? Int) ?? 0
                    if statusCode == 4 {
                        readyFiles = extractFiles(from: m)
                        if !readyFiles.isEmpty { break }
                    } else if statusCode > 4 {
                        let statusText = (m["status"] as? String) ?? "Error"
                        throw NSError(domain: "AllDebrid", code: statusCode, userInfo: [NSLocalizedDescriptionKey: "הטורנט נכשל ב-AllDebrid (\(statusText))"])
                    }
                }
            }
            try await Task.sleep(nanoseconds: 1_500_000_000)
        }

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

        let magnetId: String
        if let idInt = firstFile["id"] as? Int64 { magnetId = "\(idInt)" }
        else if let idNum = firstFile["id"] as? Int { magnetId = "\(idNum)" }
        else if let idStr = firstFile["id"] as? String { magnetId = idStr }
        else { throw NSError(domain: "AllDebrid", code: -8, userInfo: [NSLocalizedDescriptionKey: "מזהה טורנט חסר"]) }

        var readyFiles: [DebridFileItem] = []
        for _ in 0..<30 {
            let statusUrl = URL(string: "\(baseUrl)/v4/magnet/status?agent=LuminaStreams&id=\(magnetId)")!
            var statusReq = URLRequest(url: statusUrl)
            statusReq.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization")
            let (statusData, _) = try await session.data(for: statusReq)
            if let statusJson = try JSONSerialization.jsonObject(with: statusData) as? [String: Any],
               let sData = statusJson["data"] as? [String: Any] {
                let mObj = (sData["magnets"] as? [String: Any]) ?? (sData["magnets"] as? [[String: Any]])?.first
                if let m = mObj, ((m["statusCode"] as? Int) ?? 0) == 4 {
                    readyFiles = extractFiles(from: m)
                    if !readyFiles.isEmpty { break }
                }
            }
            try await Task.sleep(nanoseconds: 1_500_000_000)
        }

        guard let target = selectBestFile(files: readyFiles, season: season, episode: episode) else {
            throw NSError(domain: "AllDebrid", code: -9, userInfo: [NSLocalizedDescriptionKey: "הטורנט עדיין בהורדה או שאין בו וידאו זמין"])
        }
        return try await unlockLink(target.link)
    }

    // ── Unlock AllDebrid Link ───────────────────────────────────────────────
    func unlockLink(_ lockedLink: String) async throws -> String {
        let apiToken = token
        var comps = URLComponents(string: "\(baseUrl)/v4/link/unlock")!
        comps.queryItems = [
            URLQueryItem(name: "agent", value: "LuminaStreams"),
            URLQueryItem(name: "link", value: lockedLink)
        ]
        var req = URLRequest(url: comps.url!)
        req.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization")

        let (data, _) = try await session.data(for: req)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              (json["status"] as? String) == "success",
              let dataObj = json["data"] as? [String: Any],
              let streamLink = dataObj["link"] as? String, !streamLink.isEmpty else {
            let errMsg = ((try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["error"] as? [String: Any])?["message"] as? String ?? "Unlock error"
            throw NSError(domain: "AllDebrid", code: -10, userInfo: [NSLocalizedDescriptionKey: "פתיחת הקישור נכשלה: \(errMsg)"])
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
        let apiToken = token
        let url = URL(string: "\(baseUrl)/v4/magnet/status?agent=LuminaStreams")!
        var req = URLRequest(url: url)
        req.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization")

        let (data, _) = try await session.data(for: req)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              (json["status"] as? String) == "success",
              let dataObj = json["data"] as? [String: Any],
              let magnetsArr = dataObj["magnets"] as? [[String: Any]] else {
            return []
        }

        return magnetsArr.compactMap { m -> DebridMagnetItem? in
            let id: Int64
            if let i64 = m["id"] as? Int64 { id = i64 }
            else if let i = m["id"] as? Int { id = Int64(i) }
            else { return nil }

            let filename = (m["filename"] as? String) ?? "Unknown"
            let size = (m["size"] as? Int64) ?? Int64((m["size"] as? Int) ?? 0)
            let status = (m["status"] as? String) ?? ""
            let statusCode = (m["statusCode"] as? Int) ?? 0
            let downloaded = (m["downloaded"] as? Int64) ?? Int64((m["downloaded"] as? Int) ?? 0)
            let seeders = (m["seeders"] as? Int) ?? 0
            let uploadDate = (m["uploadDate"] as? Int64) ?? Int64((m["uploadDate"] as? Int) ?? 0)
            let files = extractFiles(from: m)

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
        let apiToken = token
        let url = URL(string: "\(baseUrl)/v4/magnet/delete?agent=LuminaStreams&id=\(id)")!
        var req = URLRequest(url: url)
        req.setValue("Bearer \(apiToken)", forHTTPHeaderField: "Authorization")
        _ = try await session.data(for: req)
    }
}
