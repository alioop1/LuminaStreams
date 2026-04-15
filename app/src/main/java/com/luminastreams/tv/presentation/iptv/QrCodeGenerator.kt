package com.luminastreams.tv.presentation.iptv

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeGenerator {

    fun generate(text: String, size: Int = 400): Bitmap {
        if (text.isBlank()) {
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }

        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            // רמת תיקון שגיאות בינונית (M) מאפשרת סריקה קלה גם מצגי טלוויזיה מרחוק
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            hints[EncodeHintType.MARGIN] = 2 // שוליים מינימליים

            val bitMatrix = MultiFormatWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            // המרת המטריצה לפיקסלים של תמונה
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

        } catch (e: Exception) {
            e.printStackTrace()
            // במקרה קיצון של שגיאה, נחזיר תמונה ריקה
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }
    }
}