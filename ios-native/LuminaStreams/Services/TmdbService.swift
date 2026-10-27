import Foundation

final class TmdbService {
    static let shared = TmdbService()
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 12
        config.timeoutIntervalForResource = 20
        self.session = URLSession(configuration: config)
    }

    private var langParam: String {
        SettingsManager.shared.isRTL ? "he-IL" : "en-US"
    }

    // ── Fetch Generic Movie/TV List ─────────────────────────────────────────
    func fetchList(endpoint: String, defaultMediaType: String = "movie", extraQuery: String = "") async throws -> [Movie] {
        let sep = endpoint.contains("?") ? "&" : "?"
        let urlStr = "\(Constants.tmdbBaseUrl)/\(endpoint)\(sep)api_key=\(Constants.tmdbApiKey)&language=\(langParam)\(extraQuery)"
        guard let url = URL(string: urlStr) else { return [] }

        let (data, _) = try await session.data(from: url)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let results = json["results"] as? [[String: Any]] else {
            return []
        }

        return results.compactMap { parseMovieItem($0, fallbackType: defaultMediaType) }
    }

    func parseMovieItem(_ dict: [String: Any], fallbackType: String = "movie") -> Movie? {
        guard let idNum = dict["id"] as? Int else { return nil }
        let mediaType = (dict["media_type"] as? String) ?? (dict["first_air_date"] != nil ? "tv" : fallbackType)
        guard mediaType == "movie" || mediaType == "tv" else { return nil }

        let title = (dict["title"] as? String)
            ?? (dict["name"] as? String)
            ?? (dict["original_title"] as? String)
            ?? (dict["original_name"] as? String)
            ?? ""
        guard !title.isEmpty else { return nil }

        let posterPath = dict["poster_path"] as? String
        let backdropPath = dict["backdrop_path"] as? String
        guard posterPath != nil || backdropPath != nil else { return nil }

        let vote = (dict["vote_average"] as? Double) ?? 0.0
        let releaseDate = (dict["release_date"] as? String) ?? (dict["first_air_date"] as? String) ?? ""
        let year = Int(releaseDate.prefix(4)) ?? 0
        let genreIds = (dict["genre_ids"] as? [Int]) ?? []
        let overview = (dict["overview"] as? String) ?? ""

        return Movie(
            id: "\(mediaType)_\(idNum)",
            title: title,
            backdropUrl: Constants.backdropUrl(backdropPath ?? posterPath),
            posterUrl: Constants.posterUrl(posterPath ?? backdropPath),
            overview: overview,
            rating: Float(vote),
            year: year,
            genre: genreName(for: genreIds.first),
            mediaType: mediaType,
            genreIds: genreIds,
            is4K: vote >= 7.0,
            resolutionBadge: vote >= 7.0 ? "4K HDR" : "1080p"
        )
    }

    func genreName(for id: Int?) -> String {
        let isHe = SettingsManager.shared.isRTL
        switch id {
        case 28, 10759: return isHe ? "פעולה" : "Action"
        case 12: return isHe ? "הרפתקאות" : "Adventure"
        case 16: return isHe ? "אנימציה" : "Animation"
        case 35: return isHe ? "קומדיה" : "Comedy"
        case 80: return isHe ? "פשע" : "Crime"
        case 99: return isHe ? "דוקומנטרי" : "Documentary"
        case 18: return isHe ? "דרמה" : "Drama"
        case 10751: return isHe ? "משפחה" : "Family"
        case 14, 10765: return isHe ? "פנטזיה ומד״ב" : "Sci-Fi & Fantasy"
        case 27: return isHe ? "אימה" : "Horror"
        case 9648: return isHe ? "מסתורין" : "Mystery"
        case 10749: return isHe ? "רומנטיקה" : "Romance"
        case 878: return isHe ? "מדע בדיוני" : "Sci-Fi"
        case 53: return isHe ? "מתח" : "Thriller"
        default: return isHe ? "קולנוע" : "Cinema"
        }
    }

    // ── Studio Discovery (matches HomeViewModel.kt loadStudioCategories) ────
    func fetchStudioCategories(studio: String, isMovies: Bool) async -> [StudioCategory] {
        let mt = isMovies ? "movie" : "tv"
        let prefix = isMovies ? "discover/movie" : "discover/tv"
        let studioFilter: String
        switch studio {
        case "HBO":       studioFilter = isMovies ? "with_watch_providers=1899|384&watch_region=US" : "with_networks=49|3186"
        case "NETFLIX":   studioFilter = isMovies ? "with_watch_providers=8&watch_region=US" : "with_networks=213"
        case "APPLE_TV":  studioFilter = isMovies ? "with_watch_providers=350&watch_region=US" : "with_networks=2552"
        case "DISNEY":    studioFilter = isMovies ? "with_companies=2|3|420" : "with_networks=2739"
        case "AMAZON":    studioFilter = isMovies ? "with_watch_providers=119&watch_region=US" : "with_networks=1024"
        case "PARAMOUNT": studioFilter = isMovies ? "with_companies=4" : "with_networks=4330|67"
        case "HULU":      studioFilter = isMovies ? "with_watch_providers=15&watch_region=US" : "with_networks=453"
        case "SONY":      studioFilter = "with_companies=5|34|559"
        default: return []
        }

        let specs: [(String, String, String, String)] = [
            ("top", "Most Popular", "הכי פופולרי", "&\(studioFilter)&sort_by=popularity.desc"),
            ("action", "Action & Adventure", "פעולה והרפתקאות", "&\(studioFilter)&with_genres=28|10759&sort_by=popularity.desc"),
            ("scifi", "Sci-Fi & Fantasy", "מדע בדיוני ופנטזיה", "&\(studioFilter)&with_genres=878|14|10765&sort_by=popularity.desc"),
            ("drama", "Drama", "דרמה", "&\(studioFilter)&with_genres=18&sort_by=popularity.desc"),
            ("comedy", "Comedy", "קומדיה", "&\(studioFilter)&with_genres=35&sort_by=popularity.desc"),
            ("crime", "Crime & Mystery", "פשע ומסתורין", "&\(studioFilter)&with_genres=80|9648&sort_by=popularity.desc"),
            ("ani", "Animation", "אנימציה", "&\(studioFilter)&with_genres=16&sort_by=popularity.desc")
        ]

        var result: [StudioCategory] = []
        for (key, en, he, query) in specs {
            if let items = try? await fetchList(endpoint: prefix, defaultMediaType: mt, extraQuery: query), !items.isEmpty {
                result.append(StudioCategory(id: "studio_\(studio)_\(key)_\(mt)", titleEn: en, titleHe: he, movies: items))
            }
        }
        return result
    }

    // ── Full Media Details (Movie or TV + Credits + Images + Videos + OMDB) ─
    func fetchFullDetails(fullId: String) async throws -> (MediaDetailsInfo, [Recommendation]) {
        let isSeries = fullId.hasPrefix("tv_")
        let rawId = fullId.replacingOccurrences(of: "movie_", with: "").replacingOccurrences(of: "tv_", with: "")
        let endpoint = isSeries ? "tv/\(rawId)" : "movie/\(rawId)"
        let urlStr = "\(Constants.tmdbBaseUrl)/\(endpoint)?api_key=\(Constants.tmdbApiKey)&language=\(langParam)&append_to_response=external_ids,credits,images,videos,recommendations,reviews&include_image_language=he,en,null"

        guard let url = URL(string: urlStr) else {
            throw URLError(.badURL)
        }

        let (data, _) = try await session.data(from: url)
        guard let dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw URLError(.cannotParseResponse)
        }

        let title = (dict["title"] as? String) ?? (dict["name"] as? String) ?? ""
        var overview = (dict["overview"] as? String) ?? ""
        let posterPath = dict["poster_path"] as? String
        let backdropPath = dict["backdrop_path"] as? String

        // If Hebrew overview is empty, fallback to English overview
        if overview.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && SettingsManager.shared.isRTL {
            if let enUrl = URL(string: "\(Constants.tmdbBaseUrl)/\(endpoint)?api_key=\(Constants.tmdbApiKey)&language=en-US"),
               let (enData, _) = try? await session.data(from: enUrl),
               let enDict = try? JSONSerialization.jsonObject(with: enData) as? [String: Any],
               let enOverview = enDict["overview"] as? String, !enOverview.isEmpty {
                overview = enOverview
            } else {
                overview = "אין תקציר זמין לכותר זה."
            }
        }

        // External IDs (IMDb)
        var imdbId = (dict["imdb_id"] as? String) ?? ""
        if let ext = dict["external_ids"] as? [String: Any], let extImdb = ext["imdb_id"] as? String, !extImdb.isEmpty {
            imdbId = extImdb
        }
        if imdbId.isEmpty {
            imdbId = "tmdb:\(rawId)"
        }

        // Genres & Studios
        let genres = ((dict["genres"] as? [[String: Any]]) ?? []).compactMap { $0["name"] as? String }
        let companiesKey = isSeries ? "networks" : "production_companies"
        let studios = ((dict[companiesKey] as? [[String: Any]]) ?? []).compactMap { $0["name"] as? String }.prefix(3).map { $0 }

        // Cast & Director
        var castList: [CastMember] = []
        var directorName = ""
        if let credits = dict["credits"] as? [String: Any] {
            if let castArr = credits["cast"] as? [[String: Any]] {
                for c in castArr.prefix(15) {
                    if let cid = c["id"] as? Int,
                       let cname = c["name"] as? String,
                       let profile = c["profile_path"] as? String {
                        let character = (c["character"] as? String) ?? ""
                        castList.append(CastMember(id: "\(cid)", name: cname, character: character, imageUrl: "\(Constants.imageW300)\(profile)"))
                    }
                }
            }
            if let crewArr = credits["crew"] as? [[String: Any]] {
                if let dir = crewArr.first(where: { ($0["job"] as? String) == "Director" || ($0["job"] as? String) == "Creator" || ($0["job"] as? String) == "Executive Producer" }) {
                    directorName = (dir["name"] as? String) ?? ""
                }
            }
        }

        // Official Logo
        var logoUrl = ""
        if let images = dict["images"] as? [String: Any],
           let logos = images["logos"] as? [[String: Any]], !logos.isEmpty {
            let preferredLang = SettingsManager.shared.isRTL ? "he" : "en"
            let chosen = logos.first(where: { ($0["iso_639_1"] as? String) == preferredLang })
                ?? logos.first(where: { ($0["iso_639_1"] as? String) == "en" })
                ?? logos.first
            if let fp = chosen?["file_path"] as? String {
                logoUrl = "\(Constants.imageOriginal)\(fp)"
            }
        }

        // YouTube Trailer
        var trailerKey: String? = nil
        if let videos = dict["videos"] as? [String: Any],
           let vResults = videos["results"] as? [[String: Any]] {
            if let yt = vResults.first(where: { (($0["site"] as? String)?.lowercased() == "youtube") && (($0["type"] as? String)?.lowercased() == "trailer") }) ?? vResults.first(where: { ($0["site"] as? String)?.lowercased() == "youtube" }) {
                trailerKey = yt["key"] as? String
            }
        }

        // Recommendations
        var recommendations: [Recommendation] = []
        if let recs = dict["recommendations"] as? [String: Any],
           let recResults = recs["results"] as? [[String: Any]] {
            for r in recResults.prefix(15) {
                if let rid = r["id"] as? Int,
                   let rPoster = r["poster_path"] as? String {
                    let rType = (r["media_type"] as? String) ?? (isSeries ? "tv" : "movie")
                    let rTitle = (r["title"] as? String) ?? (r["name"] as? String) ?? ""
                    recommendations.append(Recommendation(id: "\(rType)_\(rid)", title: rTitle, posterUrl: "\(Constants.imageW300)\(rPoster)"))
                }
            }
        }

        // Reviews
        var reviews: [Review] = []
        if let revs = dict["reviews"] as? [String: Any],
           let revResults = revs["results"] as? [[String: Any]] {
            for rv in revResults.prefix(6) {
                if let author = rv["author"] as? String,
                   let content = rv["content"] as? String, !content.isEmpty {
                    let rating = (rv["author_details"] as? [String: Any])?["rating"] as? Double
                    reviews.append(Review(author: author, content: content, rating: rating))
                }
            }
        }

        let voteAvg = (dict["vote_average"] as? Double) ?? 0.0
        var finalImdbRating = voteAvg
        var rottenTomatoes: String? = nil
        var metacritic: String? = nil

        // OMDB Ratings (Rotten Tomatoes, Metacritic, IMDb)
        if imdbId.hasPrefix("tt"),
           let omdbUrl = URL(string: "\(Constants.omdbBaseUrl)/?i=\(imdbId)&apikey=\(Constants.omdbApiKey)"),
           let (omdbData, _) = try? await session.data(from: omdbUrl),
           let omdbJson = try? JSONSerialization.jsonObject(with: omdbData) as? [String: Any],
           let ratingsArr = omdbJson["Ratings"] as? [[String: String]] {
            for r in ratingsArr {
                let src = r["Source"] ?? ""
                let valStr = r["Value"] ?? ""
                if src == "Rotten Tomatoes" {
                    rottenTomatoes = valStr.replacingOccurrences(of: "%", with: "")
                } else if src == "Metacritic" {
                    metacritic = valStr.components(separatedBy: "/").first
                } else if src == "Internet Movie Database" {
                    if let d = Double(valStr.components(separatedBy: "/").first ?? "") {
                        finalImdbRating = d
                    }
                }
            }
        }

        let releaseDateStr = ((dict["release_date"] as? String) ?? (dict["first_air_date"] as? String) ?? "")
        let releaseYear = String(releaseDateStr.prefix(4))
        let runtime = (dict["runtime"] as? Int) ?? ((dict["episode_run_time"] as? [Int])?.first ?? 0)
        let totalSeasons = (dict["number_of_seasons"] as? Int) ?? 0
        let origLang = (dict["original_language"] as? String) ?? "en"

        var nextEp: NextEpisodeInfo? = nil
        if let n = dict["next_episode_to_air"] as? [String: Any] {
            let sNum = (n["season_number"] as? Int) ?? 0
            let eNum = (n["episode_number"] as? Int) ?? 0
            let eName = (n["name"] as? String) ?? "פרק חדש"
            let airDate = (n["air_date"] as? String) ?? ""
            let stillPath = n["still_path"] as? String
            nextEp = NextEpisodeInfo(
                seasonNumber: sNum,
                episodeNumber: eNum,
                name: eName,
                airDateIso: airDate,
                stillUrl: stillPath != nil ? "\(Constants.imageW300)\(stillPath!)" : ""
            )
        }

        let info = MediaDetailsInfo(
            id: "\(isSeries ? "tv" : "movie")_\(rawId)",
            imdbId: imdbId,
            title: title,
            overview: overview,
            posterUrl: Constants.posterUrl(posterPath),
            backdropUrl: Constants.backdropUrl(backdropPath ?? posterPath),
            logoUrl: logoUrl,
            isSeries: isSeries,
            releaseDate: releaseYear,
            runtimeMinutes: runtime,
            tmdbRating: voteAvg,
            imdbRating: finalImdbRating,
            ageRating: isSeries ? "TV-MA" : "R",
            studios: studios,
            originalLanguage: origLang,
            genres: genres,
            director: directorName,
            cast: castList,
            totalSeasons: totalSeasons,
            isFavorite: WatchProgressManager.shared.isInWatchlist("\(isSeries ? "tv" : "movie")_\(rawId)"),
            trailerKey: trailerKey,
            directorName: directorName,
            rottenTomatoesScore: rottenTomatoes,
            metacriticScore: metacritic,
            reviews: reviews,
            seriesStatus: (dict["status"] as? String) ?? "",
            nextEpisode: nextEp
        )

        return (info, recommendations)
    }

    // ── Fetch Season Episodes (with English fallback + Cinemeta fallback) ───
    func fetchSeasonEpisodes(tmdbId: String, imdbId: String, seasonNumber: Int) async -> [Episode] {
        let cleanTmdb = tmdbId.replacingOccurrences(of: "tv_", with: "")
        let urlStr = "\(Constants.tmdbBaseUrl)/tv/\(cleanTmdb)/season/\(seasonNumber)?api_key=\(Constants.tmdbApiKey)&language=\(langParam)"
        var episodes: [Episode] = []

        if let url = URL(string: urlStr),
           let (data, _) = try? await session.data(from: url),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let epArr = json["episodes"] as? [[String: Any]] {
            for e in epArr {
                let epNum = (e["episode_number"] as? Int) ?? 0
                guard epNum > 0 else { continue }
                let name = (e["name"] as? String) ?? "Episode \(epNum)"
                let overview = (e["overview"] as? String) ?? ""
                let stillPath = e["still_path"] as? String
                let airDate = (e["air_date"] as? String) ?? ""
                let key = WatchProgressManager.shared.episodeKey(imdbId, season: seasonNumber, episode: epNum)
                let prog = WatchProgressManager.shared.getProgress(key)

                episodes.append(Episode(
                    id: "s\(seasonNumber)e\(epNum)",
                    episodeNumber: epNum,
                    seasonNumber: seasonNumber,
                    title: name,
                    overview: overview,
                    stillUrl: Constants.backdropUrl(stillPath),
                    hasWatched: prog?.isFinished ?? false,
                    progress: Float(prog?.fraction ?? 0),
                    airDateIso: airDate
                ))
            }
        }

        return episodes.sorted { $0.episodeNumber < $1.episodeNumber }
    }

    // ── Search Movies & TV ──────────────────────────────────────────────────
    func searchMulti(query: String) async throws -> [Movie] {
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed), !encoded.isEmpty else {
            return []
        }
        return try await fetchList(endpoint: "search/multi?query=\(encoded)&include_adult=false")
    }

    // ── Actor Details & Filmography ─────────────────────────────────────────
    func fetchActorDetails(personId: String) async throws -> ActorFullDetails {
        let urlStr = "\(Constants.tmdbBaseUrl)/person/\(personId)?api_key=\(Constants.tmdbApiKey)&language=\(langParam)&append_to_response=combined_credits"
        guard let url = URL(string: urlStr) else { throw URLError(.badURL) }

        let (data, _) = try await session.data(from: url)
        guard let dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw URLError(.cannotParseResponse)
        }

        let name = (dict["name"] as? String) ?? ""
        let bio = (dict["biography"] as? String) ?? ""
        let birthday = (dict["birthday"] as? String) ?? ""
        let place = (dict["place_of_birth"] as? String) ?? ""
        let profilePath = dict["profile_path"] as? String

        var works: [Movie] = []
        if let credits = dict["combined_credits"] as? [String: Any],
           let castArr = credits["cast"] as? [[String: Any]] {
            works = castArr.compactMap { parseMovieItem($0) }
        }

        return ActorFullDetails(
            id: personId,
            name: name,
            biography: bio,
            birthday: birthday,
            placeOfBirth: place,
            profileUrl: Constants.posterUrl(profilePath),
            knownFor: Array(works.prefix(20))
        )
    }
}
