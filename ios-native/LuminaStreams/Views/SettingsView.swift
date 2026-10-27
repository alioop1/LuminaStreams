import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var settings: SettingsManager

    @State private var pinCode: String? = nil
    @State private var pinUrl: String? = nil
    @State private var isPollingPin: Bool = false
    @State private var pinStatusText: String = ""

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    Image(systemName: "gearshape.fill")
                        .foregroundColor(LuminaTheme.cyan)
                    Text(settings.tr("הגדרות מערכת וחשבונות", "Settings & Accounts"))
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(.white)
                }

                // ── 1. AllDebrid Account Section ────────────────────────────
                settingsCard(title: "AllDebrid • חשבון פרימיום") {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Circle()
                                .fill(settings.effectiveDebridToken.isEmpty ? LuminaTheme.red : LuminaTheme.green)
                                .frame(width: 9, height: 9)
                            Text(settings.effectiveDebridToken.isEmpty ? "לא מחובר" : "מחובר ופעיל (AD+)")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                        }

                        TextField("AllDebrid API Token", text: $settings.debridToken)
                            .textFieldStyle(.plain)
                            .font(.system(size: 13, design: .monospaced))
                            .padding(10)
                            .background(Color.black.opacity(0.4))
                            .cornerRadius(8)
                            .foregroundColor(LuminaTheme.cyan)

                        if let pin = pinCode, let urlStr = pinUrl {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("קוד התחברות: \(pin)")
                                    .font(.system(size: 18, weight: .black, design: .monospaced))
                                    .foregroundColor(LuminaTheme.gold)
                                if let u = URL(string: urlStr) {
                                    Link("לחץ כאן לאישור באתר AllDebrid", destination: u)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(LuminaTheme.cyan)
                                }
                                Text(pinStatusText)
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.75))
                            }
                            .padding(10)
                            .background(Color.black.opacity(0.35))
                            .cornerRadius(8)
                        }

                        HStack(spacing: 10) {
                            Button {
                                startPinAuth()
                            } label: {
                                Text("התחברות מהירה באמצעות קוד PIN")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 9)
                                    .background(LuminaTheme.cyan)
                                    .cornerRadius(8)
                            }

                            Button {
                                settings.debridToken = Constants.defaultAllDebridToken
                            } label: {
                                Text("שחזר טוקן מובנה")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 9)
                                    .background(Color.white.opacity(0.12))
                                    .cornerRadius(8)
                            }
                        }
                    }
                }

                // ── 2. Language & Subtitles ─────────────────────────────────
                settingsCard(title: "שפה וכתוביות") {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("שפת ממשק")
                                .foregroundColor(.white)
                            Spacer()
                            Picker("", selection: $settings.appLanguage) {
                                Text("עברית (RTL)").tag("he")
                                Text("English (LTR)").tag("en")
                            }
                            .pickerStyle(.segmented)
                            .frame(width: 190)
                        }

                        Toggle("כתוביות צהובות (Cinema Yellow)", isOn: $settings.yellowSubtitles)
                            .tint(LuminaTheme.cyan)
                            .foregroundColor(.white)

                        HStack {
                            Text("גודל גופן כתוביות")
                                .foregroundColor(.white)
                            Spacer()
                            Picker("", selection: $settings.subtitleFontScale) {
                                Text("קטן").tag("small")
                                Text("בינוני").tag("medium")
                                Text("גדול").tag("large")
                            }
                            .pickerStyle(.segmented)
                            .frame(width: 190)
                        }
                    }
                }

                // ── 3. IPTV Playlist Settings ───────────────────────────────
                settingsCard(title: "טלוויזיה חיה (IPTV M3U)") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("כתובת רשימת M3U (השאר ריק לערוצי ברירת מחדל):")
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.75))
                        TextField("https://example.com/playlist.m3u", text: $settings.iptvM3uUrl)
                            .textFieldStyle(.plain)
                            .font(.system(size: 13, design: .monospaced))
                            .padding(10)
                            .background(Color.black.opacity(0.4))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                    }
                }
            }
            .padding(16)
            .padding(.bottom, 30)
        }
    }

    private func settingsCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(LuminaTheme.cyan)
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LuminaTheme.cardSurface)
        .cornerRadius(14)
    }

    private func startPinAuth() {
        guard !isPollingPin else { return }
        isPollingPin = true
        pinStatusText = "ממתין לאישור בדפדפן..."
        Task {
            do {
                let res = try await AllDebridService.shared.requestPin()
                self.pinCode = res.pin
                self.pinUrl = res.userUrl

                for _ in 0..<60 {
                    try await Task.sleep(nanoseconds: 4_000_000_000)
                    if let key = try await AllDebridService.shared.checkPinStatus(pin: res.pin, check: res.check) {
                        self.settings.debridToken = key
                        self.pinStatusText = "החשבון אומת בהצלחה!"
                        self.pinCode = nil
                        self.isPollingPin = false
                        return
                    }
                }
                self.pinStatusText = "פג תוקף הקוד, נסה שוב."
                self.isPollingPin = false
            } catch {
                self.pinStatusText = error.localizedDescription
                self.isPollingPin = false
            }
        }
    }
}
