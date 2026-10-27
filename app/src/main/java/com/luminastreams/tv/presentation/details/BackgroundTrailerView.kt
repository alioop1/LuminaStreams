package com.luminastreams.tv.presentation.details

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
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
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setBackgroundColor(0) // Transparent

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                    }
                }
                webChromeClient = WebChromeClient()
                
                // CSS trick to hide YouTube chrome/black bars and zoom in slightly
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <body style="margin:0;padding:0;overflow:hidden;background-color:transparent;">
                        <div style="position:relative;width:100vw;height:100vh;pointer-events:none;">
                            <iframe 
                                id="player"
                                src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&controls=0&showinfo=0&rel=0&loop=1&playlist=$videoId&modestbranding=1&playsinline=1&enablejsapi=1&fs=0&iv_load_policy=3&disablekb=1" 
                                style="position:absolute;top:-10%;left:-10%;width:120%;height:120%;border:none;pointer-events:none;" 
                                allow="autoplay; encrypted-media">
                            </iframe>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Re-load if videoId changes, though ideally we don't need this if we recreate the Composable
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
