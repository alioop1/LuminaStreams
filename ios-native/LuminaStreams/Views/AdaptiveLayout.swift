import SwiftUI

// ── Shared building blocks that keep every screen inside the device width ──

/// Sizes that scale with the device: compact (iPhone) vs regular (iPad, large iPhone landscape).
struct LayoutMetrics {
    let isRegular: Bool
    let isLandscape: Bool

    init(horizontal: UserInterfaceSizeClass?, vertical: UserInterfaceSizeClass?) {
        isRegular = horizontal == .regular
        isLandscape = vertical == .compact
    }

    var heroHeight: CGFloat {
        if isRegular { return isLandscape ? 420 : 520 }
        return isLandscape ? 240 : 340
    }
    var backdropHeight: CGFloat { isRegular ? 460 : 320 }
    var posterWidth: CGFloat { isRegular ? 160 : 120 }
    var posterHeight: CGFloat { isRegular ? 234 : 175 }
    var landscapeCardWidth: CGFloat { isRegular ? 260 : 190 }
    var landscapeCardHeight: CGFloat { isRegular ? 146 : 108 }
    var gridMinimum: CGFloat { isRegular ? 150 : 104 }
    var horizontalPadding: CGFloat { isRegular ? 28 : 16 }
}

/// An aspect-filled image that always takes exactly the available width.
/// A bare `.aspectRatio(.fill).frame(height:)` reports the image's natural width,
/// which can exceed the screen and push the whole page sideways.
struct FullWidthImage: View {
    let url: String
    let height: CGFloat

    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay {
                LuminaAsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle().fill(LuminaTheme.cardSurface)
                }
            }
            .clipped()
    }
}

/// Horizontal scroller that behaves in right-to-left layouts.
/// SwiftUI misplaces RTL scroll content that is narrower than the viewport
/// (it starts on the wrong side and jumps when touched); pinning the content to
/// at least the viewport width, aligned to the leading edge, avoids that.
struct HScroll<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var viewportWidth: CGFloat = 0

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            content()
                .frame(minWidth: viewportWidth, alignment: .leading)
        }
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { viewportWidth = geo.size.width }
                    .onChange(of: geo.size.width) { newWidth in viewportWidth = newWidth }
            }
        )
    }
}
