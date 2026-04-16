package com.luminastreams.tv.presentation.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

data class IncomingPlaylist(val name: String, val url: String, val epgUrl: String)

object LocalWebServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    // תיקון #7 — replay=1 מבטיח שהאירוע לא יאבד אם ה-ViewModel עוד לא מאזין
    private val _playlistFlow = MutableSharedFlow<IncomingPlaylist>(replay = 1)
    val playlistFlow = _playlistFlow.asSharedFlow()

    suspend fun start(port: Int = 8080) = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        try {
            serverSocket = ServerSocket(port).apply { reuseAddress = true }
            isRunning = true
            while (isRunning) {
                val client = serverSocket?.accept() ?: break
                launch(Dispatchers.IO) { handleClient(client) }
            }
        } catch (_: Exception) {
            isRunning = false
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val out = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return@withContext
            if (requestLine.contains("favicon.ico")) {
                out.print("HTTP/1.1 404 Not Found\r\n\r\n")
                out.flush()
                return@withContext
            }

            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.lowercase().startsWith("content-length:")) {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (requestLine.startsWith("GET")) {
                serveHtml(out)
            } else if (requestLine.startsWith("POST")) {
                val bodyChars = CharArray(contentLength)
                reader.read(bodyChars, 0, contentLength)
                val params = String(bodyChars).split("&").associate {
                    val parts = it.split("=")
                    URLDecoder.decode(parts[0], "UTF-8") to
                            URLDecoder.decode(parts.getOrNull(1) ?: "", "UTF-8")
                }

                val url = params["url"] ?: ""
                // תיקון #6 — בדיקה שה-URL לא זהה לאחרון שנשלח (מניעת דריסה כפולה)
                val lastEmitted = _playlistFlow.replayCache.lastOrNull()
                if (url.isNotBlank() && url != lastEmitted?.url) {
                    _playlistFlow.emit(
                        IncomingPlaylist(
                            name = params["name"]?.takeIf { it.isNotBlank() } ?: "Lumina IPTV",
                            url = url,
                            epgUrl = params["epg"] ?: ""
                        )
                    )
                }
                serveSuccess(out)
            }
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveHtml(out: PrintWriter) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { background: #000000; color: #fff; font-family: sans-serif; padding: 20px; }
                    input, button { width: 100%; padding: 15px; margin-top: 10px; border-radius: 8px;
                        background: #1a1a1a; color: #fff; border: 1px solid #333; box-sizing: border-box; }
                    button { background: #0a84ff; font-weight: bold; cursor: pointer; }
                    label { display: block; margin-top: 14px; font-size: 13px; color: #aaa; }
                </style>
            </head>
            <body>
                <h2>Lumina Setup</h2>
                <form method="POST">
                    <label>שם פלייליסט</label>
                    <input name="name" placeholder="Lumina IPTV">
                    <label>M3U URL *</label>
                    <input name="url" placeholder="http://..." required>
                    <label>EPG URL (אופציונלי)</label>
                    <input name="epg" placeholder="http://.../epg.xml">
                    <button type="submit">שלח לטלוויזיה</button>
                </form>
            </body>
            </html>
        """.trimIndent()
        out.print("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n$html")
        out.flush()
    }

    private fun serveSuccess(out: PrintWriter) {
        out.print(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" +
                    "<body style='background:#000;color:#30d158;text-align:center;margin-top:50px;font-family:sans-serif'>" +
                    "<h1>✓ Success!</h1><p>תסתכל על הטלוויזיה.</p></body>"
        )
        out.flush()
    }
}