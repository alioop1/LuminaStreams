import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: HomeViewModel
    let tabMode: String // "home", "movies", "series"
    let onSelectMedia: (Movie) -> Void
    @EnvironmentObject var settings: SettingsManager

    private let heroTimer = Timer.publish(every: 7, on: .main, in: .common).autoconnect()

    var activeRows: [HomeRowDef] {
        switch tabMode {
        case "movies": return viewModel.movieRows
        case "series": return viewModel.seriesRows
        default:       return viewModel.homeRows
        }
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 24) {
                // ── 1. Hero Spotlight Carousel ──────────────────────────────
                if !viewModel.heroItems.isEmpty && viewModel.selectedStudio == nil {
                    heroBannerSection
                }

                // ── 2. Studio Filter Bar (Movies & Series Tabs) ─────────────
                if tabMode == "movies" || tabMode == "series" {
                    studioFilterBar
                }

                // ── 3. Content Rows (or Studio Filtered Categories) ─────────
                if viewModel.isLoading {
                    HStack {
                        Spacer()
                        ProgressView()
                            .tint(LuminaTheme.cyan)
                            .scaleEffect(1.3)
                            .padding(.vertical, 60)
                        Spacer()
                    }
                } else if let selectedStudio = viewModel.selectedStudio, (tabMode == "movies" || tabMode == "series") {
                    if viewModel.isStudioLoading {
                        HStack {
                            Spacer()
                            ProgressView("טוען קטלוג \(selectedStudio)...")
                                .tint(LuminaTheme.cyan)
                                .foregroundColor(.white)
                                .padding(.vertical, 50)
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
        .refreshable {
            await viewModel.loadAllContent()
        }
        .onReceive(heroTimer) { _ in
            guard !viewModel.heroItems.isEmpty else { return }
            withAnimation(.easeInOut(duration: 0.5)) {
                viewModel.heroIndex = (viewModel.heroIndex + 1) % viewModel.heroItems.count
            }
        }
    }

    // ── Hero Banner ─────────────────────────────────────────────────────────
    private var heroBannerSection: some View {
        let hero = viewModel.heroItems[min(viewModel.heroIndex, max(0, viewModel.heroItems.count - 1))]
        return ZStack(alignment: .bottomLeading) {
            AsyncImage(url: URL(string: hero.backdropUrl.isEmpty ? hero.posterUrl : hero.backdropUrl)) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                default:
                    Rectangle().fill(LuminaTheme.navy)
                }
            }
            .frame(height: 360)
            .clipped()

            LuminaTheme.heroBottomGradient
                .frame(height: 360)

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Text(hero.resolutionBadge)
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundColor(.black)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(LuminaTheme.cyan)
                        .cornerRadius(5)

                    HStack(spacing: 3) {
                        Image(systemName: "star.fill")
                            .foregroundColor(LuminaTheme.gold)
                            .font(.system(size: 11))
                        Text(String(format: "%.1f", hero.rating))
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.black.opacity(0.6))
                    .cornerRadius(5)

                    if hero.year > 0 {
                        Text("\(hero.year)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white.opacity(0.85))
                    }

                    Text(hero.genre)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(LuminaTheme.cyan)
                }

                Text(hero.title)
                    .font(.system(size: 28, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(2)
                    .shadow(color: .black.opacity(0.8), radius: 6)

                if !hero.overview.isEmpty {
                    Text(hero.overview)
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.82))
                        .lineLimit(2)
                }

                HStack(spacing: 12) {
                    Button {
                        onSelectMedia(hero)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                            Text(settings.tr("צפה עכשיו", "Watch Now"))
                                .fontWeight(.bold)
                        }
                        .font(.system(size: 14))
                        .foregroundColor(.black)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 11)
                        .background(LuminaTheme.cyan)
                        .clipShape(Capsule())
                        .shadow(color: LuminaTheme.cyan.opacity(0.5), radius: 10)
                    }

                    // Carousel Dots
                    Spacer()
                    HStack(spacing: 5) {
                        ForEach(0..<viewModel.heroItems.count, id: \.self) { idx in
                            Circle()
                                .fill(idx == viewModel.heroIndex ? LuminaTheme.cyan : Color.white.opacity(0.3))
                                .frame(width: idx == viewModel.heroIndex ? 8 : 5, height: idx == viewModel.heroIndex ? 8 : 5)
                        }
                    }
                }
                .padding(.top, 4)
            }
            .padding(18)
        }
    }

    // ── Studio Filter Pills (HBO, Netflix, Apple TV+, Disney+, etc.) ────────
    private var studioFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(viewModel.studios, id: \.id) { studio in
                    let isSelected = viewModel.selectedStudio == studio.id
                    Button {
                        viewModel.selectStudio(studio.id, isMovies: tabMode == "movies")
                    } label: {
                        Text(studio.name)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(isSelected ? .black : .white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background(
                                Capsule()
                                    .fill(isSelected ? LuminaTheme.cyan : LuminaTheme.cardSurface)
                            )
                            .overlay(
                                Capsule()
                                    .stroke(isSelected ? LuminaTheme.cyan : Color.white.opacity(0.15), lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    // ── Horizontal Media Row ────────────────────────────────────────────────
    private func mediaRowView(title: String, items: [Movie], isLandscape: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(.white)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
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

struct MediaCardView: View {
    let movie: Movie
    var isLandscape: Bool = false

    var cardWidth: CGFloat { isLandscape ? 210 : 128 }
    var cardHeight: CGFloat { isLandscape ? 120 : 190 }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
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
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )

                // Rating Badge
                if movie.rating > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 9))
                            .foregroundColor(LuminaTheme.gold)
                        Text(String(format: "%.1f", movie.rating))
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Color.black.opacity(0.75))
                    .clipShape(Capsule())
                    .padding(6)
                }

                // Watch Progress Bar at bottom
                if let prog = movie.progress, prog > 0 {
                    VStack {
                        Spacer()
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(Color.white.opacity(0.25)).frame(height: 4)
                                Rectangle().fill(LuminaTheme.cyan).frame(width: geo.size.width * CGFloat(prog), height: 4)
                            }
                        }
                        .frame(height: 4)
                    }
                    .frame(width: cardWidth, height: cardHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }

            Text(movie.title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.white)
                .lineLimit(1)
                .frame(width: cardWidth, alignment: .leading)
        }
    }
}
