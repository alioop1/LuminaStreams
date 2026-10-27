import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: HomeViewModel
    let tabMode: String // "home", "movies", "series"
    let onSelectMedia: (Movie) -> Void
    @EnvironmentObject var settings: SettingsManager

    private let heroTimer = Timer.publish(every: 8, on: .main, in: .common).autoconnect()

    var activeRows: [HomeRowDef] {
        switch tabMode {
        case "movies": return viewModel.movieRows
        case "series": return viewModel.seriesRows
        default:       return viewModel.homeRows
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let isLandscape = proxy.size.width > proxy.size.height

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    
                    // ── 1. Hero Spotlight (1:1 Android TV BackdropLayer & HeroOverlay) ───
                    if !viewModel.heroItems.isEmpty && viewModel.selectedStudio == nil {
                        heroSection(isLandscape: isLandscape)
                    }

                    // ── 2. Studio Filter Bar (Android TV StudioRibbonRow) ────────────────
                    if tabMode == "movies" || tabMode == "series" || tabMode == "home" {
                        studioRibbonBar
                    }

                    // ── 3. Content Rows ──────────────────────────────────────────────────
                    if viewModel.isLoading {
                        HStack {
                            Spacer()
                            ProgressView()
                                .tint(LuminaTheme.red)
                                .scaleEffect(1.2)
                                .padding(.vertical, 50)
                            Spacer()
                        }
                    } else if let selectedStudio = viewModel.selectedStudio {
                        if viewModel.isStudioLoading {
                            HStack {
                                Spacer()
                                ProgressView("טוען קטלוג \(selectedStudio)...")
                                    .tint(LuminaTheme.cyan)
                                    .foregroundColor(.white)
                                    .padding(.vertical, 40)
                                Spacer()
                            }
                        } else {
                            ForEach(viewModel.studioCategories) { cat in
                                mediaRowView(
                                    title: settings.isRTL ? cat.titleHe : cat.titleEn,
                                    items: cat.movies,
                                    isLandscape: false
                                )
                            }
                        }
                    } else {
                        ForEach(activeRows) { row in
                            mediaRowView(
                                title: row.title,
                                items: row.items,
                                isLandscape: row.isLandscape
                            )
                        }
                    }

                    Spacer(minLength: 40)
                }
            }
        }
        .refreshable {
            await viewModel.loadAllContent()
        }
        .onReceive(heroTimer) { _ in
            guard !viewModel.heroItems.isEmpty else { return }
            withAnimation(.easeInOut(duration: 0.6)) {
                viewModel.heroIndex = (viewModel.heroIndex + 1) % viewModel.heroItems.count
            }
        }
    }

    // ── 1:1 Hero Banner with Directional RTL Gradients, Accent Line, and Cast Avatars ───
    @ViewBuilder
    private func heroSection(isLandscape: Bool) -> some View {
        let hero = viewModel.heroItems[min(viewModel.heroIndex, max(0, viewModel.heroItems.count - 1))]

        ZStack(alignment: .bottomLeading) {
            // Backdrop Image
            AsyncImage(url: URL(string: hero.backdropUrl.isEmpty ? hero.posterUrl : hero.backdropUrl)) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                default:
                    Rectangle().fill(LuminaTheme.cardSurface)
                }
            }
            .frame(height: isLandscape ? 240 : 340)
            .clipped()

            // Directional TV Gradients: Bottom fade + Horizontal RTL vignette
            LinearGradient(
                colors: [
                    Color(red: 0x06/255, green: 0x06/255, blue: 0x08/255),
                    Color(red: 0x06/255, green: 0x06/255, blue: 0x08/255).opacity(0.7),
                    Color(red: 0x06/255, green: 0x06/255, blue: 0x08/255).opacity(0.1),
                    Color(red: 0x06/255, green: 0x06/255, blue: 0x08/255).opacity(0.4)
                ],
                startPoint: .bottom,
                endPoint: .top
            )

            LinearGradient(
                colors: [
                    Color(red: 0x06/255, green: 0x06/255, blue: 0x08/255).opacity(0.85),
                    Color(red: 0x06/255, green: 0x06/255, blue: 0x08/255).opacity(0.3),
                    .clear
                ],
                startPoint: .trailing,
                endPoint: .leading
            )

            // Content Overlay
            VStack(alignment: .leading, spacing: 6) {
                // Title
                Text(hero.title)
                    .font(.system(size: isLandscape ? 20 : 23, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                    .shadow(color: .black.opacity(0.9), radius: 8, x: 0, y: 2)
                    .lineLimit(1)

                // Meta Line: [▶ טריילר] [★ דירוג] | שנת יציאה | ז'אנר
                HStack(spacing: 8) {
                    // Trailer Pill
                    HStack(spacing: 3) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 8))
                        Text("טריילר")
                            .font(.system(size: 9, weight: .black))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2.5)
                    .background(LuminaTheme.red)
                    .clipShape(RoundedRectangle(cornerRadius: 4))

                    // Rating
                    if hero.rating > 0 {
                        HStack(spacing: 2) {
                            Text("★")
                                .foregroundColor(LuminaTheme.gold)
                            Text(String(format: "%.1f", hero.rating))
                                .foregroundColor(.white)
                        }
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.7))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    }

                    Text("|").foregroundColor(.white.opacity(0.25))

                    if let year = hero.year, !year.isEmpty {
                        Text(year).font(.system(size: 11, weight: .semibold)).foregroundColor(.white.opacity(0.75))
                        Text("|").foregroundColor(.white.opacity(0.25))
                    }

                    Text(hero.mediaType == "tv" ? "סדרה" : "סרט")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.white.opacity(0.75))
                }

                // Synopsis with 3-Color Gradient Vertical Line
                if !hero.overview.isEmpty {
                    HStack(alignment: .top, spacing: 8) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(
                                LinearGradient(
                                    colors: [LuminaTheme.cyan, LuminaTheme.purple, LuminaTheme.red],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                            )
                            .frame(width: 3, height: 32)

                        Text(hero.overview)
                            .font(.system(size: 11, weight: .regular))
                            .foregroundColor(.white.opacity(0.85))
                            .lineLimit(2)
                            .lineSpacing(2)
                    }
                    .padding(.top, 2)
                }

                // Action Row
                HStack(spacing: 12) {
                    Button {
                        onSelectMedia(hero)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 12))
                            Text("נגן עכשיו")
                                .font(.system(size: 13, weight: .heavy))
                        }
                        .foregroundColor(.black)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(Color.white)
                        .clipShape(Capsule())
                        .shadow(color: Color.white.opacity(0.35), radius: 8)
                    }

                    Spacer()

                    // Carousel Dots
                    HStack(spacing: 5) {
                        ForEach(0..<min(viewModel.heroItems.count, 6), id: \.self) { idx in
                            Circle()
                                .fill(idx == viewModel.heroIndex ? Color.white : Color.white.opacity(0.3))
                                .frame(width: idx == viewModel.heroIndex ? 7 : 5, height: idx == viewModel.heroIndex ? 7 : 5)
                        }
                    }
                }
                .padding(.top, 4)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
    }

    // ── Studio Ribbon Bar (TMDB High-Res Logos with Brand Gradients) ───────
    private var studioRibbonBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("אולפנים ורשתות סטרימינג")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white.opacity(0.9))
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    studioButton(id: "hbo", name: "HBO", logoUrl: "https://image.tmdb.org/t/p/original/tuomPhY2UtuPTqqFnKMVHvSb724.png", colors: [Color(hex: 0x1A0A2E), Color(hex: 0x2D1B4E)])
                    studioButton(id: "netflix", name: "Netflix", logoUrl: "https://image.tmdb.org/t/p/original/wwemzKWzjKYJFfCeiB57q3r4Bcm.png", colors: [Color(hex: 0x141414), Color(hex: 0x1C1C1C)])
                    studioButton(id: "disney", name: "Disney+", logoUrl: "https://image.tmdb.org/t/p/original/1edZOYAfoyZyZ3rklNSiUpXX30Q.png", colors: [Color(hex: 0x0A1628), Color(hex: 0x142240)])
                    studioButton(id: "apple", name: "Apple TV+", logoUrl: "https://image.tmdb.org/t/p/original/bngHRFi794mnMq34gfVcm9nDxN1.png", colors: [Color(hex: 0x1A1A2E), Color(hex: 0x2A2A40)])
                    studioButton(id: "amazon", name: "Amazon", logoUrl: "https://image.tmdb.org/t/p/original/w7HfLNm9CWwRmAMU58udl2L7We7.png", colors: [Color(hex: 0x0A1E2C), Color(hex: 0x0F2B3D)])
                    studioButton(id: "paramount", name: "Paramount+", logoUrl: "https://image.tmdb.org/t/p/original/fi83B1oztoS47xxcemFdPMhIzK.png", colors: [Color(hex: 0x0A0F1E), Color(hex: 0x141E35)])
                    studioButton(id: "hulu", name: "Hulu", logoUrl: "https://image.tmdb.org/t/p/original/pqUTCleNUiTLAVlelGxUgWn1ELh.png", colors: [Color(hex: 0x0A1A14), Color(hex: 0x0F2A1E)])
                    
                    // SONY
                    let isSelected = viewModel.selectedStudio == "sony"
                    Button {
                        viewModel.selectStudio("sony", isMovies: tabMode == "movies")
                    } label: {
                        Text("SONY")
                            .font(.system(size: 16, weight: .black, design: .serif))
                            .foregroundColor(.white)
                            .frame(width: 110, height: 50)
                            .background(
                                LinearGradient(colors: [Color(hex: 0x050A14), Color(hex: 0x152238)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(isSelected ? Color.white : Color.white.opacity(0.12), lineWidth: isSelected ? 2 : 1)
                            )
                            .shadow(color: isSelected ? Color.white.opacity(0.4) : .clear, radius: 8)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func studioButton(id: String, name: String, logoUrl: String, colors: [Color]) -> some View {
        let isSelected = viewModel.selectedStudio == id
        return Button {
            viewModel.selectStudio(id, isMovies: tabMode == "movies")
        } label: {
            ZStack {
                LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)

                AsyncImage(url: URL(string: logoUrl)) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().aspectRatio(contentMode: .fit).padding(8)
                    default:
                        Text(name).font(.system(size: 11, weight: .bold)).foregroundColor(.white)
                    }
                }
            }
            .frame(width: 110, height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.white : Color.white.opacity(0.12), lineWidth: isSelected ? 2 : 1)
            )
            .shadow(color: isSelected ? Color.white.opacity(0.4) : .clear, radius: 8)
        }
        .buttonStyle(.plain)
    }

    // ── Media Row (Matches HomeRows.kt with Category Titles & Cards) ─────
    private func mediaRowView(title: String, items: [Movie], isLandscape: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 10) {
                    ForEach(items) { movie in
                        Button {
                            onSelectMedia(movie)
                        } label: {
                            MediaCardView(movie: movie, isLandscape: isLandscape)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

// ── 1:1 MediaCardView with Full Image Visibility, Seeders & Ratings ───────
struct MediaCardView: View {
    let movie: Movie
    var isLandscape: Bool = false

    var cardWidth: CGFloat { isLandscape ? 190 : 120 }
    var cardHeight: CGFloat { isLandscape ? 108 : 175 }

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            ZStack(alignment: .topTrailing) {
                AsyncImage(url: URL(string: isLandscape ? (movie.backdropUrl.isEmpty ? movie.posterUrl : movie.backdropUrl) : movie.posterUrl)) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().aspectRatio(contentMode: .fill)
                    default:
                        Rectangle()
                            .fill(LuminaTheme.cardSurface)
                            .overlay(
                                Image(systemName: "film")
                                    .foregroundColor(.white.opacity(0.25))
                            )
                    }
                }
                .frame(width: cardWidth, height: cardHeight)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )

                // Fuzer Badge (Top Left / Right based on RTL)
                if movie.id.hasPrefix("http") {
                    HStack {
                        Text("💎 FUZER")
                            .font(.system(size: 8, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(Color(red: 0x00/255, green: 0xB0/255, blue: 0xFF/255))
                            .clipShape(RoundedRectangle(cornerRadius: 3))
                        Spacer()
                    }
                    .padding(5)
                }

                // Rating Badge
                if movie.rating > 0 {
                    HStack(spacing: 2) {
                        Text("★")
                            .foregroundColor(LuminaTheme.gold)
                        Text(String(format: "%.1f", movie.rating))
                            .foregroundColor(.white)
                    }
                    .font(.system(size: 9, weight: .bold))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(Color.black.opacity(0.75))
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .padding(5)
                }

                // Watch Progress Bar
                if let prog = movie.progress, prog > 0 {
                    VStack {
                        Spacer()
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(Color.black.opacity(0.4)).frame(height: 3)
                                Rectangle().fill(LuminaTheme.red).frame(width: geo.size.width * CGFloat(prog), height: 3)
                            }
                        }
                        .frame(height: 3)
                    }
                    .frame(width: cardWidth, height: cardHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }

            Text(movie.title)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(.white)
                .lineLimit(1)
                .frame(width: cardWidth, alignment: .leading)

            if movie.id.hasPrefix("http") && (movie.seeders > 0 || !movie.size.isEmpty) {
                HStack(spacing: 4) {
                    Text("▲ \(movie.seeders)")
                        .font(.system(size: 9, weight: .black))
                        .foregroundColor(Color(red: 0x32/255, green: 0xD7/255, blue: 0x4B/255))
                    Text("▼ \(movie.leechers)")
                        .font(.system(size: 9, weight: .black))
                        .foregroundColor(LuminaTheme.red)
                    if !movie.size.isEmpty {
                        Text(movie.size)
                            .font(.system(size: 9, weight: .regular))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
            } else {
                Text(movie.mediaType == "tv" ? "סדרה" : "סרט")
                    .font(.system(size: 9.5, weight: .regular))
                    .foregroundColor(.white.opacity(0.45))
            }
        }
    }
}

// Color Hex Extension
extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
