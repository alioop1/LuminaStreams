import SwiftUI

enum LuminaSection: String, CaseIterable, Identifiable {
    case search    = "search"
    case home      = "home"
    case movies    = "movies"
    case series    = "series"
    case iptv      = "iptv"
    case fuzer     = "fuzer"
    case debrid    = "debrid"
    case watchlist = "watchlist"
    case settings  = "settings"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .search:    return "magnifyingglass"
        case .home:      return "house.fill"
        case .movies:    return "film.fill"
        case .series:    return "tv.fill"
        case .iptv:      return "antenna.radiowaves.left.and.right"
        case .fuzer:     return "sparkles"
        case .debrid:    return "cloud.fill"
        case .watchlist: return "heart.fill"
        case .settings:  return "gearshape.fill"
        }
    }

    func title(isRTL: Bool) -> String {
        switch self {
        case .search:    return isRTL ? "חיפוש" : "Search"
        case .home:      return isRTL ? "בית" : "Home"
        case .movies:    return isRTL ? "סרטים" : "Movies"
        case .series:    return isRTL ? "סדרות" : "Series"
        case .iptv:      return "IPTV"
        case .fuzer:     return "Fuzer"
        case .debrid:    return "Debrid"
        case .watchlist: return isRTL ? "מועדפים" : "Watchlist"
        case .settings:  return isRTL ? "הגדרות" : "Settings"
        }
    }
}

struct MainContainerView: View {
    @EnvironmentObject var settings: SettingsManager
    @EnvironmentObject var watchProgress: WatchProgressManager
    @StateObject private var homeVM = HomeViewModel()

    @State private var activeSection: LuminaSection = .home
    @State private var selectedMediaId: String? = nil
    @State private var currentTimeString: String = ""

    private let clockTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        GeometryReader { proxy in
            let isLandscape = proxy.size.width > proxy.size.height

            NavigationStack {
                ZStack(alignment: .top) {
                    LuminaTheme.blackBg.ignoresSafeArea()

                    // Main Content Area
                    Group {
                        switch activeSection {
                        case .home:
                            HomeView(
                                viewModel: homeVM,
                                tabMode: "home",
                                onSelectMedia: { movie in selectedMediaId = movie.displayId }
                            )
                        case .movies:
                            HomeView(
                                viewModel: homeVM,
                                tabMode: "movies",
                                onSelectMedia: { movie in selectedMediaId = movie.displayId }
                            )
                        case .series:
                            HomeView(
                                viewModel: homeVM,
                                tabMode: "series",
                                onSelectMedia: { movie in selectedMediaId = movie.displayId }
                            )
                        case .fuzer:
                            FuzerView(onSelectMedia: { movie in selectedMediaId = movie.displayId })
                        case .iptv:
                            IptvView()
                        case .debrid:
                            DebridCloudView()
                        case .watchlist:
                            WatchlistView(onSelectMedia: { movie in selectedMediaId = movie.displayId })
                        case .search:
                            SearchView(onSelectMedia: { movie in selectedMediaId = movie.displayId })
                        case .settings:
                            SettingsView()
                        }
                    }
                    .padding(.top, isLandscape ? 58 : 68)

                    // PS5 Top Nav Bar with High-Visibility Circular Glass Buttons
                    ps5TopBar(isLandscape: isLandscape)
                }
                .navigationDestination(
                    isPresented: Binding(
                        get: { selectedMediaId != nil },
                        set: { if !$0 { selectedMediaId = nil } }
                    )
                ) {
                    if let mediaId = selectedMediaId {
                        DetailsView(fullId: mediaId, onNavigateMedia: { newId in
                            selectedMediaId = newId
                        })
                    }
                }
            }
        }
        .onAppear { updateClock() }
        .onReceive(clockTimer) { _ in updateClock() }
    }

    // ── PS5 Top Bar with Pure Vector Shapes & Original Spacing ─────────────
    @ViewBuilder
    private func ps5TopBar(isLandscape: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                // Brand: LUMINA STREAMS
                HStack(spacing: 3) {
                    Text("LUMINA")
                        .font(.system(size: 16, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                    Text("STREAMS")
                        .font(.system(size: 16, weight: .light, design: .rounded))
                        .foregroundColor(.white.opacity(0.7))
                }
                .environment(\.layoutDirection, .leftToRight)
                .onTapGesture {
                    withAnimation(.spring(response: 0.3)) { activeSection = .home }
                }

                Spacer(minLength: 6)

                // Circular PS5 Action Icons (Exact Android TV Placement)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(LuminaSection.allCases) { section in
                            let isSelected = activeSection == section
                            Button {
                                withAnimation(.spring(response: 0.28, dampingFraction: 0.8)) {
                                    activeSection = section
                                    if section == .movies || section == .series {
                                        homeVM.selectedStudio = nil
                                        homeVM.studioCategories = []
                                    }
                                }
                            } label: {
                                ZStack {
                                    Circle()
                                        .fill(isSelected ? Color.white : Color.white.opacity(0.08))
                                        .frame(width: 36, height: 36)
                                        .overlay(
                                            Circle()
                                                .stroke(isSelected ? Color.white : Color.white.opacity(0.14), lineWidth: 1)
                                        )
                                        .shadow(color: isSelected ? Color.white.opacity(0.4) : .clear, radius: 8)

                                    Image(systemName: section.icon)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(isSelected ? .black : .white)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 4)
                    .padding(.vertical, 2)
                }

                // Live Clock
                Text(currentTimeString)
                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                    .foregroundColor(.white.opacity(0.75))
            }
            .padding(.horizontal, isLandscape ? 40 : 16)
            .padding(.top, 4)
            .padding(.bottom, 8)
            .background(
                LuminaTheme.blackBg.opacity(0.94)
                    .background(.ultraThinMaterial)
            )
            Divider().background(Color.white.opacity(0.08))
        }
    }

    private func updateClock() {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        currentTimeString = formatter.string(from: Date())
    }
}
