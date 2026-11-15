package com.royalemind.ai.vision

import android.graphics.Bitmap
import android.graphics.Color

/** Bitmap letterbox (mirror of the Python letterbox) for ONNX input. */
object ImageUtils {
    data class Letterboxed(val bitmap: Bitmap, val scale: Float,
                           val padX: Int, val padY: Int)

    fun letterbox(src: Bitmap, size: Int): Letterboxed {
        val scale = minOf(size.toFloat() / src.width, size.toFloat() / src.height)
        val nw = (src.width * scale).toInt()
        val nh = (src.height * scale).toInt()
        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val canvas = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        canvas.eraseColor(Color.rgb(114, 114, 114))
        val px = (size - nw) / 2
        val py = (size - nh) / 2
        android.graphics.Canvas(canvas).drawBitmap(resized, px.toFloat(), py.toFloat(), null)
        if (resized != src) resized.recycle()
        return Letterboxed(canvas, scale, px, py)
    }

    /** Convert a captured RGBA image buffer into a bitmap (reuses buffer). */
    fun rgbaToBitmap(buffer: java.nio.ByteBuffer, width: Int, height: Int,
                      rowStride: Int, dst: Bitmap? = null): Bitmap {
        val pixels = IntArray(width * height)
        var idx = 0
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            for (x in 0 until width) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                pixels[idx++] = Color.argb(a, r, g, b)
            }
        }
        val out = dst ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }
}
