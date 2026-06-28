package com.example.qrcodegeneratorandroidapp

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

sealed class QRTheme {
    object Black : QRTheme()
    data class Gradient(val startColor: Int, val endColor: Int) : QRTheme()
}

object QRCodeGenerator {
    fun generate(text: String, width: Int, height: Int, theme: QRTheme): Bitmap? {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = 1 // compact margins

        try {
            val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val isDark = bitMatrix[x, y]
                    if (isDark) {
                        val color = when (theme) {
                            is QRTheme.Black -> Color.BLACK
                            is QRTheme.Gradient -> {
                                val ratio = (x.toFloat() + y.toFloat()) / (width + height).toFloat()
                                interpolateColor(theme.startColor, theme.endColor, ratio)
                            }
                        }
                        bitmap.setPixel(x, y, color)
                    } else {
                        bitmap.setPixel(x, y, Color.WHITE)
                    }
                }
            }
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
        }
        return null
    }

    private fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        val r = (r1 + (r2 - r1) * ratio).toInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * ratio).toInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * ratio).toInt().coerceIn(0, 255)

        return Color.rgb(r, g, b)
    }
}
