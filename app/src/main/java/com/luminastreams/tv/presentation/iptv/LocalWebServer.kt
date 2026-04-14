package com.luminastreams.tv.presentation.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

object LocalWebServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    suspend fun start(port: Int = 8080, onPlaylistReceived: (name: String, url: String, epgUrl: String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.reuseAddress = true // מונע קריסות אם הפורט תפוס זמנית
                isRunning = true
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    // הרצת כל חיבור בחוט נפרד כדי לא לתקוע את השרת
                    launch(Dispatchers.IO) {
                        handleClient(client, onPlaylistReceived)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket, onPlaylistReceived: (String, String, String) -> Unit) {
        try {
            socket.soTimeout = 5000 // שחרור אוטומטי אחרי 5 שניות אם הדפדפן נתקע
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val out = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // התעלמות מבקשות של הדפדפן לאייקונים שתוקעות שרתים מקומיים
            if (path.contains("favicon.ico")) {
                out.print("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n")
                out.flush()
                return
            }

            var contentLength = 0
            while (true) {
                val headerLine = reader.readLine()
                if (headerLine.isNullOrBlank()) break // נקודת העצירה הבטוחה של סוף כותרות
                if (headerLine.lowercase().startsWith("content-length:")) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (method == "GET") {
                serveHtmlPage(out)
            } else if (method == "POST") {
                val bodyChars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val result = reader.read(bodyChars, read, contentLength - read)
                    if (result == -1) break
                    read += result
                }
                val body = String(bodyChars)

                val params = body.split("&").associate {
                    val paramParts = it.split("=")
                    val key = URLDecoder.decode(paramParts.getOrNull(0) ?: "", "UTF-8")
                    val value = URLDecoder.decode(paramParts.getOrNull(1) ?: "", "UTF-8")
                    key to value
                }

                val name = params["name"] ?: "My Playlist"
                val url = params["url"] ?: ""
                val epg = params["epg"] ?: ""

                if (url.isNotBlank()) {
                    onPlaylistReceived(name, url, epg)
                    serveSuccessPage(out)
                } else {
                    serveHtmlPage(out)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // הכי חשוב: סגירת הסוקט מיד בסיום מחזירה את השליטה לטלפון
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveHtmlPage(out: PrintWriter) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>Lumina IPTV Setup</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0c0c14; color: white; padding: 20px; margin: 0; }
                    .container { max-width: 400px; margin: 20px auto; background: #1e1e2e; padding: 24px; border-radius: 16px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
                    h2 { margin-top: 0; color: #0a84ff; text-align: center; font-size: 24px; }
                    p { text-align: center; color: #aaa; font-size: 14px; margin-bottom: 24px; }
                    label { display: block; margin-top: 16px; font-size: 14px; font-weight: bold; color: #eee; }
                    input[type="text"], input[type="url"] { width: 100%; padding: 14px; margin-top: 8px; border-radius: 10px; border: 1px solid #333; background: #050508; color: white; box-sizing: border-box; font-size: 16px; outline: none; }
                    input:focus { border-color: #0a84ff; }
                    button { margin-top: 32px; width: 100%; padding: 16px; background-color: #0a84ff; color: white; border: none; border-radius: 10px; font-size: 18px; font-weight: bold; cursor: pointer; }
                    button:active { background-color: #0066cc; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Lumina Streams</h2>
                    <p>Paste your playlist URLs below to send them directly to your TV.</p>
                    <form method="POST" action="/">
                        <label>Playlist Name</label>
                        <input type="text" name="name" placeholder="e.g. My IPTV" required>
                        <label>M3U / M3U8 URL *</label>
                        <input type="url" name="url" placeholder="http://..." required>
                        <label>EPG URL (Optional)</label>
                        <input type="url" name="epg" placeholder="http://...xml.gz">
                        <button type="submit">Send to TV</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()

        out.print("HTTP/1.1 200 OK\r\n")
        out.print("Content-Type: text/html; charset=UTF-8\r\n")
        out.print("Connection: close\r\n")
        out.print("\r\n")
        out.print(html)
        out.flush()
    }

    private fun serveSuccessPage(out: PrintWriter) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Success</title>
                <style>
                    body { font-family: sans-serif; background-color: #0c0c14; color: white; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; text-align: center; } 
                    .card { background: #1e1e2e; padding: 40px; border-radius: 16px; }
                    h1 { color: #30d158; margin-top: 0; }
                    p { color: #aaa; font-size: 16px; margin-bottom: 0; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>Success! 🎉</h1>
                    <p>Look at your TV.<br>The playlist is now loading.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        out.print("HTTP/1.1 200 OK\r\n")
        out.print("Content-Type: text/html; charset=UTF-8\r\n")
        out.print("Connection: close\r\n")
        out.print("\r\n")
        out.print(html)
        out.flush()
    }
}