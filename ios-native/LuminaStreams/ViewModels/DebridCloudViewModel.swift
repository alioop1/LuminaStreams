import Foundation
import SwiftUI

@MainActor
final class DebridCloudViewModel: ObservableObject {
    @Published var magnets: [DebridMagnetItem] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var selectedMagnetForFiles: DebridMagnetItem? = nil
    @Published var isUnlocking: Bool = false
    @Published var activePlayback: PlaybackRequest? = nil

    func loadMagnets() async {
        isLoading = true
        errorMessage = nil
        do {
            let list = try await AllDebridService.shared.fetchUserMagnets()
            self.magnets = list
            self.isLoading = false
        } catch {
            self.isLoading = false
            self.errorMessage = error.localizedDescription
        }
    }

    func playFile(_ file: DebridFileItem, magnetTitle: String) async {
        guard !isUnlocking else { return }
        isUnlocking = true
        do {
            let streamUrl = try await AllDebridService.shared.unlockLink(file.link)
            isUnlocking = false
            selectedMagnetForFiles = nil
            activePlayback = PlaybackRequest(
                streamUrl: streamUrl,
                title: magnetTitle,
                subtitle: file.name,
                imdbId: "",
                tmdbId: "",
                posterUrl: "",
                backdropUrl: "",
                isSeries: false,
                season: nil,
                episode: nil,
                resumePositionSeconds: 0
            )
        } catch {
            isUnlocking = false
            errorMessage = error.localizedDescription
        }
    }

    func deleteMagnet(_ item: DebridMagnetItem) async {
        try? await AllDebridService.shared.deleteMagnet(id: item.id)
        magnets.removeAll { $0.id == item.id }
    }
}
