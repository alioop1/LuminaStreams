import SwiftUI

@main
struct LuminaStreamsApp: App {
    @StateObject private var settings = SettingsManager.shared
    @StateObject private var watchProgress = WatchProgressManager.shared

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
