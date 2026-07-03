package dev.wergir.translate.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.IOException

/** Image decoding helpers for the photo-translation feature. */
object Images {

    /**
     * Decode [uri] into a mutable ARGB_8888 bitmap that is rotated upright per
     * its EXIF orientation, so ML Kit's bounding boxes line up with the pixels
     * we draw on. Works on API 26+ using [android.media.ExifInterface] (which
     * accepts an InputStream since API 24).
     */
    fun decodeUpright(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver

        val opts = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Wêne nehat xwendin")

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ins ->
                ExifInterface(ins).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }
}
