import SwiftUI

struct WatchlistView: View {
    let onSelectMedia: (Movie) -> Void
    @EnvironmentObject var watchProgress: WatchProgressManager
    @EnvironmentObject var settings: SettingsManager

    private let columns = [
        GridItem(.adaptive(minimum: 112), spacing: 12)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Image(systemName: "heart.fill")
                    .foregroundColor(LuminaTheme.red)
                Text(settings.tr("הרשימה שלי ומועדפים", "My Watchlist & Favorites"))
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(.white)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            if watchProgress.watchlist.isEmpty {
                Spacer()
                HStack {
                    Spacer()
                    VStack(spacing: 10) {
                        Image(systemName: "heart.slash")
                            .font(.system(size: 42))
                            .foregroundColor(.white.opacity(0.3))
                        Text(settings.tr("עדיין לא הוספת סרטים או סדרות למועדפים", "Your watchlist is empty"))
                            .foregroundColor(.white.opacity(0.7))
                    }
                    Spacer()
                }
                Spacer()
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(watchProgress.watchlist) { movie in
                            Button {
                                onSelectMedia(movie)
                            } label: {
                                MediaCardView(movie: movie, fillsWidth: true)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 30)
                }
            }
        }
    }
}
