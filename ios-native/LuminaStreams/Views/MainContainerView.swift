import SwiftUI

enum LuminaSection: String, CaseIterable, Identifiable {
    case home      = "home"
    case movies    = "movies"
    case series    = "series"
    case iptv      = "iptv"
    case fuzer     = "fuzer"
    case debrid    = "debrid"
    case watchlist = "watchlist"
    case search    = "search"
    case settings  = "settings"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .home:      return "house.fill"
        case .movies:    return "film.fill"
        case .series:    return "tv.fill"
        case .iptv:      return "antenna.radiowaves.left.and.right"
        case .fuzer:     return "sparkles"
        case .debrid:    return "cloud.fill"
        case .watchlist: return "heart.fill"
        case .search:    return "magnifyingglass"
        case .settings:  return "gearshape.fill"
        }
    }

    func title(isRTL: Bool) -> String {
        switch self {
        case .home:      return isRTL ? "בית" : "Home"
        case .movies:    return isRTL ? "סרטים" : "Movies"
        case .series:    return isRTL ? "סדרות" : "Series"
        case .iptv:      return "IPTV"
        case .fuzer:     return "Fuzer"
        case .debrid:    return "Debrid"
        case .watchlist: return isRTL ? "מועדפים" : "Watchlist"
        case .search:    return isRTL ? "חיפוש" : "Search"
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
            let isPad = UIDevice.current.userInterfaceIdiom == .pad

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
                    .padding(.top, isLandscape ? 64 : (isPad ? 74 : 100))

                    // Safe PS5 Top Bar with Dynamic Island Avoidance
                    ps5TopBar(isLandscape: isLandscape, isPad: isPad)
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

    // ── Safe PS5 Top Bar (Clean Dynamic Island Clearance & No Text Wrap) ────
    @ViewBuilder
    private func ps5TopBar(isLandscape: Bool, isPad: Bool) -> some View {
        VStack(spacing: 0) {
            if isLandscape || isPad {
                // Horizontal Single-Row Layout (Landscape / iPad)
                HStack(spacing: 14) {
                    brandLogoView
                    Spacer(minLength: 12)
                    actionButtonsScrollView
                    Spacer(minLength: 12)
                    liveClockView
                }
                .padding(.horizontal, isLandscape ? 48 : 24)
                .padding(.vertical, 8)
            } else {
                // 2-Row Vertical Layout (iPhone Portrait - Clears Dynamic Island & Prevents Cramping)
                VStack(spacing: 8) {
                    HStack {
                        brandLogoView
                        Spacer()
                        liveClockView
                    }
                    .padding(.horizontal, 16)

                    actionButtonsScrollView
                        .padding(.horizontal, 10)
                }
                .padding(.top, 4)
                .padding(.bottom, 8)
            }

            Divider().background(Color.white.opacity(0.08))
        }
        .background(
            LuminaTheme.blackBg.opacity(0.96)
                .background(.ultraThinMaterial)
                .ignoresSafeArea(edges: .top)
        )
    }

    private var brandLogoView: some View {
        HStack(spacing: 4) {
            Text("LUMINA")
                .font(.system(size: 17, weight: .black, design: .rounded))
                .foregroundColor(.white)
            Text("STREAMS")
                .font(.system(size: 17, weight: .light, design: .rounded))
                .foregroundColor(.white.opacity(0.75))
        }
        .fixedSize()
        .environment(\.layoutDirection, .leftToRight)
        .onTapGesture {
            withAnimation(.spring(response: 0.3)) { activeSection = .home }
        }
    }

    private var liveClockView: some View {
        Text(currentTimeString)
            .font(.system(size: 13, weight: .medium, design: .monospaced))
            .foregroundColor(.white.opacity(0.75))
            .fixedSize()
    }

    private var actionButtonsScrollView: some View {
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
                                .frame(width: 38, height: 38)
                                .overlay(
                                    Circle()
                                        .stroke(isSelected ? Color.white : Color.white.opacity(0.14), lineWidth: 1)
                                )
                                .shadow(color: isSelected ? Color.white.opacity(0.4) : .clear, radius: 8)

                            Image(systemName: section.icon)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(isSelected ? .black : .white)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
        }
    }

    private func updateClock() {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        currentTimeString = formatter.string(from: Date())
    }
}
