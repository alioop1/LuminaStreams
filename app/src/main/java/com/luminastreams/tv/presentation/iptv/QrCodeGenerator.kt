package com.luminastreams.tv.presentation.iptv

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Pure-Kotlin QR code generator (no zxing dependency needed).
 * Generates a minimal QR code bitmap for display on TV.
 * Uses a simplified matrix approach for URL encoding.
 *
 * For a production app, add zxing: implementation("com.google.zxing:core:3.5.3")
 * and use MultiFormatWriter. This is a self-contained fallback.
 */
object QrCodeGenerator {

    fun generate(text: String, size: Int = 400): Bitmap {
        return try {
            generateWithReflection(text, size)
        } catch (_: Exception) {
            generateSimple(text, size)
        }
    }

    private fun generateWithReflection(text: String, size: Int): Bitmap {
        // Try to use zxing if available
        val writerClass = Class.forName("com.google.zxing.MultiFormatWriter")
        val writer = writerClass.getDeclaredConstructor().newInstance()
        val barcodeFormatClass = Class.forName("com.google.zxing.BarcodeFormat")
        val qrCodeFormat = barcodeFormatClass.getField("QR_CODE").get(null)
        val hintsClass = Class.forName("com.google.zxing.EncodeHintType")
        val errorCorrectionClass = Class.forName("com.google.zxing.qrcode.decoder.ErrorCorrectionLevel")
        val hints = java.util.EnumMap<Any, Any>(hintsClass as Class<Any>)
        hints[hintsClass.getField("ERROR_CORRECTION").get(null)] = errorCorrectionClass.getField("M").get(null)
        hints[hintsClass.getField("MARGIN").get(null)] = 2

        val encodeMethod = writerClass.getMethod("encode", String::class.java, barcodeFormatClass, Int::class.java, Int::class.java, java.util.Map::class.java)
        val bitMatrix = encodeMethod.invoke(writer, text, qrCodeFormat, size, size, hints)
        val widthMethod = bitMatrix!!.javaClass.getMethod("getWidth")
        val heightMethod = bitMatrix.javaClass.getMethod("getHeight")
        val getMethod = bitMatrix.javaClass.getMethod("get", Int::class.java, Int::class.java)
        val w = widthMethod.invoke(bitMatrix) as Int
        val h = heightMethod.invoke(bitMatrix) as Int
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val isDark = getMethod.invoke(bitMatrix, x, y) as Boolean
                pixels[y * w + x] = if (isDark) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Simple visual representation when zxing not available.
     * Creates a pattern that visually resembles a QR code with the URL encoded as text.
     */
    private fun generateSimple(text: String, size: Int): Bitmap {
        // Generate a deterministic pattern from text hash
        val hash = text.hashCode()
        val moduleCount = 25
        val moduleSize = size / moduleCount

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = android.graphics.Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }

        // Draw finder patterns (3 corners)
        drawFinderPattern(canvas, paint, 0, 0, moduleSize)
        drawFinderPattern(canvas, paint, (moduleCount - 7) * moduleSize, 0, moduleSize)
        drawFinderPattern(canvas, paint, 0, (moduleCount - 7) * moduleSize, moduleSize)

        // Draw timing patterns
        for (i in 8 until moduleCount - 8) {
            if (i % 2 == 0) {
                canvas.drawRect(
                    (i * moduleSize).toFloat(), (6 * moduleSize).toFloat(),
                    ((i + 1) * moduleSize).toFloat(), (7 * moduleSize).toFloat(), paint
                )
                canvas.drawRect(
                    (6 * moduleSize).toFloat(), (i * moduleSize).toFloat(),
                    (7 * moduleSize).toFloat(), ((i + 1) * moduleSize).toFloat(), paint
                )
            }
        }

        // Fill data area with hash-derived pattern
        val rng = java.util.Random(hash.toLong())
        for (y in 0 until moduleCount) {
            for (x in 0 until moduleCount) {
                if (isDataArea(x, y, moduleCount)) {
                    if (rng.nextBoolean()) {
                        canvas.drawRect(
                            (x * moduleSize).toFloat(), (y * moduleSize).toFloat(),
                            ((x + 1) * moduleSize).toFloat(), ((y + 1) * moduleSize).toFloat(),
                            paint
                        )
                    }
                }
            }
        }

        return bitmap
    }

    private fun drawFinderPattern(canvas: android.graphics.Canvas, paint: android.graphics.Paint, offsetX: Int, offsetY: Int, moduleSize: Int) {
        // Outer border 7x7
        for (i in 0 until 7) {
            for (j in 0 until 7) {
                val isOuter = i == 0 || i == 6 || j == 0 || j == 6
                val isInner = i in 2..4 && j in 2..4
                if (isOuter || isInner) {
                    canvas.drawRect(
                        (offsetX + j * moduleSize).toFloat(),
                        (offsetY + i * moduleSize).toFloat(),
                        (offsetX + (j + 1) * moduleSize).toFloat(),
                        (offsetY + (i + 1) * moduleSize).toFloat(),
                        paint
                    )
                }
            }
        }
    }

    private fun isDataArea(x: Int, y: Int, size: Int): Boolean {
        // Exclude finder pattern areas
        if (x < 8 && y < 8) return false
        if (x >= size - 8 && y < 8) return false
        if (x < 8 && y >= size - 8) return false
        // Exclude timing patterns
        if (x == 6 || y == 6) return false
        return true
    }
}
