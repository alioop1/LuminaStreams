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

    private val _playlistFlow = MutableSharedFlow<IncomingPlaylist>()
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
        } catch (e: Exception) {
            isRunning = false
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val out = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return@withContext
            if (requestLine.contains("favicon.ico")) {
                out.print("HTTP/1.1 404 Not Found\r\n\r\n")
                out.flush() // <-- דוחף את הנתונים החוצה
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
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrNull(1) ?: "", "UTF-8")
                }

                val url = params["url"] ?: ""
                if (url.isNotBlank()) {
                    _playlistFlow.emit(
                        IncomingPlaylist(
                            name = params["name"]?.takeIf { it.isNotBlank() } ?: "Lumina IPTV",
                            url = url,
                            epgUrl = params["epg"] ?: ""
                        )
                    )
                    serveSuccess(out)
                }
            }
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun serveHtml(out: PrintWriter) {
        val html = """
            <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>body{background:#000000;color:#fff;font-family:sans-serif;padding:20px} input,button{width:100%;padding:15px;margin-top:10px;border-radius:8px;background:#1a1a1a;color:#fff;border:1px solid #333} button{background:#0a84ff;font-weight:bold}</style>
            </head><body><h2>Lumina Setup</h2><form method="POST">
            <input name="name" placeholder="Playlist Name"><input name="url" placeholder="M3U URL" required>
            <input name="epg" placeholder="EPG URL (Optional)"><button type="submit">Send to TV</button></form></body></html>
        """.trimIndent()
        out.print("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n$html")
        out.flush() // <-- דוחף את הנתונים החוצה
    }

    private fun serveSuccess(out: PrintWriter) {
        out.print("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<body style='background:#000000;color:#30d158;text-align:center;margin-top:50px;font-family:sans-serif'><h1>Success!</h1><p>Look at your TV.</p></body>")
        out.flush() // <-- דוחף את הנתונים החוצה
    }
}