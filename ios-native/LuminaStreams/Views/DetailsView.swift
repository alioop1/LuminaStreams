import SwiftUI

struct DetailsView: View {
    let fullId: String
    var onNavigateMedia: ((String) -> Void)? = nil

    @StateObject private var vm = DetailsViewModel()
    @EnvironmentObject var settings: SettingsManager
    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    private var metrics: LayoutMetrics {
        LayoutMetrics(horizontal: horizontalSizeClass, vertical: verticalSizeClass)
    }

    var body: some View {
        ZStack {
            LuminaTheme.blackBg.ignoresSafeArea()

            if vm.isLoadingData {
                VStack(spacing: 14) {
                    ProgressView()
                        .tint(LuminaTheme.cyan)
                        .scaleEffect(1.4)
                    Text(settings.tr("טוען פרטי תוכן...", "Loading details..."))
                        .foregroundColor(.white.opacity(0.7))
                        .font(.system(size: 14))
                }
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 22) {
                        headerBackdropSection

                        VStack(alignment: .leading, spacing: 20) {
                            metadataAndBadgesSection
                            actionButtonsRow

                            if let nextEp = vm.mediaInfo.nextEpisode {
                                nextEpisodeBanner(nextEp)
                            }

                            Text(vm.mediaInfo.overview)
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.88))
                                .lineSpacing(4)

                            if !vm.mediaInfo.genres.isEmpty {
                                HScroll {
                                    HStack(spacing: 8) {
                                        ForEach(vm.mediaInfo.genres, id: \.self) { g in
                                            Text(g)
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundColor(LuminaTheme.cyan)
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 5)
                                                .background(LuminaTheme.cyan.opacity(0.12))
                                                .clipShape(Capsule())
                                        }
                                    }
                                }
                            }

                            // ── Seasons & Episodes (For TV Series) ──────────
                            if vm.mediaInfo.isSeries && vm.mediaInfo.totalSeasons > 0 {
                                seasonsAndEpisodesSection
                            }

                            // ── Cast Carousel ───────────────────────────────
                            if !vm.mediaInfo.cast.isEmpty {
                                castSection
                            }

                            // ── Recommendations ─────────────────────────────
                            if !vm.recommendations.isEmpty {
                                recommendationsSection
                            }
                        }
                        .padding(.horizontal, metrics.horizontalPadding)
                        .padding(.bottom, 40)
                    }
                }
                .ignoresSafeArea(edges: .top)
            }
        }
        .navigationBarBackButtonHidden(false)
        // Keyed on the id so tapping a recommendation or actor credit reloads.
        .task(id: fullId) {
            await vm.load(fullId: fullId)
        }
        .sheet(isPresented: $vm.showSourcesSheet) {
            SourcesOverlaySheet(vm: vm)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $vm.showActorSheet) {
            ActorDetailsSheet(vm: vm, onSelectMovie: { movie in
                vm.showActorSheet = false
                onNavigateMedia?(movie.displayId)
            })
            .presentationDetents([.medium, .large])
        }
        .fullScreenCover(item: $vm.activePlayback) { playback in
            PlayerView(request: playback)
        }
    }

    // ── Backdrop Header ─────────────────────────────────────────────────────
    private var headerBackdropSection: some View {
        ZStack(alignment: .bottomLeading) {
            // Pinned to the screen width; a bare fill image widened the whole page.
            FullWidthImage(url: vm.mediaInfo.backdropUrl.isEmpty ? vm.mediaInfo.posterUrl : vm.mediaInfo.backdropUrl,
                           height: metrics.backdropHeight)

            LuminaTheme.heroBottomGradient
                .frame(height: metrics.backdropHeight)

            VStack(alignment: .leading, spacing: 8) {
                if !vm.mediaInfo.logoUrl.isEmpty {
                    LuminaAsyncImage(url: vm.mediaInfo.logoUrl) { logo in
                        logo.resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(maxWidth: 220, maxHeight: 75, alignment: .leading)
                    } placeholder: {
                        Text(vm.mediaInfo.title)
                            .font(.system(size: 28, weight: .black, design: .rounded))
                            .foregroundColor(.white)
                    }
                } else {
                    Text(vm.mediaInfo.title)
                        .font(.system(size: 28, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 10)
        }
    }

    // ── Ratings & Metadata Row (IMDb, Rotten Tomatoes, Metacritic, Year) ────
    private var metadataAndBadgesSection: some View {
        HScroll {
            HStack(spacing: 10) {
                // IMDb Badge
                if vm.mediaInfo.imdbRating > 0 {
                    HStack(spacing: 4) {
                        Text("IMDb")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.black)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(LuminaTheme.gold)
                            .cornerRadius(4)
                        Text(String(format: "%.1f", vm.mediaInfo.imdbRating))
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                    }
                }

                // Rotten Tomatoes
                if let rt = vm.mediaInfo.rottenTomatoesScore {
                    HStack(spacing: 4) {
                        Text("🍅 \(rt)%")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(LuminaTheme.cardSurface)
                    .cornerRadius(6)
                }

                // Metacritic
                if let mc = vm.mediaInfo.metacriticScore {
                    Text("MC \(mc)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(LuminaTheme.green)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(LuminaTheme.cardSurface)
                        .cornerRadius(6)
                }

                if !vm.mediaInfo.releaseDate.isEmpty {
                    Text(vm.mediaInfo.releaseDate)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white.opacity(0.8))
                }

                if vm.mediaInfo.runtimeMinutes > 0 {
                    Text("\(vm.mediaInfo.runtimeMinutes) דק׳")
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.75))
                }

                Text("4K • AD+")
                    .font(.system(size: 11, weight: .heavy))
                    .foregroundColor(.black)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(LuminaTheme.cyan)
                    .cornerRadius(5)
            }
        }
    }

    // ── Action Buttons (Play / Sources / Watchlist / Trailer) ───────────────
    private var actionButtonsRow: some View {
        HStack(spacing: 12) {
            Button {
                if vm.mediaInfo.isSeries {
                    vm.startScraping(season: vm.selectedSeason, episode: 1, autoPlayTop: false)
                } else {
                    vm.startScraping(season: nil, episode: nil, autoPlayTop: false)
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "play.fill")
                    Text(vm.mediaInfo.isSeries
                         ? settings.tr("בחר מקור (עונה \(vm.selectedSeason) פרק 1)", "Sources (S\(vm.selectedSeason)E1)")
                         : settings.tr("בחר מקור וצפה", "Select Source & Play"))
                        .fontWeight(.bold)
                }
                .font(.system(size: 15))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(LuminaTheme.cyan)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .shadow(color: LuminaTheme.cyan.opacity(0.4), radius: 8)
            }

            Button {
                vm.toggleFavorite()
            } label: {
                Image(systemName: vm.mediaInfo.isFavorite ? "heart.fill" : "heart")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(vm.mediaInfo.isFavorite ? LuminaTheme.red : .white)
                    .frame(width: 48, height: 48)
                    .background(LuminaTheme.cardSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            if let trailerKey = vm.mediaInfo.trailerKey,
               let ytUrl = URL(string: "https://www.youtube.com/watch?v=\(trailerKey)") {
                Link(destination: ytUrl) {
                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 48, height: 48)
                        .background(LuminaTheme.cardSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }

    private func nextEpisodeBanner(_ nextEp: NextEpisodeInfo) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 20))
                .foregroundColor(LuminaTheme.cyan)
            VStack(alignment: .leading, spacing: 2) {
                Text(settings.tr("הפרק הבא: עונה \(nextEp.seasonNumber) פרק \(nextEp.episodeNumber)", "Next Episode: S\(nextEp.seasonNumber)E\(nextEp.episodeNumber)"))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                Text("\(nextEp.name) • \(nextEp.airDateIso)")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.75))
            }
            Spacer()
        }
        .padding(12)
        .background(LuminaTheme.cardSurface)
        .cornerRadius(10)
    }

    // ── Seasons & Episodes ──────────────────────────────────────────────────
    private var seasonsAndEpisodesSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(settings.tr("עונות ופרקים", "Seasons & Episodes"))
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            HScroll {
                HStack(spacing: 8) {
                    ForEach(1...max(1, vm.mediaInfo.totalSeasons), id: \.self) { sNum in
                        let isSelected = vm.selectedSeason == sNum
                        Button {
                            Task { await vm.selectSeason(sNum) }
                        } label: {
                            Text(settings.tr("עונה \(sNum)", "Season \(sNum)"))
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(isSelected ? .black : .white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule()
                                        .fill(isSelected ? LuminaTheme.cyan : LuminaTheme.cardSurface)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if vm.isEpisodesLoading {
                ProgressView()
                    .tint(LuminaTheme.cyan)
                    .padding(.vertical, 20)
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(vm.episodes) { ep in
                        Button {
                            vm.startScraping(season: ep.seasonNumber, episode: ep.episodeNumber)
                        } label: {
                            HStack(spacing: 12) {
                                ZStack(alignment: .center) {
                                    LuminaAsyncImage(url: ep.stillUrl.isEmpty ? vm.mediaInfo.backdropUrl : ep.stillUrl) { img in
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } placeholder: {
                                        Rectangle().fill(LuminaTheme.navy)
                                    }
                                    .frame(width: 120, height: 70)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                    Image(systemName: "play.circle.fill")
                                        .font(.system(size: 26))
                                        .foregroundColor(.white.opacity(0.9))
                                        .shadow(radius: 4)
                                }

                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text("\(ep.episodeNumber). \(ep.title)")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                        Spacer()
                                        if ep.hasWatched {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundColor(LuminaTheme.green)
                                                .font(.system(size: 14))
                                        }
                                    }
                                    if !ep.overview.isEmpty {
                                        Text(ep.overview)
                                            .font(.system(size: 12))
                                            .foregroundColor(.white.opacity(0.68))
                                            .lineLimit(2)
                                    }
                                    if !ep.airDateIso.isEmpty {
                                        Text(ep.airDateIso)
                                            .font(.system(size: 10))
                                            .foregroundColor(LuminaTheme.cyan.opacity(0.8))
                                    }
                                }
                            }
                            .padding(10)
                            .background(LuminaTheme.cardSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // ── Cast Section ────────────────────────────────────────────────────────
    private var castSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(settings.tr("שחקנים וצוות", "Cast & Crew"))
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            HScroll {
                HStack(spacing: 14) {
                    ForEach(vm.mediaInfo.cast) { actor in
                        Button {
                            vm.loadActor(actorId: actor.id)
                        } label: {
                            VStack(spacing: 6) {
                                LuminaAsyncImage(url: actor.imageUrl) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Circle().fill(LuminaTheme.cardSurface)
                                }
                                .frame(width: 72, height: 72)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(Color.white.opacity(0.18), lineWidth: 1))

                                Text(actor.name)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                Text(actor.character)
                                    .font(.system(size: 10))
                                    .foregroundColor(.white.opacity(0.6))
                                    .lineLimit(1)
                            }
                            .frame(width: 82)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // ── Recommendations Section ─────────────────────────────────────────────
    private var recommendationsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(settings.tr("תכנים דומים", "More Like This"))
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            HScroll {
                HStack(spacing: 12) {
                    ForEach(vm.recommendations) { rec in
                        Button {
                            onNavigateMedia?(rec.id)
                        } label: {
                            VStack(alignment: .leading, spacing: 5) {
                                LuminaAsyncImage(url: rec.posterUrl) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Rectangle().fill(LuminaTheme.cardSurface)
                                }
                                .frame(width: 115, height: 170)
                                .clipShape(RoundedRectangle(cornerRadius: 10))

                                Text(rec.title)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                    .frame(width: 115, alignment: .leading)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

// ── Torrentio + AllDebrid Sources Sheet ─────────────────────────────────────
struct SourcesOverlaySheet: View {
    @ObservedObject var vm: DetailsViewModel
    @EnvironmentObject var settings: SettingsManager

    var body: some View {
        ZStack {
            LuminaTheme.blackBg.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(settings.tr("מקורות צפייה (Torrentio • AllDebrid)", "Available Streams"))
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.white)
                        Text("\(vm.filteredStreams.count) מקורות זמינים")
                            .font(.system(size: 12))
                            .foregroundColor(LuminaTheme.cyan)
                    }
                    Spacer()
                    if vm.showCachedOnly || vm.showDvOnly || vm.qualityFilter != nil || vm.selectedLanguageFilter != nil {
                        Button(settings.tr("נקה סינון", "Reset")) {
                            vm.clearFilters()
                        }
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(LuminaTheme.red)
                    }
                    Button {
                        vm.showSourcesSheet = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 26))
                            .foregroundColor(.white.opacity(0.75))
                    }
                    .accessibilityLabel(settings.tr("סגור", "Close"))
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                // ── Filter Pills Bar ────────────────────────────────────────
                HScroll {
                    HStack(spacing: 8) {
                        Label(settings.tr("סינון", "Filter"), systemImage: "line.3.horizontal.decrease.circle")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white.opacity(0.6))
                        filterPill(title: "⚡ AD+ בלבד", isActive: vm.showCachedOnly) {
                            vm.showCachedOnly.toggle()
                            vm.applyFiltersAndSort()
                        }
                        filterPill(title: "4K UHD", isActive: vm.qualityFilter == .uhd4K) {
                            vm.qualityFilter = (vm.qualityFilter == .uhd4K) ? nil : .uhd4K
                            vm.applyFiltersAndSort()
                        }
                        filterPill(title: "1080p", isActive: vm.qualityFilter == .fhd1080p) {
                            vm.qualityFilter = (vm.qualityFilter == .fhd1080p) ? nil : .fhd1080p
                            vm.applyFiltersAndSort()
                        }
                        filterPill(title: "Dolby Vision", isActive: vm.showDvOnly) {
                            vm.showDvOnly.toggle()
                            vm.applyFiltersAndSort()
                        }
                        ForEach([AudioLanguage.hebrew, .english, .russian], id: \.self) { lang in
                            filterPill(title: "\(lang.flag) \(lang.rawValue)", isActive: vm.selectedLanguageFilter == lang) {
                                vm.selectedLanguageFilter = (vm.selectedLanguageFilter == lang) ? nil : lang
                                vm.applyFiltersAndSort()
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }

                if vm.isScraping || vm.isResolvingStream {
                    Spacer()
                    HStack {
                        Spacer()
                        VStack(spacing: 12) {
                            ProgressView()
                                .tint(LuminaTheme.cyan)
                                .scaleEffect(1.3)
                            Text(vm.isResolvingStream ? vm.resolvingMessage : settings.tr("סורק מקורות מ-Torrentio...", "Scraping Torrentio sources..."))
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)
                        }
                        Spacer()
                    }
                    Spacer()
                } else if let err = vm.scrapeError {
                    Spacer()
                    HStack {
                        Spacer()
                        VStack(spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 32))
                                .foregroundColor(LuminaTheme.red)
                            Text(err)
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                        }
                        .padding()
                        Spacer()
                    }
                    Spacer()
                } else {
                    ScrollView(.vertical, showsIndicators: false) {
                        LazyVStack(spacing: 10) {
                            ForEach(vm.filteredStreams) { stream in
                                Button {
                                    Task { await vm.selectAndPlayStream(stream) }
                                } label: {
                                    streamRowCard(stream)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                    }
                }
            }
        }
    }

    private func filterPill(title: String, isActive: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(isActive ? .black : .white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Capsule().fill(isActive ? LuminaTheme.cyan : LuminaTheme.cardSurface))
        }
        .buttonStyle(.plain)
    }

    private func streamRowCard(_ s: AdvancedStreamSource) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                if s.isCachedAd {
                    Text("⚡ AD+")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.black)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(LuminaTheme.cyan)
                        .cornerRadius(4)
                }

                Text(s.quality.rawValue)
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(s.quality == .uhd4K ? LuminaTheme.purple : Color.white.opacity(0.18))
                    .cornerRadius(4)

                if !s.videoCodec.rawValue.isEmpty {
                    Text(s.videoCodec.rawValue)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white.opacity(0.85))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.white.opacity(0.12))
                        .cornerRadius(4)
                }

                if s.isDolbyVision {
                    Text("DV")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(LuminaTheme.gold)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(LuminaTheme.gold.opacity(0.18))
                        .cornerRadius(4)
                }

                Text(s.trackerName)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(TrackerParser.trackerColor(s.trackerName).opacity(0.8))
                    .cornerRadius(4)

                Spacer()

                if !s.formattedSize.isEmpty {
                    Text("💾 \(s.formattedSize)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(LuminaTheme.cyan)
                }

                if s.seeders > 0 {
                    Text("👤 \(s.seeders)")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(LuminaTheme.green)
                }
            }

            Text(s.filename)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.white.opacity(0.9))
                .lineLimit(2)

            HStack(spacing: 6) {
                ForEach(s.audioLanguages, id: \.self) { lang in
                    Text("\(lang.flag) \(lang.rawValue)")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(.white.opacity(0.75))
                }
            }
        }
        .padding(12)
        .background(LuminaTheme.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(s.isCachedAd ? LuminaTheme.cyan.opacity(0.35) : Color.white.opacity(0.08), lineWidth: 1)
        )
    }
}

// ── Actor Details Sheet ─────────────────────────────────────────────────────
struct ActorDetailsSheet: View {
    @ObservedObject var vm: DetailsViewModel
    let onSelectMovie: (Movie) -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            LuminaTheme.blackBg.ignoresSafeArea()
            Button {
                vm.showActorSheet = false
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 26))
                    .foregroundColor(.white.opacity(0.75))
            }
            .padding(14)
            .zIndex(1)
            if vm.isLoadingActor {
                ProgressView().tint(LuminaTheme.cyan)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let actor = vm.selectedActor {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(spacing: 14) {
                            AsyncImage(url: URL(string: actor.profileUrl)) { phase in
                                if case .success(let img) = phase {
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    Circle().fill(LuminaTheme.cardSurface)
                                }
                            }
                            .frame(width: 84, height: 84)
                            .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 4) {
                                Text(actor.name)
                                    .font(.system(size: 20, weight: .black))
                                    .foregroundColor(.white)
                                if !actor.birthday.isEmpty {
                                    Text(actor.birthday)
                                        .font(.system(size: 12))
                                        .foregroundColor(LuminaTheme.cyan)
                                }
                                if !actor.placeOfBirth.isEmpty {
                                    Text(actor.placeOfBirth)
                                        .font(.system(size: 12))
                                        .foregroundColor(.white.opacity(0.7))
                                }
                            }
                        }

                        if !actor.biography.isEmpty {
                            Text(actor.biography)
                                .font(.system(size: 13))
                                .foregroundColor(.white.opacity(0.85))
                                .lineLimit(8)
                        }

                        Text("פילמוגרפיה בולטת")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)

                        HScroll {
                            HStack(spacing: 12) {
                                ForEach(actor.knownFor) { movie in
                                    Button {
                                        onSelectMovie(movie)
                                    } label: {
                                        MediaCardView(movie: movie)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }
        }
    }
}
