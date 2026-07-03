package dev.wergir.translate.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device OCR using ML Kit Text Recognition (bundled Latin-script model, works
 * offline). ML Kit's text recognizer covers Latin-script languages (Kurmancî,
 * Türkçe, English, German, French, …). Arabic-script OCR (Soranî/Arabic/Farsi)
 * is not provided by ML Kit; [isScriptSupported] lets the UI inform the user.
 */
class OcrManager {

    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun isScriptSupported(srcFlores: String): Boolean = !srcFlores.endsWith("_Arab")

    /** One recognized line with its pixel bounding box in the bitmap's space. */
    data class OcrLine(val text: String, val box: Rect)

    /** Blocking; call from a background dispatcher. Returns recognized text. */
    fun recognize(context: Context, imageUri: Uri): String {
        val image = InputImage.fromFilePath(context, imageUri)
        val result = Tasks.await(latin.process(image))
        return result.text
    }

    /**
     * Blocking; call from a background dispatcher. Recognizes text with per-line
     * bounding boxes for the in-place overlay renderer. The [bitmap] is taken as
     * an already-upright ARGB image so its coordinate space matches the boxes.
     */
    fun recognizeLayout(bitmap: Bitmap): List<OcrLine> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(latin.process(image))
        val lines = ArrayList<OcrLine>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                if (line.text.isBlank() || box.width() <= 0 || box.height() <= 0) continue
                lines.add(OcrLine(line.text, box))
            }
        }
        return lines
    }
}
