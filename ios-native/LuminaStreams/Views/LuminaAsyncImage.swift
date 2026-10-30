import SwiftUI
import UIKit
import CryptoKit

// MARK: - In-Memory and Disk Image Cache
final class LuminaImageCache {
    static let shared = LuminaImageCache()
    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let cacheDir: URL

    private init() {
        memoryCache.countLimit = 400
        memoryCache.totalCostLimit = 120 * 1024 * 1024 // 120MB

        let paths = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)
        cacheDir = paths[0].appendingPathComponent("LuminaImageCache")
        try? fileManager.createDirectory(at: cacheDir, withIntermediateDirectories: true)
    }

    /// Stable across launches (String.hashValue is randomized per process,
    /// which made every disk lookup miss after a relaunch).
    private func diskKey(for urlString: String) -> String {
        SHA256.hash(data: Data(urlString.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// Memory only — safe to call on the main thread.
    func memoryImage(for urlString: String) -> UIImage? {
        memoryCache.object(forKey: urlString as NSString)
    }

    /// Memory, then disk (call off the main thread).
    func get(for urlString: String) -> UIImage? {
        if let mem = memoryImage(for: urlString) {
            return mem
        }
        let fileUrl = cacheDir.appendingPathComponent(diskKey(for: urlString))
        if let data = try? Data(contentsOf: fileUrl), let img = UIImage(data: data)?.preparingForDisplay() {
            let cost = Int(img.size.width * img.size.height * 4)
            memoryCache.setObject(img, forKey: urlString as NSString, cost: cost)
            return img
        }
        return nil
    }

    func set(_ image: UIImage, data: Data, for urlString: String) {
        let cost = Int(image.size.width * image.size.height * 4)
        memoryCache.setObject(image, forKey: urlString as NSString, cost: cost)

        DispatchQueue.global(qos: .background).async { [weak self] in
            guard let self = self else { return }
            let fileUrl = self.cacheDir.appendingPathComponent(self.diskKey(for: urlString))
            try? data.write(to: fileUrl)
        }
    }
}

// MARK: - Network loader with TMDB-block fallback
/// Some Israeli cellular networks block image.tmdb.org. Once a direct TMDB fetch
/// fails, every later TMDB image goes straight through the wsrv.nl proxy instead
/// of waiting for each direct request to time out first.
actor LuminaImageLoader {
    static let shared = LuminaImageLoader()
    private var tmdbBlocked = false

    func load(_ url: String) async -> (UIImage, Data)? {
        let isTmdb = url.contains("image.tmdb.org")
        if isTmdb && tmdbBlocked {
            if let result = await fetch(proxyUrl(for: url), timeout: 10) { return result }
            return await fetch(URL(string: url), timeout: 6)
        }
        if let result = await fetch(URL(string: url), timeout: isTmdb ? 4 : 8) { return result }
        guard isTmdb else { return nil }
        tmdbBlocked = true
        return await fetch(proxyUrl(for: url), timeout: 10)
    }

    private func proxyUrl(for url: String) -> URL? {
        guard let encoded = url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return nil }
        return URL(string: "https://wsrv.nl/?url=\(encoded)")
    }

    private nonisolated func fetch(_ url: URL?, timeout: TimeInterval) async -> (UIImage, Data)? {
        guard let url = url else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = timeout
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: request),
              let http = resp as? HTTPURLResponse, http.statusCode == 200,
              // Decode here, off the main thread, so scrolling never stalls on it.
              let img = UIImage(data: data)?.preparingForDisplay() else { return nil }
        return (img, data)
    }
}

// MARK: - LuminaAsyncImage Component
struct LuminaAsyncImage<Content: View, Placeholder: View>: View {
    let url: String
    let content: (Image) -> Content
    let placeholder: () -> Placeholder

    @State private var image: UIImage? = nil
    @State private var loadedUrl: String? = nil

    init(
        url: String,
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder
    ) {
        self.url = url
        self.content = content
        self.placeholder = placeholder
    }

    var body: some View {
        Group {
            if let uiImage = image {
                content(Image(uiImage: uiImage))
            } else {
                placeholder()
            }
        }
        // Restarts (and cancels the old load) whenever the URL changes.
        .task(id: url) {
            await load(url)
        }
    }

    private func load(_ target: String) async {
        guard !target.isEmpty, target != "null" else { return }
        if loadedUrl == target, image != nil { return }

        // Fast path: memory cache, synchronously, so cached images never flash.
        if let mem = LuminaImageCache.shared.memoryImage(for: target) {
            image = mem
            loadedUrl = target
            return
        }
        // Keep showing the previous image until the new one is ready (carousels).
        let fromDisk = await Task.detached(priority: .userInitiated) {
            LuminaImageCache.shared.get(for: target)
        }.value
        if Task.isCancelled { return }
        if let fromDisk = fromDisk {
            image = fromDisk
            loadedUrl = target
            return
        }

        guard let (loaded, data) = await LuminaImageLoader.shared.load(target) else { return }
        LuminaImageCache.shared.set(loaded, data: data, for: target)
        if Task.isCancelled { return }
        withAnimation(.easeIn(duration: 0.2)) {
            image = loaded
        }
        loadedUrl = target
    }
}
