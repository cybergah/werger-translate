package dev.wergir.translate.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Draws translated text back onto an image in place (Google-Lens style): each
 * recognized line's box is covered with the sampled background colour, then the
 * translation is drawn, scaled to fit the box.
 */
object ImageOverlay {

    data class Item(val box: Rect, val text: String)

    fun render(src: Bitmap, items: List<Item>, rtl: Boolean): Bitmap {
        val bmp = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }

        for (item in items) {
            val box = item.box
            if (box.width() <= 0 || box.height() <= 0 || item.text.isBlank()) continue

            val bg = sampleBackground(bmp, box)
            fill.color = bg
            val pad = (box.height() * 0.12f).coerceAtLeast(1f)
            canvas.drawRect(
                box.left - pad, box.top - pad, box.right + pad, box.bottom + pad, fill,
            )

            val lum = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)
            text.color = if (lum > 140) Color.rgb(17, 17, 17) else Color.WHITE

            var size = (box.height() * 0.82f).coerceAtLeast(9f)
            text.textSize = size
            while (size > 9f && text.measureText(item.text) > box.width()) {
                size -= 1f
                text.textSize = size
            }

            text.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val fm = text.fontMetrics
            val baseline = box.top + box.height() / 2f - (fm.ascent + fm.descent) / 2f
            val x = if (rtl) box.right.toFloat() else box.left.toFloat()
            canvas.drawText(item.text, x, baseline, text)
        }
        return bmp
    }

    /** Average a 1px strip just outside the box (above, else below) as the bg. */
    private fun sampleBackground(bmp: Bitmap, box: Rect): Int {
        val gap = (box.height() * 0.3f).toInt().coerceAtLeast(2)
        val y = when {
            box.top - gap >= 0 -> box.top - gap
            box.bottom + gap < bmp.height -> box.bottom + gap
            else -> box.top.coerceIn(0, bmp.height - 1)
        }.coerceIn(0, bmp.height - 1)

        val x0 = box.left.coerceIn(0, bmp.width - 1)
        val x1 = box.right.coerceIn(0, bmp.width)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        var x = x0
        while (x < x1) {
            val p = bmp.getPixel(x, y)
            r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
            x++
        }
        return if (n > 0) Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        else Color.WHITE
    }
}
