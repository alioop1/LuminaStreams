import SwiftUI

struct IptvView: View {
    @StateObject private var vm = IptvViewModel()
    @EnvironmentObject var settings: SettingsManager

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .foregroundColor(LuminaTheme.cyan)
                Text(settings.tr("שידורים חיים (IPTV)", "Live TV Channels"))
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(.white)
                Spacer()
                Button {
                    Task { await vm.loadPlaylist() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundColor(LuminaTheme.cyan)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            // Search Channels
            TextField(settings.tr("חפש ערוץ...", "Search channel..."), text: $vm.searchQuery)
                .textFieldStyle(.plain)
                .padding(10)
                .background(LuminaTheme.cardSurface)
                .cornerRadius(10)
                .foregroundColor(.white)
                .padding(.horizontal, 16)

            // Group Pills
            if !vm.groups.isEmpty {
                HScroll {
                    HStack(spacing: 8) {
                        ForEach(vm.groups, id: \.self) { grp in
                            let isSelected = vm.selectedGroup == grp
                            Button {
                                vm.selectedGroup = grp
                            } label: {
                                Text(grp)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(isSelected ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(Capsule().fill(isSelected ? LuminaTheme.cyan : LuminaTheme.cardSurface))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }

            if vm.isLoading {
                Spacer()
                HStack {
                    Spacer()
                    ProgressView("טוען רשימת ערוצים חיים...")
                        .tint(LuminaTheme.cyan)
                        .foregroundColor(.white)
                    Spacer()
                }
                Spacer()
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 8) {
                        ForEach(vm.filteredChannels) { channel in
                            Button {
                                vm.activeChannel = channel
                            } label: {
                                HStack(spacing: 12) {
                                    LuminaAsyncImage(url: channel.logoUrl) { img in
                                        img.resizable().aspectRatio(contentMode: .fit)
                                    } placeholder: {
                                        Image(systemName: "tv.fill")
                                            .foregroundColor(LuminaTheme.cyan)
                                    }
                                    .frame(width: 44, height: 44)
                                    .background(Color.black.opacity(0.35))
                                    .cornerRadius(8)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(channel.name)
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(.white)
                                        Text(channel.group)
                                            .font(.system(size: 11))
                                            .foregroundColor(LuminaTheme.cyan)
                                    }

                                    Spacer()

                                    HStack(spacing: 4) {
                                        Circle().fill(LuminaTheme.red).frame(width: 7, height: 7)
                                        Text("LIVE")
                                            .font(.system(size: 10, weight: .black))
                                            .foregroundColor(.white)
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.white.opacity(0.1))
                                    .cornerRadius(6)
                                }
                                .padding(10)
                                .background(LuminaTheme.cardSurface)
                                .cornerRadius(12)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 30)
                }
            }
        }
        .task {
            if vm.allChannels.isEmpty {
                await vm.loadPlaylist()
            }
        }
        .fullScreenCover(item: $vm.activeChannel) { ch in
            PlayerView(request: PlaybackRequest(
                streamUrl: ch.streamUrl,
                title: ch.name,
                subtitle: "LIVE • \(ch.group)",
                imdbId: "",
                tmdbId: ch.id,
                posterUrl: ch.logoUrl,
                backdropUrl: "",
                isSeries: false,
                season: nil,
                episode: nil,
                resumePositionSeconds: 0
            ))
        }
    }
}
