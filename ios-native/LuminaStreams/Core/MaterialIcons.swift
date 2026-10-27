import SwiftUI

// MARK: - Exact Material Design SVG Vector Shapes (1:1 with Android TV Compose)

struct MaterialSearchIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)
        
        var p = Path()
        p.move(to: CGPoint(x: 15.5, y: 14))
        p.addLine(to: CGPoint(x: 14.71, y: 14))
        p.addLine(to: CGPoint(x: 14.43, y: 13.73))
        p.addCurve(to: CGPoint(x: 16, y: 9.5), control1: CGPoint(x: 15.41, y: 12.59), control2: CGPoint(x: 16, y: 11.11))
        p.addCurve(to: CGPoint(x: 9.5, y: 3), control1: CGPoint(x: 16, y: 5.91), control2: CGPoint(x: 13.09, y: 3))
        p.addCurve(to: CGPoint(x: 3, y: 9.5), control1: CGPoint(x: 5.91, y: 3), control2: CGPoint(x: 3, y: 5.91))
        p.addCurve(to: CGPoint(x: 9.5, y: 16), control1: CGPoint(x: 3, y: 13.09), control2: CGPoint(x: 5.91, y: 16))
        p.addCurve(to: CGPoint(x: 13.73, y: 14.43), control1: CGPoint(x: 11.11, y: 16), control2: CGPoint(x: 12.59, y: 15.41))
        p.addLine(to: CGPoint(x: 14, y: 14.71))
        p.addLine(to: CGPoint(x: 14, y: 15.5))
        p.addLine(to: CGPoint(x: 19, y: 20.49))
        p.addLine(to: CGPoint(x: 20.49, y: 19))
        p.addLine(to: CGPoint(x: 15.5, y: 14))
        p.closeSubpath()

        // Inner circle hole
        p.move(to: CGPoint(x: 9.5, y: 14))
        p.addCurve(to: CGPoint(x: 5, y: 9.5), control1: CGPoint(x: 7.01, y: 14), control2: CGPoint(x: 5, y: 11.99))
        p.addCurve(to: CGPoint(x: 9.5, y: 5), control1: CGPoint(x: 5, y: 7.01), control2: CGPoint(x: 7.01, y: 5))
        p.addCurve(to: CGPoint(x: 14, y: 9.5), control1: CGPoint(x: 11.99, y: 5), control2: CGPoint(x: 14, y: 7.01))
        p.addCurve(to: CGPoint(x: 9.5, y: 14), control1: CGPoint(x: 14, y: 11.99), control2: CGPoint(x: 11.99, y: 14))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialHomeIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 10, y: 20))
        p.addLine(to: CGPoint(x: 10, y: 14))
        p.addLine(to: CGPoint(x: 14, y: 14))
        p.addLine(to: CGPoint(x: 14, y: 20))
        p.addLine(to: CGPoint(x: 19, y: 20))
        p.addLine(to: CGPoint(x: 19, y: 12))
        p.addLine(to: CGPoint(x: 22, y: 12))
        p.addLine(to: CGPoint(x: 12, y: 3))
        p.addLine(to: CGPoint(x: 2, y: 12))
        p.addLine(to: CGPoint(x: 5, y: 12))
        p.addLine(to: CGPoint(x: 5, y: 20))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialMovieIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 18, y: 4))
        p.addLine(to: CGPoint(x: 20, y: 8))
        p.addLine(to: CGPoint(x: 17, y: 8))
        p.addLine(to: CGPoint(x: 15, y: 4))
        p.addLine(to: CGPoint(x: 13, y: 4))
        p.addLine(to: CGPoint(x: 15, y: 8))
        p.addLine(to: CGPoint(x: 12, y: 8))
        p.addLine(to: CGPoint(x: 10, y: 4))
        p.addLine(to: CGPoint(x: 8, y: 4))
        p.addLine(to: CGPoint(x: 10, y: 8))
        p.addLine(to: CGPoint(x: 7, y: 8))
        p.addLine(to: CGPoint(x: 5, y: 4))
        p.addLine(to: CGPoint(x: 4, y: 4))
        p.addCurve(to: CGPoint(x: 2.01, y: 6), control1: CGPoint(x: 2.9, y: 4), control2: CGPoint(x: 2.01, y: 4.9))
        p.addLine(to: CGPoint(x: 2, y: 18))
        p.addCurve(to: CGPoint(x: 4, y: 20), control1: CGPoint(x: 2, y: 19.1), control2: CGPoint(x: 2.9, y: 20))
        p.addLine(to: CGPoint(x: 20, y: 20))
        p.addCurve(to: CGPoint(x: 22, y: 18), control1: CGPoint(x: 21.1, y: 20), control2: CGPoint(x: 22, y: 19.1))
        p.addLine(to: CGPoint(x: 22, y: 4))
        p.addLine(to: CGPoint(x: 18, y: 4))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialTvIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 21, y: 3))
        p.addLine(to: CGPoint(x: 3, y: 3))
        p.addCurve(to: CGPoint(x: 1, y: 5), control1: CGPoint(x: 1.9, y: 3), control2: CGPoint(x: 1, y: 3.9))
        p.addLine(to: CGPoint(x: 1, y: 17))
        p.addCurve(to: CGPoint(x: 3, y: 19), control1: CGPoint(x: 1, y: 18.1), control2: CGPoint(x: 1.9, y: 19))
        p.addLine(to: CGPoint(x: 8, y: 19))
        p.addLine(to: CGPoint(x: 8, y: 21))
        p.addLine(to: CGPoint(x: 16, y: 21))
        p.addLine(to: CGPoint(x: 16, y: 19))
        p.addLine(to: CGPoint(x: 21, y: 19))
        p.addCurve(to: CGPoint(x: 23, y: 17), control1: CGPoint(x: 22.1, y: 19), control2: CGPoint(x: 23, y: 18.1))
        p.addLine(to: CGPoint(x: 23, y: 5))
        p.addCurve(to: CGPoint(x: 21, y: 3), control1: CGPoint(x: 23, y: 3.9), control2: CGPoint(x: 22.1, y: 3))
        p.closeSubpath()

        p.move(to: CGPoint(x: 21, y: 17))
        p.addLine(to: CGPoint(x: 3, y: 17))
        p.addLine(to: CGPoint(x: 3, y: 5))
        p.addLine(to: CGPoint(x: 21, y: 5))
        p.addLine(to: CGPoint(x: 21, y: 17))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialLiveTvIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 9, y: 10))
        p.addLine(to: CGPoint(x: 9, y: 18))
        p.addLine(to: CGPoint(x: 16, y: 14))
        p.closeSubpath()

        p.move(to: CGPoint(x: 21, y: 6))
        p.addLine(to: CGPoint(x: 13.42, y: 6))
        p.addLine(to: CGPoint(x: 16.71, y: 2.71))
        p.addLine(to: CGPoint(x: 16, y: 2))
        p.addLine(to: CGPoint(x: 12, y: 6))
        p.addLine(to: CGPoint(x: 8, y: 2))
        p.addLine(to: CGPoint(x: 7.31, y: 2.71))
        p.addLine(to: CGPoint(x: 10.56, y: 6))
        p.addLine(to: CGPoint(x: 3, y: 6))
        p.addCurve(to: CGPoint(x: 1, y: 8), control1: CGPoint(x: 1.9, y: 6), control2: CGPoint(x: 1, y: 6.9))
        p.addLine(to: CGPoint(x: 1, y: 20))
        p.addCurve(to: CGPoint(x: 3, y: 22), control1: CGPoint(x: 1, y: 21.1), control2: CGPoint(x: 1.9, y: 22))
        p.addLine(to: CGPoint(x: 21, y: 22))
        p.addCurve(to: CGPoint(x: 23, y: 20), control1: CGPoint(x: 22.1, y: 22), control2: CGPoint(x: 23, y: 21.1))
        p.addLine(to: CGPoint(x: 23, y: 8))
        p.addCurve(to: CGPoint(x: 21, y: 6), control1: CGPoint(x: 23, y: 6.9), control2: CGPoint(x: 22.1, y: 6))
        p.closeSubpath()

        p.move(to: CGPoint(x: 21, y: 20))
        p.addLine(to: CGPoint(x: 3, y: 20))
        p.addLine(to: CGPoint(x: 3, y: 8))
        p.addLine(to: CGPoint(x: 21, y: 8))
        p.addLine(to: CGPoint(x: 21, y: 20))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialAutoAwesomeIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 19, y: 9))
        p.addLine(to: CGPoint(x: 20.25, y: 6.25))
        p.addLine(to: CGPoint(x: 23, y: 5))
        p.addLine(to: CGPoint(x: 20.25, y: 3.75))
        p.addLine(to: CGPoint(x: 19, y: 1))
        p.addLine(to: CGPoint(x: 17.75, y: 3.75))
        p.addLine(to: CGPoint(x: 15, y: 5))
        p.addLine(to: CGPoint(x: 17.75, y: 6.25))
        p.closeSubpath()

        p.move(to: CGPoint(x: 11.5, y: 9.5))
        p.addLine(to: CGPoint(x: 9, y: 4))
        p.addLine(to: CGPoint(x: 6.5, y: 9.5))
        p.addLine(to: CGPoint(x: 1, y: 12))
        p.addLine(to: CGPoint(x: 6.5, y: 14.5))
        p.addLine(to: CGPoint(x: 9, y: 20))
        p.addLine(to: CGPoint(x: 11.5, y: 14.5))
        p.addLine(to: CGPoint(x: 17, y: 12))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialCloudIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 19.35, y: 10.04))
        p.addCurve(to: CGPoint(x: 12, y: 4), control1: CGPoint(x: 18.67, y: 6.59), control2: CGPoint(x: 15.64, y: 4))
        p.addCurve(to: CGPoint(x: 5.35, y: 8.04), control1: CGPoint(x: 9.11, y: 4), control2: CGPoint(x: 6.6, y: 5.64))
        p.addCurve(to: CGPoint(x: 0, y: 14), control1: CGPoint(x: 2.34, y: 8.36), control2: CGPoint(x: 0, y: 10.91))
        p.addCurve(to: CGPoint(x: 6, y: 20), control1: CGPoint(x: 0, y: 17.31), control2: CGPoint(x: 2.69, y: 20))
        p.addLine(to: CGPoint(x: 19, y: 20))
        p.addCurve(to: CGPoint(x: 24, y: 15), control1: CGPoint(x: 21.76, y: 20), control2: CGPoint(x: 24, y: 17.76))
        p.addCurve(to: CGPoint(x: 19.35, y: 10.04), control1: CGPoint(x: 24, y: 12.36), control2: CGPoint(x: 21.95, y: 10.22))
        p.closeSubpath()

        p.move(to: CGPoint(x: 17, y: 13))
        p.addLine(to: CGPoint(x: 12, y: 18))
        p.addLine(to: CGPoint(x: 7, y: 13))
        p.addLine(to: CGPoint(x: 10, y: 13))
        p.addLine(to: CGPoint(x: 10, y: 9))
        p.addLine(to: CGPoint(x: 14, y: 9))
        p.addLine(to: CGPoint(x: 14, y: 13))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialFavoriteIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 12, y: 21.35))
        p.addLine(to: CGPoint(x: 10.55, y: 20.03))
        p.addCurve(to: CGPoint(x: 2, y: 8.5), control1: CGPoint(x: 5.4, y: 15.36), control2: CGPoint(x: 2, y: 12.28))
        p.addCurve(to: CGPoint(x: 7.5, y: 3), control1: CGPoint(x: 2, y: 5.42), control2: CGPoint(x: 4.42, y: 3))
        p.addCurve(to: CGPoint(x: 12, y: 5.09), control1: CGPoint(x: 9.24, y: 3), control2: CGPoint(x: 10.91, y: 3.81))
        p.addCurve(to: CGPoint(x: 16.5, y: 3), control1: CGPoint(x: 13.09, y: 3.81), control2: CGPoint(x: 14.76, y: 3))
        p.addCurve(to: CGPoint(x: 22, y: 8.5), control1: CGPoint(x: 19.58, y: 3), control2: CGPoint(x: 22, y: 5.42))
        p.addCurve(to: CGPoint(x: 13.45, y: 20.04), control1: CGPoint(x: 22, y: 12.28), control2: CGPoint(x: 18.6, y: 15.36))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}

