import SwiftUI
import UIKit

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

    private func diskKey(for urlString: String) -> String {
        let safe = urlString.components(separatedBy: CharacterSet.alphanumerics.inverted).joined()
        return String(safe.suffix(40)) + "_" + String(abs(urlString.hashValue))
    }

    func get(for urlString: String) -> UIImage? {
        if let mem = memoryCache.object(forKey: urlString as NSString) {
            return mem
        }
        let fileUrl = cacheDir.appendingPathComponent(diskKey(for: urlString))
        if let data = try? Data(contentsOf: fileUrl), let img = UIImage(data: data) {
            let cost = Int(img.size.width * img.size.height * 4)
            memoryCache.setObject(img, forKey: urlString as NSString, cost: cost)
            return img
        }
        return nil
    }

    func set(_ image: UIImage, for urlString: String) {
        let cost = Int(image.size.width * image.size.height * 4)
        memoryCache.setObject(image, forKey: urlString as NSString, cost: cost)
        
        DispatchQueue.global(qos: .background).async { [weak self] in
            guard let self = self else { return }
            let fileUrl = self.cacheDir.appendingPathComponent(self.diskKey(for: urlString))
            if let data = image.jpegData(compressionQuality: 0.85) {
                try? data.write(to: fileUrl)
            }
        }
    }
}

// MARK: - LuminaAsyncImage Component with Resilient Cloudflare Fallback
struct LuminaAsyncImage<Content: View, Placeholder: View>: View {
    let url: String
    let content: (Image) -> Content
    let placeholder: () -> Placeholder

    @State private var image: UIImage? = nil
    @State private var isLoading: Bool = false

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
        .onAppear {
            load()
        }
        .onChange(of: url) { _ in
            image = nil
            load()
        }
    }

    private func load() {
        guard !url.isEmpty, url != "null" else { return }

        // Fast path: memory or disk cache
        if let cached = LuminaImageCache.shared.get(for: url) {
            self.image = cached
            return
        }

        guard !isLoading else { return }
        isLoading = true

        Task {
            if let loaded = await fetchWithFallback(url: url) {
                LuminaImageCache.shared.set(loaded, for: url)
                await MainActor.run {
                    withAnimation(.easeIn(duration: 0.2)) {
                        self.image = loaded
                    }
                    self.isLoading = false
                }
            } else {
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }

    private func fetchWithFallback(url: String) async -> UIImage? {
        // Attempt 1: Direct Fetch with mobile User-Agent
        if let targetUrl = URL(string: url) {
            var request = URLRequest(url: targetUrl)
            request.timeoutInterval = 7
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148", forHTTPHeaderField: "User-Agent")
            if let (data, resp) = try? await URLSession.shared.data(for: request),
               let http = resp as? HTTPURLResponse, http.statusCode == 200,
               let img = UIImage(data: data) {
                return img
            }
        }

        // Attempt 2: Fallback through Cloudflare / wsrv proxy (bypasses Israeli cellular ISP blocks for image.tmdb.org)
        if url.contains("image.tmdb.org"),
           let encoded = url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
           let proxyUrl = URL(string: "https://wsrv.nl/?url=\(encoded)") {
            var proxyRequest = URLRequest(url: proxyUrl)
            proxyRequest.timeoutInterval = 10
            if let (data, resp) = try? await URLSession.shared.data(for: proxyRequest),
               let http = resp as? HTTPURLResponse, http.statusCode == 200,
               let img = UIImage(data: data) {
                return img
            }
        }

        return nil
    }
}
