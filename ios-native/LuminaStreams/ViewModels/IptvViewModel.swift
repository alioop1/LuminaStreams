import Foundation
import SwiftUI

@MainActor
final class IptvViewModel: ObservableObject {
    @Published var allChannels: [IptvChannel] = []
    @Published var groups: [String] = []
    @Published var selectedGroup: String = "הכל"
    @Published var searchQuery: String = ""
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var activeChannel: IptvChannel? = nil

    var filteredChannels: [IptvChannel] {
        var list = allChannels
        if selectedGroup != "הכל" && selectedGroup != "All" {
            list = list.filter { $0.group == selectedGroup }
        }
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        if !q.isEmpty {
            list = list.filter { $0.name.localizedCaseInsensitiveContains(q) || $0.group.localizedCaseInsensitiveContains(q) }
        }
        return list
    }

    func loadPlaylist() async {
        isLoading = true
        errorMessage = nil
        do {
            let customUrl = SettingsManager.shared.iptvM3uUrl
            let channels = try await IptvService.shared.fetchAndParseM3u(urlStr: customUrl)
            self.allChannels = channels
            let uniqueGroups = Array(Set(channels.map { $0.group })).sorted()
            self.groups = ["הכל"] + uniqueGroups
            self.isLoading = false
        } catch {
            self.isLoading = false
            self.errorMessage = error.localizedDescription
        }
    }
}
