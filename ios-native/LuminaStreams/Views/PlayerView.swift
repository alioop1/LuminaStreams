import SwiftUI

struct PlayerView: View {
    let request: PlaybackRequest
    @EnvironmentObject var settings: SettingsManager
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    @StateObject private var playback = VLCPlaybackController()

    @State private var showControls: Bool = true
    @State private var lastInteraction = Date()
    @State private var isScrubbing = false
    @State private var scrubValue: Double = 0

    @State private var subtitles: [SubtitleCue] = []
    @State private var currentSubtitleText: String = ""
    @State private var subtitleOffsetSeconds: Double = 0.0
    @State private var externalSubLang: String? = nil
    @State private var isLoadingSubtitles: Bool = false
    @State private var showTracksSheet: Bool = false
    @State private var userPickedSubtitles = false

    @State private var showResumeBanner = false
    @State private var lastSavedAt = Date.distantPast

    private let ticker = Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()
    private static let speeds: [Float] = [0.75, 1.0, 1.25, 1.5, 2.0]

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VLCVideoSurface(controller: playback)
                .ignoresSafeArea()
                .allowsHitTesting(false)

            // Tap target above the video so VLC's own views never swallow taps.
            Color.clear
                .contentShape(Rectangle())
                .ignoresSafeArea()
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.2)) { showControls.toggle() }
                    lastInteraction = Date()
                }

            if playback.isBuffering && playback.errorMessage == nil {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(LuminaTheme.cyan)
                    .scaleEffect(1.6)
            }

            // ── External subtitle overlay (Android style: no box, drop shadow) ──
            if externalSubLang != nil && !currentSubtitleText.isEmpty {
                VStack {
                    Spacer()
                    Text(currentSubtitleText)
                        .font(.system(size: settings.subtitleFontSize, weight: .bold))
                        .foregroundColor(settings.yellowSubtitles ? LuminaTheme.gold : .white)
                        .multilineTextAlignment(.center)
                        .shadow(color: .black, radius: 1, x: 1, y: 1)
                        .shadow(color: .black.opacity(0.9), radius: 3)
                        .padding(.bottom, showControls ? 110 : 36)
                        .padding(.horizontal, 24)
                }
                .allowsHitTesting(false)
            }

            if showControls {
                controlsOverlay
            }

            if showResumeBanner {
                resumeBanner
            }

            if let error = playback.errorMessage {
                errorOverlay(error)
            }
        }
        .statusBarHidden(!showControls)
        .onAppear {
            if let url = URL(string: request.streamUrl) {
                playback.load(url: url, startAt: request.resumePositionSeconds)
            }
            if request.resumePositionSeconds > 30 {
                showResumeBanner = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 6) {
                    withAnimation { showResumeBanner = false }
                }
            }
            let preferred = settings.defaultSubtitles.lowercased()
            if preferred != "none" && preferred != "off" {
                loadSubtitles(lang: preferred.hasPrefix("en") ? "eng" : "heb")
            }
        }
        .onDisappear {
            saveProgress()
            playback.stop()
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active { saveProgress() }
        }
        .onReceive(ticker) { _ in tick() }
        .onChange(of: playback.subtitleTracks) { _ in applyDefaultSubtitles() }
        .sheet(isPresented: $showTracksSheet) {
            tracksSheet
                .presentationDetents([.medium, .large])
        }
    }

    // ── Controls ───────────────────────────────────────────────────────────
    private var controlsOverlay: some View {
        VStack {
            HStack(spacing: 12) {
                Button {
                    closePlayer()
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
                            .lineLimit(1)
                    }
                }

                Spacer()

                Button {
                    showTracksSheet = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "captions.bubble.fill")
                        Text(subtitleOffsetSeconds == 0
                             ? settings.tr("שמע וכתוביות", "Audio & Subs")
                             : String(format: "%+.1fs", subtitleOffsetSeconds))
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(.black)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(LuminaTheme.cyan)
                    .clipShape(Capsule())
                }

                externalPlayerMenu
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .background(LinearGradient(colors: [.black.opacity(0.8), .clear], startPoint: .top, endPoint: .bottom))

            Spacer()

            VStack(spacing: 10) {
                HStack(spacing: 28) {
                    if !playback.isLive {
                        Button { playback.seek(by: -10); touch() } label: {
                            Image(systemName: "gobackward.10")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }

                    Button { playback.togglePlayPause(); touch() } label: {
                        Image(systemName: playback.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 46))
                            .foregroundColor(LuminaTheme.cyan)
                    }

                    if !playback.isLive {
                        Button { playback.seek(by: 10); touch() } label: {
                            Image(systemName: "goforward.10")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white)
                        }

                        // Skip intro (Android: +85 s)
                        Button { playback.seek(by: 85); touch() } label: {
                            Text(settings.tr("דלג פתיח", "Skip intro"))
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.white.opacity(0.18))
                                .clipShape(Capsule())
                        }
                    }
                }

                if playback.isLive {
                    HStack(spacing: 6) {
                        Circle().fill(LuminaTheme.red).frame(width: 8, height: 8)
                        Text("LIVE")
                            .font(.system(size: 12, weight: .black))
                            .foregroundColor(.white)
                    }
                } else {
                    HStack(spacing: 10) {
                        Text(formatTime(isScrubbing ? scrubValue : playback.currentTime))
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundColor(.white.opacity(0.8))

                        Slider(
                            value: Binding(
                                get: { isScrubbing ? scrubValue : playback.currentTime },
                                set: { scrubValue = $0 }
                            ),
                            in: 0...max(playback.duration, 1),
                            onEditingChanged: { editing in
                                if editing {
                                    scrubValue = playback.currentTime
                                    isScrubbing = true
                                } else {
                                    playback.seek(to: scrubValue)
                                    isScrubbing = false
                                }
                                touch()
                            }
                        )
                        .tint(LuminaTheme.cyan)

                        Text(formatTime(playback.duration))
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 22)
            .background(LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .top, endPoint: .bottom))
        }
    }

    private var externalPlayerMenu: some View {
        let encoded = request.streamUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? request.streamUrl
        return Menu {
            if let vlcUrl = URL(string: "vlc-x-callback://x-callback-url/stream?url=\(encoded)") {
                Link(settings.tr("פתח ב-VLC", "Open in VLC"), destination: vlcUrl)
            }
            if let infuseUrl = URL(string: "infuse://x-callback-url/play?url=\(encoded)") {
                Link(settings.tr("פתח ב-Infuse", "Open in Infuse"), destination: infuseUrl)
            }
            if let directUrl = URL(string: request.streamUrl) {
                ShareLink(item: directUrl) {
                    Label(settings.tr("שתף קישור ישיר", "Share direct link"), systemImage: "square.and.arrow.up")
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

    private var resumeBanner: some View {
        VStack {
            HStack(spacing: 12) {
                Text(settings.tr("ממשיך מ-", "Resuming from ") + formatTime(request.resumePositionSeconds))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                Button {
                    playback.seek(to: 0)
                    withAnimation { showResumeBanner = false }
                } label: {
                    Text(settings.tr("התחל מההתחלה", "Start over"))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(LuminaTheme.cyan)
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(LuminaTheme.glassBg)
            .clipShape(Capsule())
            .padding(.top, 70)
            Spacer()
        }
        .transition(.opacity)
    }

    private func errorOverlay(_ message: String) -> some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 36))
                .foregroundColor(LuminaTheme.gold)
            Text(message)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            HStack(spacing: 12) {
                Button {
                    playback.retry()
                } label: {
                    Text(settings.tr("נסה שוב", "Retry"))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(LuminaTheme.cyan)
                        .clipShape(Capsule())
                }
                Button {
                    closePlayer()
                } label: {
                    Text(settings.tr("חזרה", "Back"))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(Color.white.opacity(0.18))
                        .clipShape(Capsule())
                }
            }
        }
        .padding(24)
        .background(LuminaTheme.glassBg)
        .cornerRadius(18)
        .padding(32)
    }

    // ── Audio / subtitle / speed sheet ─────────────────────────────────────
    private var tracksSheet: some View {
        ZStack {
            LuminaTheme.blackBg.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    HStack {
                        Text(settings.tr("שמע וכתוביות", "Audio & Subtitles"))
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.white)
                        Spacer()
                        Button {
                            showTracksSheet = false
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 26))
                                .foregroundColor(.white.opacity(0.75))
                        }
                        .accessibilityLabel(settings.tr("סגור", "Close"))
                    }

                    sheetSection(settings.tr("שמע", "Audio")) {
                        if playback.audioTracks.isEmpty {
                            sheetHint(settings.tr("אין רצועות שמע נוספות", "No alternate audio tracks"))
                        }
                        ForEach(playback.audioTracks) { track in
                            sheetRow(track.name, selected: track.id == playback.currentAudioTrack) {
                                playback.selectAudioTrack(track.id)
                            }
                        }
                    }

                    sheetSection(settings.tr("כתוביות", "Subtitles")) {
                        sheetRow(settings.tr("כבוי", "Off"),
                                 selected: externalSubLang == nil && playback.currentSubtitleTrack < 0) {
                            userPickedSubtitles = true
                            externalSubLang = nil
                            playback.selectSubtitleTrack(-1)
                        }
                        ForEach([("heb", "🇮🇱 עברית"), ("eng", "🇬🇧 English"), ("rus", "🇷🇺 Русский")], id: \.0) { code, label in
                            sheetRow(label + (isLoadingSubtitles && externalSubLang == code ? " …" : ""),
                                     selected: externalSubLang == code) {
                                userPickedSubtitles = true
                                playback.selectSubtitleTrack(-1)
                                loadSubtitles(lang: code)
                            }
                        }
                        ForEach(playback.subtitleTracks) { track in
                            sheetRow("▸ " + track.name, selected: externalSubLang == nil && track.id == playback.currentSubtitleTrack) {
                                userPickedSubtitles = true
                                externalSubLang = nil
                                playback.selectSubtitleTrack(track.id)
                            }
                        }
                    }

                    sheetSection(String(format: settings.tr("סנכרון כתוביות: %+.2f ש׳", "Subtitle sync: %+.2fs"), subtitleOffsetSeconds)) {
                        ForEach([[-1.0, -0.5, -0.1], [0.1, 0.5, 1.0]], id: \.self) { row in
                            HStack(spacing: 10) {
                                ForEach(row, id: \.self) { delta in
                                    offsetButton(String(format: "%+.1fs", delta)) {
                                        subtitleOffsetSeconds = min(15, max(-15, subtitleOffsetSeconds + delta))
                                    }
                                }
                            }
                        }
                        offsetButton(settings.tr("איפוס", "Reset")) { subtitleOffsetSeconds = 0 }
                    }

                    if !playback.isLive {
                        sheetSection(settings.tr("מהירות", "Speed")) {
                            HStack(spacing: 8) {
                                ForEach(Self.speeds, id: \.self) { speed in
                                    offsetButton(speed == 1.0 ? "1x" : String(format: "%gx", speed),
                                                 highlighted: playback.rate == speed) {
                                        playback.setRate(speed)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
        }
    }

    private func sheetSection<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 16, weight: .black))
                .foregroundColor(.white)
            content()
        }
    }

    private func sheetHint(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundColor(LuminaTheme.textSecondary)
    }

    private func sheetRow(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                Spacer()
                if selected {
                    Image(systemName: "checkmark")
                        .foregroundColor(LuminaTheme.cyan)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(selected ? LuminaTheme.cyan.opacity(0.15) : LuminaTheme.cardSurface)
            .cornerRadius(10)
        }
    }

    private func offsetButton(_ title: String, highlighted: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(highlighted ? Color.white : LuminaTheme.cyan)
                .cornerRadius(8)
        }
    }

    // ── Logic ──────────────────────────────────────────────────────────────
    private func loadSubtitles(lang: String) {
        guard request.imdbId.hasPrefix("tt") else { return }
        externalSubLang = lang
        isLoadingSubtitles = true
        Task {
            let cues = await SubtitleService.shared.fetchSubtitles(
                imdbId: request.imdbId,
                season: request.season,
                episode: request.episode,
                langCode: lang
            )
            // Ignore a late result if the user switched language meanwhile.
            if externalSubLang == lang {
                subtitles = cues
                if cues.isEmpty { externalSubLang = nil }
            }
            isLoadingSubtitles = false
            applyDefaultSubtitles()
        }
    }

    /// Android behaviour: the preferred language from the web first, then a matching
    /// track inside the file, otherwise subtitles off. VLC would otherwise turn on
    /// whatever embedded track the file marks as default (often English).
    private func applyDefaultSubtitles() {
        guard !userPickedSubtitles, !isLoadingSubtitles else { return }
        if externalSubLang != nil && !subtitles.isEmpty {
            playback.selectSubtitleTrack(-1)
            return
        }
        let preferred = settings.defaultSubtitles.lowercased()
        let wanted: [String] = preferred.hasPrefix("en")
            ? ["english", "eng"]
            : ["hebrew", "heb", "עברית"]
        if !preferred.hasPrefix("none") && !preferred.hasPrefix("off"),
           let match = playback.subtitleTracks.first(where: { track in
               let name = track.name.lowercased()
               return wanted.contains { name.contains($0) }
           }) {
            playback.selectSubtitleTrack(match.id)
        } else if !playback.subtitleTracks.isEmpty {
            playback.selectSubtitleTrack(-1)
        }
    }

    private func tick() {
        let adjusted = playback.currentTime - subtitleOffsetSeconds
        if let cue = subtitles.first(where: { adjusted >= $0.startTime && adjusted <= $0.endTime }) {
            currentSubtitleText = cue.text
        } else {
            currentSubtitleText = ""
        }

        // Android saves progress every 5 s while playing.
        if playback.isPlaying && Date().timeIntervalSince(lastSavedAt) >= 5 {
            saveProgress()
        }

        // Auto-hide controls after 4 s of no interaction while playing.
        if showControls && playback.isPlaying && !isScrubbing && !showTracksSheet
            && Date().timeIntervalSince(lastInteraction) > 4 {
            withAnimation(.easeInOut(duration: 0.3)) { showControls = false }
        }
    }

    private func touch() {
        lastInteraction = Date()
    }

    private func saveProgress() {
        lastSavedAt = Date()
        guard !playback.isLive, playback.duration > 10, playback.currentTime > 0 else { return }
        let key: String
        if let s = request.season, let e = request.episode {
            key = WatchProgressManager.shared.episodeKey(request.imdbId, season: s, episode: e)
        } else {
            key = WatchProgressManager.shared.movieKey(request.imdbId)
        }
        WatchProgressManager.shared.save(
            key: key,
            positionSeconds: playback.currentTime,
            durationSeconds: playback.duration,
            meta: WatchProgressMeta(
                tmdbId: request.tmdbId,
                title: request.title,
                posterUrl: request.posterUrl,
                backdropUrl: request.backdropUrl
            )
        )
    }

    private func closePlayer() {
        saveProgress()
        playback.stop()
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
