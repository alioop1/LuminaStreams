import SwiftUI

struct DebridCloudView: View {
    @StateObject private var vm = DebridCloudViewModel()
    @EnvironmentObject var settings: SettingsManager

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Image(systemName: "cloud.fill")
                    .foregroundColor(LuminaTheme.cyan)
                Text(settings.tr("הענן שלי ב-AllDebrid", "AllDebrid Cloud Library"))
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(.white)
                Spacer()
                Button {
                    Task { await vm.loadMagnets() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(LuminaTheme.cyan)
                        .padding(8)
                        .background(LuminaTheme.cardSurface)
                        .clipShape(Circle())
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            if vm.isLoading {
                Spacer()
                HStack {
                    Spacer()
                    ProgressView("טוען את ספריית הענן מ-AllDebrid...")
                        .tint(LuminaTheme.cyan)
                        .foregroundColor(.white)
                    Spacer()
                }
                Spacer()
            } else if vm.magnets.isEmpty {
                Spacer()
                HStack {
                    Spacer()
                    VStack(spacing: 10) {
                        Image(systemName: "cloud")
                            .font(.system(size: 44))
                            .foregroundColor(.white.opacity(0.3))
                        Text(settings.tr("אין טורנטים שמורים בענן כרגע", "No magnets in your AllDebrid cloud"))
                            .foregroundColor(.white.opacity(0.7))
                    }
                    Spacer()
                }
                Spacer()
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 10) {
                        ForEach(vm.magnets) { magnet in
                            HStack(spacing: 12) {
                                Image(systemName: magnet.isReady ? "checkmark.icloud.fill" : "arrow.down.circle")
                                    .font(.system(size: 24))
                                    .foregroundColor(magnet.isReady ? LuminaTheme.green : LuminaTheme.gold)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(magnet.displayTitle)
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(2)

                                    HStack(spacing: 8) {
                                        Text(magnet.formattedSize)
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(LuminaTheme.cyan)
                                        Text(magnet.status)
                                            .font(.system(size: 11))
                                            .foregroundColor(.white.opacity(0.7))
                                        Text("\(magnet.files.count) קבצים")
                                            .font(.system(size: 11))
                                            .foregroundColor(.white.opacity(0.6))
                                    }
                                }

                                Spacer()

                                if magnet.isReady && !magnet.files.isEmpty {
                                    Button {
                                        if magnet.files.count == 1, let first = magnet.files.first {
                                            Task { await vm.playFile(first, magnetTitle: magnet.displayTitle) }
                                        } else {
                                            vm.selectedMagnetForFiles = magnet
                                        }
                                    } label: {
                                        Image(systemName: "play.circle.fill")
                                            .font(.system(size: 28))
                                            .foregroundColor(LuminaTheme.cyan)
                                    }
                                    .buttonStyle(.plain)
                                }

                                Button {
                                    Task { await vm.deleteMagnet(magnet) }
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 15))
                                        .foregroundColor(LuminaTheme.red.opacity(0.85))
                                        .padding(6)
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(12)
                            .background(LuminaTheme.cardSurface)
                            .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 30)
                }
            }
        }
        .task {
            await vm.loadMagnets()
        }
        .sheet(item: $vm.selectedMagnetForFiles) { magnet in
            ZStack {
                LuminaTheme.blackBg.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 14) {
                    Text(magnet.displayTitle)
                        .font(.system(size: 16, weight: .black))
                        .foregroundColor(.white)
                        .padding(.top, 16)

                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(magnet.files) { file in
                                Button {
                                    Task { await vm.playFile(file, magnetTitle: magnet.displayTitle) }
                                } label: {
                                    HStack {
                                        Image(systemName: "play.fill")
                                            .foregroundColor(LuminaTheme.cyan)
                                        Text(file.name)
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.white)
                                            .lineLimit(2)
                                        Spacer()
                                        Text(file.formattedSize)
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(LuminaTheme.cyan)
                                    }
                                    .padding(12)
                                    .background(LuminaTheme.cardSurface)
                                    .cornerRadius(10)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .presentationDetents([.medium, .large])
        }
        .fullScreenCover(item: $vm.activePlayback) { playback in
            PlayerView(request: playback)
        }
    }
}
