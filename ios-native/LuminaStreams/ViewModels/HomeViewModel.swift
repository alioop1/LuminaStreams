import Foundation
import SwiftUI

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var selectedTab: String = "home"
    @Published var heroItems: [Movie] = []
    @Published var heroIndex: Int = 0
    @Published var homeRows: [HomeRowDef] = []
    @Published var movieRows: [HomeRowDef] = []
    @Published var seriesRows: [HomeRowDef] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil

    // Studio filter state (HBO, NETFLIX, APPLE_TV, DISNEY, AMAZON, PARAMOUNT, HULU, SONY)
    @Published var selectedStudio: String? = nil
    @Published var studioCategories: [StudioCategory] = []
    @Published var isStudioLoading: Bool = false

    let studios: [(id: String, name: String)] = [
        ("NETFLIX", "Netflix"),
        ("HBO", "HBO Max"),
        ("APPLE_TV", "Apple TV+"),
        ("DISNEY", "Disney+"),
        ("AMAZON", "Prime Video"),
        ("PARAMOUNT", "Paramount+"),
        ("HULU", "Hulu"),
        ("SONY", "Sony Pictures")
    ]

    init() {
        Task {
            await loadAllContent()
        }
    }

    func loadAllContent() async {
        isLoading = true
        errorMessage = nil
        let isHe = SettingsManager.shared.isRTL

        async let trendingAll = try? TmdbService.shared.fetchList(endpoint: "trending/all/week")
        async let trendingMovies = try? TmdbService.shared.fetchList(endpoint: "trending/movie/week", defaultMediaType: "movie")
        async let trendingSeries = try? TmdbService.shared.fetchList(endpoint: "trending/tv/week", defaultMediaType: "tv")
        async let topRatedMovies = try? TmdbService.shared.fetchList(endpoint: "movie/top_rated", defaultMediaType: "movie")
        async let popularMovies = try? TmdbService.shared.fetchList(endpoint: "movie/popular", defaultMediaType: "movie")
        async let actionMovies = try? TmdbService.shared.fetchList(endpoint: "discover/movie", defaultMediaType: "movie", extraQuery: "&with_genres=28&sort_by=popularity.desc")
        async let scifiMovies = try? TmdbService.shared.fetchList(endpoint: "discover/movie", defaultMediaType: "movie", extraQuery: "&with_genres=878&sort_by=popularity.desc")
        async let comedyMovies = try? TmdbService.shared.fetchList(endpoint: "discover/movie", defaultMediaType: "movie", extraQuery: "&with_genres=35&sort_by=popularity.desc")
        async let horrorMovies = try? TmdbService.shared.fetchList(endpoint: "discover/movie", defaultMediaType: "movie", extraQuery: "&with_genres=27&sort_by=popularity.desc")
        async let popularSeries = try? TmdbService.shared.fetchList(endpoint: "tv/popular", defaultMediaType: "tv")
        async let topRatedSeries = try? TmdbService.shared.fetchList(endpoint: "tv/top_rated", defaultMediaType: "tv")
        async let israeliContent = try? TmdbService.shared.fetchList(endpoint: "discover/tv", defaultMediaType: "tv", extraQuery: "&with_origin_country=IL&sort_by=popularity.desc")

        let tAll = await trendingAll ?? []
        let tMov = await trendingMovies ?? []
        let tSer = await trendingSeries ?? []
        let topMov = await topRatedMovies ?? []
        let popMov = await popularMovies ?? []
        let actMov = await actionMovies ?? []
        let sciMov = await scifiMovies ?? []
        let comMov = await comedyMovies ?? []
        let horMov = await horrorMovies ?? []
        let popSer = await popularSeries ?? []
        let topSer = await topRatedSeries ?? []
        let ilSer  = await israeliContent ?? []

        self.heroItems = Array((tAll.isEmpty ? tMov : tAll).prefix(8))

        var hRows: [HomeRowDef] = []
        let cw = WatchProgressManager.shared.continueWatchingMovies()
        if !cw.isEmpty {
            hRows.append(HomeRowDef(id: "cw", title: isHe ? "המשך צפייה" : "Continue Watching", items: cw, isLandscape: true))
        }
        if !tMov.isEmpty {
            hRows.append(HomeRowDef(id: "trend_m", title: isHe ? "סרטים חמים השבוע 🔥" : "Trending Movies 🔥", items: tMov))
        }
        if !tSer.isEmpty {
            hRows.append(HomeRowDef(id: "trend_tv", title: isHe ? "סדרות מובילות השבוע 📺" : "Trending Series 📺", items: tSer))
        }
        if !ilSer.isEmpty {
            hRows.append(HomeRowDef(id: "il_tv", title: isHe ? "יצירה ישראלית 🇮🇱" : "Israeli Originals 🇮🇱", items: ilSer))
        }
        if !topMov.isEmpty {
            hRows.append(HomeRowDef(id: "top_m", title: isHe ? "סרטים בדירוג גבוה ⭐" : "Top Rated Movies ⭐", items: topMov))
        }
        if !actMov.isEmpty {
            hRows.append(HomeRowDef(id: "act_m", title: isHe ? "שוברי קופות ופעולה 💥" : "Action Blockbusters 💥", items: actMov))
        }
        if !sciMov.isEmpty {
            hRows.append(HomeRowDef(id: "sci_m", title: isHe ? "מדע בדיוני ופנטזיה 🚀" : "Sci-Fi & Fantasy 🚀", items: sciMov))
        }
        self.homeRows = hRows

        // Movies Tab Rows
        var mRows: [HomeRowDef] = []
        if !tMov.isEmpty { mRows.append(HomeRowDef(id: "m_trend", title: isHe ? "סרטים פופולריים כעת" : "Trending Now", items: tMov)) }
        if !popMov.isEmpty { mRows.append(HomeRowDef(id: "m_pop", title: isHe ? "הכי נצפים בקולנוע" : "Most Popular", items: popMov)) }
        if !topMov.isEmpty { mRows.append(HomeRowDef(id: "m_top", title: isHe ? "המדורגים ביותר בכל הזמנים" : "All-Time Top Rated", items: topMov)) }
        if !actMov.isEmpty { mRows.append(HomeRowDef(id: "m_act", title: isHe ? "פעולה ואדרנלין" : "Action & Adrenaline", items: actMov)) }
        if !sciMov.isEmpty { mRows.append(HomeRowDef(id: "m_sci", title: isHe ? "מדע בדיוני" : "Sci-Fi", items: sciMov)) }
        if !comMov.isEmpty { mRows.append(HomeRowDef(id: "m_com", title: isHe ? "קומדיה" : "Comedy", items: comMov)) }
        if !horMov.isEmpty { mRows.append(HomeRowDef(id: "m_hor", title: isHe ? "אימה ומתח" : "Horror & Thriller", items: horMov)) }
        self.movieRows = mRows

        // Series Tab Rows
        var sRows: [HomeRowDef] = []
        if !tSer.isEmpty { sRows.append(HomeRowDef(id: "s_trend", title: isHe ? "סדרות חמות השבוע" : "Trending Series", items: tSer)) }
        if !popSer.isEmpty { sRows.append(HomeRowDef(id: "s_pop", title: isHe ? "סדרות פופולריות" : "Popular TV Shows", items: popSer)) }
        if !topSer.isEmpty { sRows.append(HomeRowDef(id: "s_top", title: isHe ? "סדרות מופת בדירוג גבוה" : "Top Rated Series", items: topSer)) }
        if !ilSer.isEmpty { sRows.append(HomeRowDef(id: "s_il", title: isHe ? "סדרות ישראליות 🇮🇱" : "Israeli Series 🇮🇱", items: ilSer)) }
        self.seriesRows = sRows

        self.isLoading = false
    }

    func selectStudio(_ studio: String?, isMovies: Bool) {
        if selectedStudio == studio {
            selectedStudio = nil
            studioCategories = []
            return
        }
        selectedStudio = studio
        guard let studio = studio else {
            studioCategories = []
            return
        }
        isStudioLoading = true
        Task {
            let cats = await TmdbService.shared.fetchStudioCategories(studio: studio, isMovies: isMovies)
            self.studioCategories = cats
            self.isStudioLoading = false
        }
    }
}
