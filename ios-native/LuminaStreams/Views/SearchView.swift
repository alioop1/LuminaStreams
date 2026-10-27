import SwiftUI

struct SearchView: View {
    let onSelectMedia: (Movie) -> Void
    @StateObject private var vm = SearchViewModel()
    @EnvironmentObject var settings: SettingsManager

    private let columns = [
        GridItem(.adaptive(minimum: 112), spacing: 12)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(LuminaTheme.cyan)
                TextField(settings.tr("חפש סרטים, סדרות או שחרורי Fuzer...", "Search movies, series, or Fuzer..."), text: $vm.query)
                    .textFieldStyle(.plain)
                    .foregroundColor(.white)
                    .onChange(of: vm.query) { newVal in
                        vm.onQueryChanged(newVal)
                    }
                if !vm.query.isEmpty {
                    Button {
                        vm.query = ""
                        vm.results = []
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
            }
            .padding(12)
            .background(LuminaTheme.cardSurface)
            .cornerRadius(12)
            .padding(.horizontal, 16)
            .padding(.top, 8)

            // Filter Pills
            HStack(spacing: 8) {
                ForEach([("all", "הכל"), ("movie", "סרטים"), ("tv", "סדרות"), ("fuzer", "Fuzer")], id: \.0) { key, label in
                    let isSelected = vm.selectedFilter == key
                    Button {
                        vm.selectedFilter = key
                    } label: {
                        Text(label)
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

            if vm.isSearching {
                Spacer()
                HStack {
                    Spacer()
                    ProgressView().tint(LuminaTheme.cyan)
                    Spacer()
                }
                Spacer()
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(vm.filteredResults) { movie in
                            Button {
                                onSelectMedia(movie)
                            } label: {
                                MediaCardView(movie: movie)
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
