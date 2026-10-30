import SwiftUI
import AVFoundation

@main
struct LuminaStreamsApp: App {
    @StateObject private var settings = SettingsManager.shared
    @StateObject private var watchProgress = WatchProgressManager.shared

    init() {
        // Video must be audible even with the ring/silent switch on.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    var body: some Scene {
        WindowGroup {
            MainContainerView()
                .environmentObject(settings)
                .environmentObject(watchProgress)
                .environment(\.layoutDirection, settings.isRTL ? .rightToLeft : .leftToRight)
                .preferredColorScheme(.dark)
        }
    }
}
