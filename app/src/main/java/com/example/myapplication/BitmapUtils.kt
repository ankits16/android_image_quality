package com.example.myapplication
import android.graphics.Bitmap

class BitmapUtils {
    companion object {
        fun getChannelOrientation(bitmap: Bitmap): String {

            return  bitmap.config.name
//            val pixel = bitmap.getPixel(0, 0)
//            val alpha = pixel shr 24 and 0xFF
//            val red = pixel shr 16 and 0xFF
//            val green = pixel shr 8 and 0xFF
//            val blue = pixel and 0xFF
//
//            return when {
//                alpha == 0xFF && red == 0 && green == 0 && blue == 0 -> "ARGB (Alpha, Red, Green, Blue)"
//                alpha == 0 && red == 0xFF && green == 0 && blue == 0 -> "RGBA (Red, Green, Blue, Alpha)"
//                alpha == 0 && red == 0 && green == 0xFF && blue == 0 -> "BGRA (Blue, Green, Red, Alpha)"
//                alpha == 0 && red == 0 && green == 0 && blue == 0xFF -> "ABGR (Alpha, Blue, Green, Red)"
//                else -> "Unknown channel orientation"
//            }
        }
    }
}