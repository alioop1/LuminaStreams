import SwiftUI
import UIKit
import VLCKitSPM

/// SwiftUI-facing wrapper around MobileVLCKit's `VLCMediaPlayer`.
/// VLC plays what Android's Media3+FFmpeg stack plays (MKV, HEVC, AC3/EAC3/DTS/TrueHD,
/// embedded SRT/ASS/PGS), which AVPlayer rejects.
final class VLCPlaybackController: NSObject, ObservableObject, VLCMediaPlayerDelegate {
    struct MediaTrack: Identifiable, Hashable {
        let id: Int32
        let name: String
    }

    let mediaPlayer = VLCMediaPlayer()

    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = true
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var isSeekable = false
    @Published private(set) var errorMessage: String? = nil
    @Published private(set) var audioTracks: [MediaTrack] = []
    @Published private(set) var subtitleTracks: [MediaTrack] = []
    @Published private(set) var currentAudioTrack: Int32 = -1
    @Published private(set) var currentSubtitleTrack: Int32 = -1
    @Published private(set) var rate: Float = 1.0

    private var currentUrl: URL? = nil

    /// Live streams report no length.
    var isLive: Bool { duration <= 0 }

    override init() {
        super.init()
        mediaPlayer.delegate = self
    }

    // ── Loading ────────────────────────────────────────────────────────────
    func load(url: URL, startAt seconds: Double = 0) {
        currentUrl = url
        let media = VLCMedia(url: url)
        media.addOption(":network-caching=3000")
        media.addOption(":http-reconnect")
        if seconds > 1 {
            media.addOption(":start-time=\(Int(seconds))")
        }
        errorMessage = nil
        isBuffering = true
        mediaPlayer.media = media
        mediaPlayer.play()
    }

    /// Reload the same URL from the last known position (error recovery).
    func retry() {
        guard let url = currentUrl else { return }
        let resumeAt = currentTime
        mediaPlayer.stop()
        load(url: url, startAt: resumeAt)
    }

    func stop() {
        mediaPlayer.stop()
        isPlaying = false
    }

    // ── Transport ──────────────────────────────────────────────────────────
    func togglePlayPause() {
        if mediaPlayer.isPlaying {
            mediaPlayer.pause()
        } else {
            mediaPlayer.play()
        }
    }

    func pause() {
        if mediaPlayer.isPlaying { mediaPlayer.pause() }
    }

    func seek(to seconds: Double) {
        guard mediaPlayer.isSeekable else { return }
        let upper = duration > 0 ? duration : seconds
        let target = min(max(0, seconds), upper)
        mediaPlayer.time = VLCTime(int: Int32(target * 1000))
        currentTime = target
    }

    func seek(by delta: Double) {
        seek(to: currentTime + delta)
    }

    func setRate(_ newRate: Float) {
        mediaPlayer.rate = newRate
        rate = newRate
    }

    // ── Tracks ─────────────────────────────────────────────────────────────
    func selectAudioTrack(_ id: Int32) {
        mediaPlayer.currentAudioTrackIndex = id
        currentAudioTrack = mediaPlayer.currentAudioTrackIndex
    }

    /// Pass -1 to turn embedded subtitles off.
    func selectSubtitleTrack(_ id: Int32) {
        mediaPlayer.currentVideoSubTitleIndex = id
        currentSubtitleTrack = mediaPlayer.currentVideoSubTitleIndex
    }

    private func refreshTracks() {
        audioTracks = Self.tracks(names: mediaPlayer.audioTrackNames, indexes: mediaPlayer.audioTrackIndexes)
        subtitleTracks = Self.tracks(names: mediaPlayer.videoSubTitlesNames, indexes: mediaPlayer.videoSubTitlesIndexes)
        currentAudioTrack = mediaPlayer.currentAudioTrackIndex
        currentSubtitleTrack = mediaPlayer.currentVideoSubTitleIndex
    }

    private static func tracks(names: [Any], indexes: [Any]) -> [MediaTrack] {
        zip(indexes, names).compactMap { index, name in
            // VLC lists a "Disable" pseudo-track with index -1.
            guard let id = (index as? NSNumber)?.int32Value, id >= 0 else { return nil }
            return MediaTrack(id: id, name: (name as? String) ?? "Track \(id)")
        }
    }

    // ── VLCMediaPlayerDelegate ─────────────────────────────────────────────
    func mediaPlayerStateChanged(_ aNotification: Notification) {
        DispatchQueue.main.async { [weak self] in
            self?.syncState()
        }
    }

    func mediaPlayerTimeChanged(_ aNotification: Notification) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.currentTime = Double(self.mediaPlayer.time.intValue) / 1000
            if let length = self.mediaPlayer.media?.length.intValue, length > 0 {
                self.duration = Double(length) / 1000
            }
            self.isSeekable = self.mediaPlayer.isSeekable
            self.isPlaying = self.mediaPlayer.isPlaying
            self.isBuffering = false
            if self.audioTracks.isEmpty { self.refreshTracks() }
        }
    }

    private func syncState() {
        switch mediaPlayer.state {
        case .playing:
            isBuffering = false
            refreshTracks()
        case .error:
            isBuffering = false
            errorMessage = SettingsManager.shared.tr(
                "לא ניתן להפעיל את המקור. נסה שוב או בחר מקור אחר.",
                "This source could not be played. Retry or pick another source."
            )
        case .ended, .stopped, .paused:
            isBuffering = false
        default:
            // Opening / buffering / elementary-stream-added.
            refreshTracks()
        }
        isPlaying = mediaPlayer.isPlaying
    }
}

/// The view VLC renders video into.
struct VLCVideoSurface: UIViewRepresentable {
    let controller: VLCPlaybackController

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        controller.mediaPlayer.drawable = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
