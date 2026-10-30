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
/// SwiftUI's RTL horizontal ScrollView computes its starting offset from the first
/// layout; when the width changes afterwards (sheets sliding in, rotation) the
/// content shows from the wrong end and jumps when touched. In RTL this runs the
/// ScrollView in plain LTR mode and mirrors it, then mirrors the content back:
/// offset 0 then shows the right-hand (leading) end, text renders normally and
/// swiping follows the finger as expected.
struct HScroll<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @Environment(\.layoutDirection) private var layoutDirection

    var body: some View {
        if layoutDirection == .rightToLeft {
            ScrollView(.horizontal, showsIndicators: false) {
                content()
                    .environment(\.layoutDirection, .rightToLeft)
                    .scaleEffect(x: -1, y: 1)
            }
            .environment(\.layoutDirection, .leftToRight)
            .scaleEffect(x: -1, y: 1)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                content()
            }
        }
    }
}
