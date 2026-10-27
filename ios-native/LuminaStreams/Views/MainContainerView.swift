import SwiftUI

enum LuminaSection: String, CaseIterable, Identifiable {
    case home      = "home"
    case movies    = "movies"
    case series    = "series"
    case fuzer     = "fuzer"
    case iptv      = "iptv"
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
        case .fuzer:     return "sparkles"
        case .iptv:      return "antenna.radiowaves.left.and.right"
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
        case .fuzer:     return "Fuzer"
        case .iptv:      return "IPTV"
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
        NavigationStack {
            ZStack(alignment: .top) {
                LuminaTheme.blackBg.ignoresSafeArea()

                // ── Main Content Area ───────────────────────────────────────
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
                .padding(.top, 64)

                // ── PS5 Top Navigation Bar (Matches HomeNavigation.kt) ──────
                ps5TopBar
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
        .onAppear { updateClock() }
        .onReceive(clockTimer) { _ in updateClock() }
    }

    private var ps5TopBar: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                // Brand Logo (LTR fixed like Android TV)
                HStack(spacing: 4) {
                    Text("LUMINA")
                        .font(.system(size: 17, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                    Text("STREAMS")
                        .font(.system(size: 17, weight: .light, design: .rounded))
                        .foregroundColor(LuminaTheme.cyan)
                }
                .environment(\.layoutDirection, .leftToRight)
                .onTapGesture {
                    withAnimation(.spring(response: 0.3)) { activeSection = .home }
                }

                Spacer(minLength: 4)

                // Scrollable Circular PS5 Glass Buttons
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
                                HStack(spacing: 5) {
                                    Image(systemName: section.icon)
                                        .font(.system(size: 13, weight: .semibold))
                                    if isSelected {
                                        Text(section.title(isRTL: settings.isRTL))
                                            .font(.system(size: 12, weight: .bold))
                                    }
                                }
                                .foregroundColor(isSelected ? .black : .white.opacity(0.8))
                                .padding(.horizontal, isSelected ? 12 : 10)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule()
                                        .fill(isSelected ? LuminaTheme.cyan : Color.white.opacity(0.09))
                                )
                                .overlay(
                                    Capsule()
                                        .stroke(isSelected ? LuminaTheme.cyan : Color.white.opacity(0.14), lineWidth: 1)
                                )
                                .shadow(color: isSelected ? LuminaTheme.cyan.opacity(0.45) : .clear, radius: 8)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 4)
                }

                // Live Clock
                Text(currentTimeString)
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                    .foregroundColor(.white.opacity(0.75))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                LuminaTheme.blackBg.opacity(0.92)
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
