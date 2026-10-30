import SwiftUI
import WebKit

/// Fuzer's own login page in a web view. Fuzer protects sign-in with reCAPTCHA,
/// so the user signs in here; once the page leaves login.php the session
/// cookies are handed to FuzerService.
struct FuzerLoginView: View {
    let onSignedIn: () -> Void
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsManager

    var body: some View {
        NavigationStack {
            FuzerLoginWebView {
                onSignedIn()
                dismiss()
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(settings.tr("התחברות ל-Fuzer", "Sign in to Fuzer"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(settings.tr("סגור", "Close")) { dismiss() }
                }
            }
        }
    }
}

private struct FuzerLoginWebView: UIViewRepresentable {
    let onSignedIn: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onSignedIn: onSignedIn) }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
        webView.customUserAgent = FuzerService.desktopUserAgent
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .black
        if let url = URL(string: "\(Constants.fuzerBaseUrl)/login.php") {
            webView.load(URLRequest(url: url))
        }
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        let onSignedIn: () -> Void
        private var finished = false

        init(onSignedIn: @escaping () -> Void) {
            self.onSignedIn = onSignedIn
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard !finished, let url = webView.url,
                  url.host?.contains("fuzer.xyz") == true,
                  !url.path.lowercased().contains("login") else { return }
            finished = true
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
                FuzerService.shared.importSession(cookies)
                DispatchQueue.main.async { self.onSignedIn() }
            }
        }
    }
}
