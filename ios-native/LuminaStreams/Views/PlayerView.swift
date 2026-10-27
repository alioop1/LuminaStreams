import SwiftUI
import AVKit

struct PlayerView: View {
    let request: PlaybackRequest
    @EnvironmentObject var settings: SettingsManager
    @Environment(\.dismiss) private var dismiss

    @State private var player: AVPlayer? = nil
    @State private var isPlaying: Bool = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 1
    @State private var showControls: Bool = true
    @State private var subtitles: [SubtitleCue] = []
    @State private var currentSubtitleText: String = ""
    @State private var subtitleOffsetSeconds: Double = 0.0
    @State private var subtitlesEnabled: Bool = true
    @State private var showSubtitleSheet: Bool = false
    @State private var isLoadingSubtitles: Bool = false

    private let ticker = Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let player = player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            showControls.toggle()
                        }
                    }
            }

            // ── Live Subtitle Overlay (With Offset Synchronization) ─────────
            if subtitlesEnabled && !currentSubtitleText.isEmpty {
                VStack {
                    Spacer()
                    Text(currentSubtitleText)
                        .font(.system(size: settings.subtitleFontSize, weight: .bold))
                        .foregroundColor(settings.yellowSubtitles ? LuminaTheme.gold : .white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(8)
                        .padding(.bottom, showControls ? 96 : 36)
                        .padding(.horizontal, 24)
                }
            }

            // ── Top & Bottom Custom OSD Controls ────────────────────────────
            if showControls {
                VStack {
                    // Top Bar
                    HStack(spacing: 12) {
                        Button {
                            saveProgressAndDismiss()
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 28))
                                .foregroundColor(.white)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(request.title)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            if !request.subtitle.isEmpty {
                                Text(request.subtitle)
                                    .font(.system(size: 12))
                                    .foregroundColor(LuminaTheme.cyan)
                            }
                        }

                        Spacer()

                        // Subtitle & Sync Button
                        Button {
                            showSubtitleSheet = true
                        } label: {
                            HStack(spacing: 5) {
                                Image(systemName: "captions.bubble.fill")
                                Text(subtitleOffsetSeconds == 0 ? "כתוביות" : String(format: "%+.1fs", subtitleOffsetSeconds))
                                    .font(.system(size: 12, weight: .bold))
                            }
                            .foregroundColor(.black)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(LuminaTheme.cyan)
                            .clipShape(Capsule())
                        }

                        // Open in External Player (VLC / Infuse) for heavy MKV / DTS codecs
                        Menu {
                            if let vlcUrl = URL(string: "vlc-x-callback://x-callback-url/stream?url=\(request.streamUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? request.streamUrl)") {
                                Link("פתח ב-VLC", destination: vlcUrl)
                            }
                            if let infuseUrl = URL(string: "infuse://x-callback-url/play?url=\(request.streamUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? request.streamUrl)") {
                                Link("פתח ב-Infuse", destination: infuseUrl)
                            }
                            if let directUrl = URL(string: request.streamUrl) {
                                ShareLink(item: directUrl) {
                                    Label("שתף קישור ישיר", systemImage: "square.and.arrow.up")
                                }
                            }
                        } label: {
                            Image(systemName: "arrow.up.right.video.fill")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .padding(9)
                                .background(Color.white.opacity(0.18))
                                .clipShape(Circle())
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .background(
                        LinearGradient(colors: [.black.opacity(0.8), .clear], startPoint: .top, endPoint: .bottom)
                    )

                    Spacer()

                    // Bottom Scrubber & Transport Controls
                    VStack(spacing: 10) {
                        HStack(spacing: 28) {
                            Button {
                                seekBy(-10)
                            } label: {
                                Image(systemName: "gobackward.10")
                                    .font(.system(size: 24, weight: .bold))
                                    .foregroundColor(.white)
                            }

                            Button {
                                togglePlayPause()
                            } label: {
                                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                    .font(.system(size: 46))
                                    .foregroundColor(LuminaTheme.cyan)
                            }

                            Button {
                                seekBy(10)
                            } label: {
                                Image(systemName: "goforward.10")
                                    .font(.system(size: 24, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }

                        HStack(spacing: 10) {
                            Text(formatTime(currentTime))
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(.white.opacity(0.8))

                            Slider(
                                value: Binding(
                                    get: { currentTime },
                                    set: { newVal in
                                        currentTime = newVal
                                        player?.seek(to: CMTime(seconds: newVal, preferredTimescale: 600))
                                    }
                                ),
                                in: 0...max(duration, 1)
                            )
                            .tint(LuminaTheme.cyan)

                            Text(formatTime(duration))
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(.white.opacity(0.8))
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 22)
                    .background(
                        LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .top, endPoint: .bottom)
                    )
                }
            }
        }
        .onAppear {
            setupPlayer()
            loadSubtitles(lang: settings.defaultSubtitles)
        }
        .onDisappear {
            player?.pause()
        }
        .onReceive(ticker) { _ in
            updateProgressTick()
        }
        .sheet(isPresented: $showSubtitleSheet) {
            subtitleSettingsSheet
                .presentationDetents([.height(320)])
        }
    }

    private var subtitleSettingsSheet: some View {
        ZStack {
            LuminaTheme.blackBg.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 18) {
                Text("כתוביות וסנכרון זמן (Subtitle Offset)")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(.white)

                Toggle("הצג כתוביות", isOn: $subtitlesEnabled)
                    .tint(LuminaTheme.cyan)
                    .foregroundColor(.white)

                // Language switcher
                HStack(spacing: 10) {
                    ForEach([("heb", "🇮🇱 עברית"), ("eng", "🇬🇧 English"), ("rus", "🇷🇺 Русский")], id: \.0) { code, label in
                        Button {
                            loadSubtitles(lang: code)
                        } label: {
                            Text(label)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(LuminaTheme.cardSurface)
                                .cornerRadius(8)
                        }
                    }
                }

                // Offset Synchronization (-0.5s / -0.1s / +0.1s / +0.5s)
                VStack(alignment: .leading, spacing: 8) {
                    Text(String(format: "היסט סנכרון נוכחי: %+.2f שניות", subtitleOffsetSeconds))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(LuminaTheme.cyan)

                    HStack(spacing: 10) {
                        ForEach([-0.5, -0.1, 0.0, +0.1, +0.5], id: \.self) { delta in
                            Button {
                                if delta == 0 {
                                    subtitleOffsetSeconds = 0
                                } else {
                                    subtitleOffsetSeconds += delta
                                }
                            } label: {
                                Text(delta == 0 ? "איפוס" : String(format: "%+.1fs", delta))
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(delta == 0 ? Color.white : LuminaTheme.cyan)
                                    .cornerRadius(8)
                            }
                        }
                    }
                }

                Spacer()
            }
            .padding(20)
        }
    }

    private func setupPlayer() {
        guard let url = URL(string: request.streamUrl) else { return }
        let avPlayer = AVPlayer(url: url)
        self.player = avPlayer
        if request.resumePositionSeconds > 5 {
            avPlayer.seek(to: CMTime(seconds: request.resumePositionSeconds, preferredTimescale: 600))
        }
        avPlayer.play()
        isPlaying = true
    }

    private func loadSubtitles(lang: String) {
        guard !request.imdbId.isEmpty else { return }
        isLoadingSubtitles = true
        Task {
            let cues = await SubtitleService.shared.fetchSubtitles(
                imdbId: request.imdbId,
                season: request.season,
                episode: request.episode,
                langCode: lang
            )
            self.subtitles = cues
            self.isLoadingSubtitles = false
        }
    }

    private func updateProgressTick() {
        guard let player = player else { return }
        let sec = player.currentTime().seconds
        if sec.isFinite && sec >= 0 {
            currentTime = sec
        }
        if let itemDur = player.currentItem?.duration.seconds, itemDur.isFinite && itemDur > 0 {
            duration = itemDur
        }

        // Match active subtitle cue with offset
        let adjustedTime = currentTime - subtitleOffsetSeconds
        if let cue = subtitles.first(where: { adjustedTime >= $0.startTime && adjustedTime <= $0.endTime }) {
            currentSubtitleText = cue.text
        } else {
            currentSubtitleText = ""
        }
    }

    private func togglePlayPause() {
        guard let player = player else { return }
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
        }
    }

    private func seekBy(_ delta: Double) {
        let target = max(0, min(duration, currentTime + delta))
        player?.seek(to: CMTime(seconds: target, preferredTimescale: 600))
        currentTime = target
    }

    private func saveProgressAndDismiss() {
        player?.pause()
        if !request.imdbId.isEmpty && duration > 10 {
            let key = (request.season != nil && request.episode != nil)
                ? WatchProgressManager.shared.episodeKey(request.imdbId, season: request.season!, episode: request.episode!)
                : WatchProgressManager.shared.movieKey(request.imdbId)
            WatchProgressManager.shared.saveProgress(
                key: key,
                tmdbId: request.tmdbId,
                title: request.title,
                posterUrl: request.posterUrl,
                backdropUrl: request.backdropUrl,
                mediaType: request.isSeries ? "tv" : "movie",
                season: request.season,
                episode: request.episode,
                positionSeconds: currentTime,
                durationSeconds: duration
            )
        }
        dismiss()
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds >= 0 else { return "00:00" }
        let total = Int(seconds)
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%02d:%02d", m, s)
    }
}
