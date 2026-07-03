package dev.wergir.translate.nllb

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the on-device NLLB-200 model assets: the int8-quantized ONNX encoder
 * and (merged, KV-cached) decoder plus the tokenizer. Files live in a folder named
 * "Wergêr" in the app's external files directory. They can be downloaded once from
 * the public Hugging Face mirror, OR imported from a folder the user pre-populated
 * (auto-detected). After that the app translates fully offline.
 */
class ModelManager(context: Context) {

    private val appContext = context.applicationContext

    private val baseUrl =
        "https://huggingface.co/Xenova/nllb-200-distilled-600M/resolve/main"

    /** Visible, path-accessible folder: /Android/data/<pkg>/files/Wergêr */
    val modelsDir: File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "Wergêr")
            .apply { mkdirs() }

    /** Human-readable location for the settings screen. */
    val modelsPath: String get() = modelsDir.absolutePath

    data class Asset(
        val fileName: String,
        val url: String,
        /** Approximate size in bytes, used only for progress before Content-Length is known. */
        val approxBytes: Long,
    )

    val assets: List<Asset> = listOf(
        Asset(
            "encoder.onnx",
            "$baseUrl/onnx/encoder_model_quantized.onnx",
            419_120_483L,
        ),
        Asset(
            "decoder.onnx",
            "$baseUrl/onnx/decoder_model_merged_quantized.onnx",
            475_505_771L,
        ),
        Asset(
            "tokenizer.json",
            "$baseUrl/tokenizer.json",
            17_331_224L,
        ),
    )

    fun fileFor(name: String): File = File(modelsDir, name)

    val encoderFile: File get() = fileFor("encoder.onnx")
    val decoderFile: File get() = fileFor("decoder.onnx")
    val tokenizerFile: File get() = fileFor("tokenizer.json")

    /** Total approximate download size for all assets. */
    val totalApproxBytes: Long get() = assets.sumOf { it.approxBytes }

    /** True if every asset is present and non-trivially sized. */
    fun isReady(): Boolean = assets.all { a ->
        val f = fileFor(a.fileName)
        f.exists() && f.length() > 1_000_000L
    }

    /** Bytes already present on disk across all assets. */
    fun bytesOnDisk(): Long = assets.sumOf { fileFor(it.fileName).let { f -> if (f.exists()) f.length() else 0L } }

    /**
     * Downloads any missing assets, reporting cumulative progress in bytes.
     * Safe to re-run: already-complete files are skipped. Throws on network errors.
     */
    fun download(onProgress: (downloaded: Long, total: Long) -> Unit) {
        // Determine real sizes first so the progress bar is accurate.
        val sizes = LinkedHashMap<String, Long>()
        var total = 0L
        for (a in assets) {
            val size = if (fileFor(a.fileName).let { it.exists() && it.length() > 1_000_000L })
                fileFor(a.fileName).length()
            else
                contentLength(a.url).takeIf { it > 0 } ?: a.approxBytes
            sizes[a.fileName] = size
            total += size
        }

        var done = 0L
        onProgress(done, total)
        for (a in assets) {
            val target = fileFor(a.fileName)
            val expected = sizes[a.fileName] ?: a.approxBytes
            if (target.exists() && target.length() > 1_000_000L) {
                done += target.length()
                onProgress(done, total)
                continue
            }
            val baseDone = done
            downloadFile(a.url, target) { fileBytes ->
                onProgress(baseDone + fileBytes, total)
            }
            done = baseDone + target.length()
            onProgress(done, total)
        }
    }

    /** Candidate source file names accepted when importing from a user folder. */
    private val encoderNames = listOf("encoder.onnx", "encoder_model_quantized.onnx")
    private val decoderNames = listOf("decoder.onnx", "decoder_model_merged_quantized.onnx")
    private val tokenizerNames = listOf("tokenizer.json")

    /** Returns how many of the 3 required assets exist in the given SAF tree. */
    fun detectInTree(treeUri: Uri): Int {
        val tree = DocumentFile.fromTreeUri(appContext, treeUri) ?: return 0
        val names = tree.listFiles().mapNotNull { it.name }.toSet()
        var n = 0
        if (encoderNames.any { it in names }) n++
        if (decoderNames.any { it in names }) n++
        if (tokenizerNames.any { it in names }) n++
        return n
    }

    /**
     * Imports the model files from a user-selected folder (SAF tree) into the
     * managed Wergêr folder, so ONNX Runtime can load them by path. Reports
     * cumulative copied bytes. Returns true if all 3 assets ended up present.
     */
    fun importFromTree(treeUri: Uri, onProgress: (copied: Long, total: Long) -> Unit): Boolean {
        val tree = DocumentFile.fromTreeUri(appContext, treeUri) ?: return false
        val files = tree.listFiles().associateBy { it.name }

        fun pick(cands: List<String>): DocumentFile? = cands.firstNotNullOfOrNull { files[it] }

        val sources = listOfNotNull(
            pick(encoderNames)?.let { it to "encoder.onnx" },
            pick(decoderNames)?.let { it to "decoder.onnx" },
            pick(tokenizerNames)?.let { it to "tokenizer.json" },
        )
        val total = sources.sumOf { it.first.length() }
        var copied = 0L
        onProgress(0, total)
        for ((src, targetName) in sources) {
            val target = File(modelsDir, targetName)
            if (target.exists() && target.length() == src.length()) {
                copied += src.length(); onProgress(copied, total); continue
            }
            val tmp = File(modelsDir, "$targetName.part")
            appContext.contentResolver.openInputStream(src.uri).use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input?.read(buf) ?: -1
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        onProgress(copied, total)
                    }
                }
            }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        }
        return isReady()
    }

    private fun contentLength(urlStr: String): Long {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 20_000
            }
            conn.connect()
            val len = conn.contentLengthLong
            conn.disconnect()
            len
        } catch (e: Exception) {
            -1L
        }
    }

    private fun downloadFile(urlStr: String, target: File, onBytes: (Long) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".part")
        if (tmp.exists()) tmp.delete()
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw RuntimeException("HTTP ${conn.responseCode} for $urlStr")
        }
        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(1 shl 16)
                var written = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    written += n
                    onBytes(written)
                }
                output.flush()
            }
        }
        conn.disconnect()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
