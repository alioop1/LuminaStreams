import Foundation
import SwiftUI

final class SettingsManager: ObservableObject {
    static let shared = SettingsManager()

    @AppStorage("app_lang") var appLanguage: String = "he"
    @AppStorage("ad_api_token") var debridToken: String = Constants.defaultAllDebridToken
    @AppStorage("preferred_audio_lang") var preferredAudioLang: String = "original"
    @AppStorage("def_subs") var defaultSubtitles: String = "he"
    @AppStorage("yellow_subs") var yellowSubtitles: Bool = false
    @AppStorage("subtitle_font_scale") var subtitleFontScale: String = "medium"
    @AppStorage("max_resolution") var maxResolution: String = "Unlimited"
    @AppStorage("max_file_size") var maxFileSize: String = "Unlimited"
    @AppStorage("ktuvit_email") var ktuvitEmail: String = ""
    @AppStorage("ktuvit_password") var ktuvitPassword: String = ""
    @AppStorage("iptv_m3u_url") var iptvM3uUrl: String = ""
    @AppStorage("iptv_epg_url") var iptvEpgUrl: String = ""

    var isRTL: Bool {
        appLanguage == "he"
    }

    var effectiveDebridToken: String {
        let trimmed = debridToken.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? Constants.defaultAllDebridToken : trimmed
    }

    var subtitleFontSize: CGFloat {
        switch subtitleFontScale {
        case "small": return 15
        case "large": return 22
        default: return 18
        }
    }

    func tr(_ he: String, _ en: String) -> String {
        isRTL ? he : en
    }
}