struct MaterialSettingsIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let sx = rect.width / 24.0
        let sy = rect.height / 24.0
        let t = CGAffineTransform(scaleX: sx, y: sy)

        var p = Path()
        p.move(to: CGPoint(x: 19.14, y: 12.94))
        p.addCurve(to: CGPoint(x: 19.2, y: 12), control1: CGPoint(x: 19.18, y: 12.64), control2: CGPoint(x: 19.2, y: 12.33))
        p.addCurve(to: CGPoint(x: 19.13, y: 11.06), control1: CGPoint(x: 19.2, y: 11.68), control2: CGPoint(x: 19.18, y: 11.36))
        p.addLine(to: CGPoint(x: 21.16, y: 9.48))
        p.addLine(to: CGPoint(x: 19.24, y: 6.16))
        p.addLine(to: CGPoint(x: 16.85, y: 7.12))
        p.addCurve(to: CGPoint(x: 15.23, y: 6.18), control1: CGPoint(x: 16.35, y: 6.74), control2: CGPoint(x: 15.82, y: 6.42))
        p.addLine(to: CGPoint(x: 14.87, y: 3.64))
        p.addLine(to: CGPoint(x: 11.03, y: 3.64))
        p.addLine(to: CGPoint(x: 10.67, y: 6.18))
        p.addCurve(to: CGPoint(x: 9.05, y: 7.12), control1: CGPoint(x: 10.08, y: 6.42), control2: CGPoint(x: 9.55, y: 6.74))
        p.addLine(to: CGPoint(x: 6.66, y: 6.16))
        p.addLine(to: CGPoint(x: 4.74, y: 9.48))
        p.addLine(to: CGPoint(x: 6.77, y: 11.06))
        p.addCurve(to: CGPoint(x: 6.7, y: 12), control1: CGPoint(x: 6.72, y: 11.36), control2: CGPoint(x: 6.7, y: 11.69))
        p.addCurve(to: CGPoint(x: 6.77, y: 12.94), control1: CGPoint(x: 6.7, y: 12.32), control2: CGPoint(x: 6.72, y: 12.65))
        p.addLine(to: CGPoint(x: 4.74, y: 14.52))
        p.addLine(to: CGPoint(x: 6.66, y: 17.84))
        p.addLine(to: CGPoint(x: 9.05, y: 16.88))
        p.addCurve(to: CGPoint(x: 10.67, y: 17.82), control1: CGPoint(x: 9.55, y: 17.26), control2: CGPoint(x: 10.08, y: 17.58))
        p.addLine(to: CGPoint(x: 11.03, y: 20.36))
        p.addLine(to: CGPoint(x: 14.87, y: 20.36))
        p.addLine(to: CGPoint(x: 15.23, y: 17.82))
        p.addCurve(to: CGPoint(x: 16.85, y: 16.88), control1: CGPoint(x: 15.82, y: 17.58), control2: CGPoint(x: 16.35, y: 17.26))
        p.addLine(to: CGPoint(x: 19.24, y: 17.84))
        p.addLine(to: CGPoint(x: 21.16, y: 14.52))
        p.closeSubpath()

        // Center ring
        p.move(to: CGPoint(x: 12, y: 15.6))
        p.addCurve(to: CGPoint(x: 8.4, y: 12), control1: CGPoint(x: 10.02, y: 15.6), control2: CGPoint(x: 8.4, y: 13.98))
        p.addCurve(to: CGPoint(x: 12, y: 8.4), control1: CGPoint(x: 8.4, y: 10.02), control2: CGPoint(x: 10.02, y: 8.4))
        p.addCurve(to: CGPoint(x: 15.6, y: 12), control1: CGPoint(x: 13.98, y: 8.4), control2: CGPoint(x: 15.6, y: 10.02))
        p.addCurve(to: CGPoint(x: 12, y: 15.6), control1: CGPoint(x: 15.6, y: 13.98), control2: CGPoint(x: 13.98, y: 15.6))
        p.closeSubpath()

        path.addPath(p, transform: t)
        return path
    }
}
