import SwiftUI

enum LuminaTheme {
    // Deep Space Blue-Black + Neon Cyan + Electric Purple (matches Android TV Color.kt)
    static let blackBg     = Color(red: 0x07 / 255.0, green: 0x0B / 255.0, blue: 0x14 / 255.0)
    static let navy        = Color(red: 0x0D / 255.0, green: 0x15 / 255.0, blue: 0x25 / 255.0)
    static let cardSurface = Color(red: 0x13 / 255.0, green: 0x1D / 255.0, blue: 0x33 / 255.0)
    static let cyan        = Color(red: 0x00 / 255.0, green: 0xD4 / 255.0, blue: 0xFF / 255.0)
    static let purple      = Color(red: 0x7B / 255.0, green: 0x61 / 255.0, blue: 0xFF / 255.0)
    static let red         = Color(red: 0xFF / 255.0, green: 0x3B / 255.0, blue: 0x5C / 255.0)
    static let green       = Color(red: 0x22 / 255.0, green: 0xC5 / 255.0, blue: 0x5E / 255.0)
    static let gold        = Color(red: 0xF5 / 255.0, green: 0xC5 / 255.0, blue: 0x18 / 255.0)
    static let textPrimary = Color(red: 0xEE / 255.0, green: 0xF2 / 255.0, blue: 0xFF / 255.0)
    static let textSecondary = Color.white.opacity(0.68)
    static let glassBg     = Color(red: 0x0D / 255.0, green: 0x15 / 255.0, blue: 0x25 / 255.0).opacity(0.78)

    static let accentGradient = LinearGradient(
        colors: [cyan, purple],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let heroBottomGradient = LinearGradient(
        colors: [.clear, blackBg.opacity(0.6), blackBg],
        startPoint: .top,
        endPoint: .bottom
    )
}
