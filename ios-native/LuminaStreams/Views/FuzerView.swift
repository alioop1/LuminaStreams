import SwiftUI

struct FuzerView: View {
    let onSelectMedia: (Movie) -> Void
    @EnvironmentObject var settings: SettingsManager

    @State private var selectedCatId: Int = 7
    @State private var searchQuery: String = ""
    @State private var items: [Movie] = []
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var needsSignIn: Bool = false
    @State private var showSignIn: Bool = false

    private let categories: [(id: Int, title: String)] = [
        (7, "סרטים HD"),
        (9, "סרטים 4K / Bluray"),
        (60, "סרטים מדובבים"),
        (8, "סדרות HD"),
        (61, "סדרות ישראליות")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Header + Search Bar
            HStack(spacing: 10) {
                Image(systemName: "sparkles")
                    .foregroundColor(LuminaTheme.cyan)
                Text("Fuzer Israel • שחרורים ישירים")
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(.white)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            HStack(spacing: 10) {
                TextField("חפש שחרור ב-Fuzer...", text: $searchQuery)
                    .textFieldStyle(.plain)
                    .padding(10)
                    .background(LuminaTheme.cardSurface)
                    .cornerRadius(10)
                    .foregroundColor(.white)
                    .onSubmit {
                        Task { await performSearch() }
                    }

                Button {
                    Task { await performSearch() }
                } label: {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.black)
                        .padding(11)
                        .background(LuminaTheme.cyan)
                        .cornerRadius(10)
                }
            }
            .padding(.horizontal, 16)

            // Category Pills
            HScroll {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.id) { cat in
                        let isSelected = selectedCatId == cat.id
                        Button {
                            selectedCatId = cat.id
                            Task { await loadCategory(cat.id) }
                        } label: {
                            Text(cat.title)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(isSelected ? .black : .white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Capsule().fill(isSelected ? LuminaTheme.cyan : LuminaTheme.cardSurface))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }

            if isLoading {
                Spacer()
                HStack {
                    Spacer()
                    ProgressView("מתחבר ל-Fuzer ומושך שחרורים...")
                        .tint(LuminaTheme.cyan)
                        .foregroundColor(.white)
                    Spacer()
                }
                Spacer()
            } else if needsSignIn {
                Spacer()
                VStack(spacing: 14) {
                    Image(systemName: "person.crop.circle.badge.exclamationmark")
                        .font(.system(size: 40))
                        .foregroundColor(LuminaTheme.cyan)
                    Text(settings.tr("Fuzer דורש התחברות (כולל אימות reCAPTCHA).\nהתחבר פעם אחת והאפליקציה תזכור אותך.",
                                     "Fuzer requires signing in (with reCAPTCHA).\nSign in once and the app remembers you."))
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.85))
                        .multilineTextAlignment(.center)
                    Button {
                        showSignIn = true
                    } label: {
                        Text(settings.tr("התחבר ל-Fuzer", "Sign in to Fuzer"))
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.black)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 11)
                            .background(LuminaTheme.cyan)
                            .clipShape(Capsule())
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 24)
                Spacer()
            } else if let err = errorMessage {
                Spacer()
                HStack {
                    Spacer()
                    Text(err).foregroundColor(LuminaTheme.red)
                    Spacer()
                }
                Spacer()
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 10) {
                        ForEach(items) { item in
                            Button {
                                onSelectMedia(item)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "bolt.horizontal.circle.fill")
                                        .font(.system(size: 28))
                                        .foregroundColor(LuminaTheme.cyan)

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(item.title)
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(2)
                                        HStack(spacing: 8) {
                                            Text(item.resolutionBadge)
                                                .font(.system(size: 10, weight: .black))
                                                .foregroundColor(.black)
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 2)
                                                .background(LuminaTheme.cyan)
                                                .cornerRadius(4)
                                            Text("ניגון ישיר דרך AllDebrid")
                                                .font(.system(size: 11))
                                                .foregroundColor(.white.opacity(0.65))
                                        }
                                    }
                                    Spacer()
                                    Image(systemName: "play.fill")
                                        .foregroundColor(LuminaTheme.cyan)
                                }
                                .padding(12)
                                .background(LuminaTheme.cardSurface)
                                .cornerRadius(12)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 30)
                }
            }
        }
        .task {
            if items.isEmpty {
                await loadCategory(selectedCatId)
            }
        }
        .sheet(isPresented: $showSignIn) {
            FuzerLoginView {
                Task { await loadCategory(selectedCatId) }
            }
        }
    }

    private func handle(_ error: Error) {
        if (error as NSError).domain == "Fuzer" && (error as NSError).code == 401 {
            needsSignIn = true
        } else {
            errorMessage = error.localizedDescription
        }
    }

    private func loadCategory(_ catId: Int) async {
        isLoading = true
        errorMessage = nil
        needsSignIn = false
        do {
            items = try await FuzerService.shared.fetchCategory(catId: catId)
            isLoading = false
        } catch {
            isLoading = false
            handle(error)
        }
    }

    private func performSearch() async {
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        needsSignIn = false
        do {
            items = try await FuzerService.shared.search(query: q)
            isLoading = false
        } catch {
            isLoading = false
            handle(error)
        }
    }
}
