import Foundation
import SwiftUI

@MainActor
final class SearchViewModel: ObservableObject {
    @Published var query: String = ""
    @Published var selectedFilter: String = "all" // all, movie, tv, fuzer
    @Published var results: [Movie] = []
    @Published var isSearching: Bool = false
    private var searchTask: Task<Void, Never>? = nil

    var filteredResults: [Movie] {
        switch selectedFilter {
        case "movie": return results.filter { $0.mediaType == "movie" }
        case "tv": return results.filter { $0.mediaType == "tv" }
        case "fuzer": return results.filter { $0.mediaType == "fuzer" }
        default: return results
        }
    }

    func onQueryChanged(_ newValue: String) {
        searchTask?.cancel()
        let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            results = []
            isSearching = false
            return
        }

        isSearching = true
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }

            async let tmdbRes = try? TmdbService.shared.searchMulti(query: trimmed)
            async let fuzerRes = try? FuzerService.shared.search(query: trimmed)

            let tmdb = await tmdbRes ?? []
            let fuzer = await fuzerRes ?? []
            guard !Task.isCancelled else { return }

            self.results = tmdb + fuzer
            self.isSearching = false
        }
    }
}
