package dev.wergir.translate.docs

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Extracts plain text from supported document types (.txt, .pdf) for document
 * translation. Everything runs on-device/offline.
 */
object DocumentExtractor {

    private var initialized = false

    private fun ensureInit(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    /** Reads text from a content Uri. Detects PDF by [mimeType] or [displayName]. */
    fun extract(context: Context, uri: Uri, mimeType: String?, displayName: String?): String {
        val isPdf = (mimeType?.contains("pdf") == true) ||
            (displayName?.endsWith(".pdf", ignoreCase = true) == true)
        return if (isPdf) extractPdf(context, uri) else extractText(context, uri)
    }

    private fun extractText(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri).use { input ->
            return input?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
        }
    }

    private fun extractPdf(context: Context, uri: Uri): String {
        ensureInit(context)
        context.contentResolver.openInputStream(uri).use { input ->
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                return stripper.getText(doc)
            }
        }
    }
}
