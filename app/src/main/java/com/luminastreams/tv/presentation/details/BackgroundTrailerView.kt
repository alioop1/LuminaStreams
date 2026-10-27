package com.luminastreams.tv.presentation.details

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackgroundTrailerView(videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setBackgroundColor(0) // Transparent

                isClickable = false
                isLongClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                clearFocus()
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()

                val appOrigin = "https://${context.packageName}"
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; }
                            html, body {
                                width: 100%;
                                height: 100%;
                                overflow: hidden;
                                background-color: #000000;
                            }
                            #player {
                                position: absolute;
                                top: -10%;
                                left: -10%;
                                width: 120%;
                                height: 120%;
                                pointer-events: none;
                            }
                        </style>
                        <script defer src="https://www.youtube.com/iframe_api"></script>
                    </head>
                    <body>
                        <div id="player"></div>
                        <script type="text/javascript">
                            var player;
                            function onYouTubeIframeAPIReady() {
                                player = new YT.Player('player', {
                                    height: '100%',
                                    width: '100%',
                                    videoId: '$videoId',
                                    playerVars: {
                                        'autoplay': 1,
                                        'mute': 1,
                                        'controls': 0,
                                        'showinfo': 0,
                                        'rel': 0,
                                        'loop': 1,
                                        'playlist': '$videoId',
                                        'playsinline': 1,
                                        'enablejsapi': 1,
                                        'fs': 0,
                                        'iv_load_policy': 3,
                                        'disablekb': 1,
                                        'origin': '$appOrigin'
                                    },
                                    events: {
                                        'onReady': function(e) {
                                            e.target.mute();
                                            e.target.playVideo();
                                        }
                                    }
                                });
                            }
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                
                loadDataWithBaseURL(appOrigin, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Re-load if videoId changes, though Compose key(videoId) ensures recreation
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    )
}
