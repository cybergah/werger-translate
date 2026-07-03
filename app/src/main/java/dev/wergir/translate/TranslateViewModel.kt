package dev.wergir.translate

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wergir.translate.engine.OnlineTranslator
import dev.wergir.translate.nllb.Language
import dev.wergir.translate.nllb.Languages
import dev.wergir.translate.nllb.ModelManager
import dev.wergir.translate.nllb.NllbTokenizer
import dev.wergir.translate.nllb.Translator
import dev.wergir.translate.ocr.OcrManager
import dev.wergir.translate.tts.TtsManager
import dev.wergir.translate.util.Connectivity
import dev.wergir.translate.util.ImageOverlay
import dev.wergir.translate.util.Images
import dev.wergir.translate.util.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Where the offline model is in its lifecycle. */
enum class ModelState { ABSENT, DOWNLOADING, IMPORTING, LOADING, READY, ERROR }

/** Which engine actually produced the last translation. */
enum class UsedEngine { NONE, ONLINE, OFFLINE }

class TranslateViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<WergirApp>()
    private val modelManager: ModelManager = (app as WergirApp).modelManager
    val prefs: Prefs = (app as WergirApp).prefs

    private val tts = TtsManager(app)
    private val ocr = OcrManager()
    private var translator: Translator? = null
    private var translateJob: Job? = null

    val languages: List<Language> = Languages.all

    // ---- language selection ----
    var sourceLang by mutableStateOf(Languages.default(prefs.lastSource)); private set
    var targetLang by mutableStateOf(Languages.default(prefs.lastTarget)); private set

    // ---- main text translation ----
    var inputText by mutableStateOf("")
    var outputText by mutableStateOf(""); private set
    var isTranslating by mutableStateOf(false); private set
    var usedEngine by mutableStateOf(UsedEngine.NONE); private set
    var statusMessage by mutableStateOf<String?>(null); private set

    // ---- model / offline engine ----
    var modelState by mutableStateOf(if (modelManager.isReady()) ModelState.READY else ModelState.ABSENT)
        private set
    var downloadProgress by mutableStateOf(0f); private set
    var downloadedMb by mutableStateOf(0L); private set
    var totalMb by mutableStateOf(modelManager.totalApproxBytes / (1024 * 1024)); private set
    var modelError by mutableStateOf<String?>(null); private set
    val modelsPath: String get() = modelManager.modelsPath

    // ---- settings ----
    var mode by mutableStateOf(prefs.mode); private set
    var libreUrl by mutableStateOf(prefs.libreUrl); private set
    var libreApiKey by mutableStateOf(prefs.libreApiKey); private set

    // ---- document translation (same-format output file) ----
    var docName by mutableStateOf<String?>(null); private set
    var docBusy by mutableStateOf(false); private set
    var docProgress by mutableStateOf(0f); private set
    var docStatus by mutableStateOf<String?>(null); private set
    var docMessage by mutableStateOf<String?>(null); private set
    var docResultReady by mutableStateOf(false); private set
    var docPreview by mutableStateOf<Bitmap?>(null); private set        // first PDF page (fullscreen legacy)
    var docPreviewPages by mutableStateOf<List<Bitmap>>(emptyList()); private set // all translated PDF pages
    var docPreviewText by mutableStateOf<String?>(null); private set    // TXT content
    private var docResultFile: File? = null
    private var docJob: Job? = null

    // ---- photo translation (in-place overlay) ----
    var photoUri by mutableStateOf<Uri?>(null); private set
    var photoResult by mutableStateOf<Bitmap?>(null); private set
    var photoOriginal by mutableStateOf<Bitmap?>(null); private set
    var showOriginalPhoto by mutableStateOf(false); private set
    var photoBusy by mutableStateOf(false); private set
    var photoStatus by mutableStateOf<String?>(null); private set
    var photoMessage by mutableStateOf<String?>(null); private set
    private var photoJob: Job? = null

    // ===================== language =====================
    fun setSource(l: Language) { sourceLang = l; prefs.lastSource = l.token }
    fun setTarget(l: Language) { targetLang = l; prefs.lastTarget = l.token }
    fun swapLanguages() {
        val s = sourceLang; sourceLang = targetLang; targetLang = s
        prefs.lastSource = sourceLang.token; prefs.lastTarget = targetLang.token
        if (outputText.isNotBlank()) { inputText = outputText; outputText = "" }
    }

    // ===================== settings =====================
    fun applyMode(m: String) { mode = m; prefs.mode = m }
    fun applyLibreUrl(u: String) { libreUrl = u; prefs.libreUrl = u }
    fun applyLibreApiKey(k: String) { libreApiKey = k; prefs.libreApiKey = k }

    // ===================== model =====================
    fun downloadModel() {
        if (modelState == ModelState.DOWNLOADING || modelState == ModelState.LOADING) return
        modelState = ModelState.DOWNLOADING
        modelError = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    modelManager.download { done, total ->
                        downloadProgress = if (total > 0) done.toFloat() / total else 0f
                        downloadedMb = done / (1024 * 1024)
                        totalMb = total / (1024 * 1024)
                    }
                }
                loadEngine()
            } catch (e: Exception) {
                modelError = e.message ?: "İndirme hatası"
                modelState = ModelState.ERROR
            }
        }
    }

    fun importModelFromTree(treeUri: Uri) {
        if (modelState == ModelState.IMPORTING) return
        modelState = ModelState.IMPORTING
        modelError = null
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    modelManager.importFromTree(treeUri) { done, total ->
                        downloadProgress = if (total > 0) done.toFloat() / total else 0f
                        downloadedMb = done / (1024 * 1024)
                        totalMb = total / (1024 * 1024)
                    }
                }
                if (ok) loadEngine()
                else { modelError = "Seçilen klasörde model dosyaları bulunamadı"; modelState = ModelState.ABSENT }
            } catch (e: Exception) {
                modelError = e.message ?: "İçe aktarma hatası"
                modelState = ModelState.ERROR
            }
        }
    }

    /** Counts model files present in a SAF tree (for the folder picker hint). */
    fun detectInTree(treeUri: Uri): Int = runCatching { modelManager.detectInTree(treeUri) }.getOrDefault(0)

    private suspend fun loadEngine() {
        modelState = ModelState.LOADING
        try {
            translator = withContext(Dispatchers.Default) {
                val tok = NllbTokenizer.fromFile(modelManager.tokenizerFile)
                Translator.create(modelManager.encoderFile, modelManager.decoderFile, tok)
            }
            modelState = ModelState.READY
        } catch (e: Exception) {
            modelError = e.message ?: "Model yükleme hatası"
            modelState = ModelState.ERROR
        }
    }

    private suspend fun ensureEngine(): Translator? {
        translator?.let { return it }
        if (modelManager.isReady()) { loadEngine(); return translator }
        return null
    }

    val offlineReady: Boolean get() = modelManager.isReady()

    // ===================== translation routing =====================
    private fun involvesKurdish() =
        sourceLang.token in KURDISH || targetLang.token in KURDISH

    fun translate() {
        val text = inputText.trim()
        if (text.isEmpty()) { outputText = ""; return }
        translateJob?.cancel()
        isTranslating = true
        outputText = ""
        statusMessage = null
        usedEngine = UsedEngine.NONE
        translateJob = viewModelScope.launch {
            try {
                val (result, engine) = route(text, sourceLang.token, targetLang.token)
                outputText = result
                usedEngine = engine
            } catch (e: Exception) {
                outputText = ""
                statusMessage = e.message ?: "Çeviri hatası"
            } finally {
                isTranslating = false
            }
        }
    }

    /** Core hybrid routing. Returns (translatedText, engineUsed). */
    private suspend fun route(text: String, src: String, tgt: String): Pair<String, UsedEngine> {
        val online = Connectivity.isOnline(ctx)
        when (mode) {
            "offline" -> {
                val t = ensureEngine() ?: throw RuntimeException("Çevrimdışı model indirilmemiş. Ayarlar → Modeli indir.")
                return withContext(Dispatchers.Default) { t.translate(text, src, tgt) } to UsedEngine.OFFLINE
            }
            "online" -> {
                if (!online) throw RuntimeException("İnternet yok.")
                return withContext(Dispatchers.IO) {
                    OnlineTranslator(libreUrl, libreApiKey).translate(text, src, tgt)
                } to UsedEngine.ONLINE
            }
            else -> { // auto
                // Kurdish quality is best offline; prefer offline model when present.
                if (involvesKurdish() && offlineReady) {
                    val t = ensureEngine()
                    if (t != null) return withContext(Dispatchers.Default) { t.translate(text, src, tgt) } to UsedEngine.OFFLINE
                }
                if (online) {
                    try {
                        return withContext(Dispatchers.IO) {
                            OnlineTranslator(libreUrl, libreApiKey).translate(text, src, tgt)
                        } to UsedEngine.ONLINE
                    } catch (e: Exception) {
                        val t = ensureEngine()
                        if (t != null) return withContext(Dispatchers.Default) { t.translate(text, src, tgt) } to UsedEngine.OFFLINE
                        throw e
                    }
                }
                val t = ensureEngine() ?: throw RuntimeException("İnternet yok ve çevrimdışı model indirilmemiş.")
                return withContext(Dispatchers.Default) { t.translate(text, src, tgt) } to UsedEngine.OFFLINE
            }
        }
    }

    /**
     * Translate many lines fast: offline uses one batched call; online runs
     * several requests concurrently. Returns a same-length aligned list.
     */
    private suspend fun translateLines(texts: List<String>): List<String> {
        val online = Connectivity.isOnline(ctx)
        val useOffline = (mode == "offline") || (mode == "auto" && (involvesKurdish() || !online) && offlineReady)
        if (useOffline) {
            val t = ensureEngine()
            if (t != null) {
                return withContext(Dispatchers.Default) { t.translateBatch(texts, sourceLang.token, targetLang.token) }
            }
            if (mode == "offline") throw RuntimeException("Çevrimdışı model yok. Ayarlardan indirin.")
        }
        return translateLinesOnline(texts)
    }

    /** Online translation of many lines with bounded concurrency + dedupe. */
    private suspend fun translateLinesOnline(texts: List<String>): List<String> = coroutineScope {
        val online = OnlineTranslator(libreUrl, libreApiKey)
        val uniq = LinkedHashMap<String, Int>()
        for (raw in texts) {
            val t = raw.trim()
            if (t.isNotEmpty() && t.any { it.isLetter() }) uniq.getOrPut(t) { uniq.size }
        }
        val uniqList = uniq.keys.toList()
        val results = arrayOfNulls<String>(uniqList.size)
        val sem = Semaphore(6)
        uniqList.mapIndexed { idx, s ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    results[idx] = try {
                        online.translate(s, sourceLang.token, targetLang.token)
                    } catch (e: Exception) {
                        s
                    }
                }
            }
        }.awaitAll()
        texts.map { raw ->
            val t = raw.trim()
            if (t.isEmpty() || t.none { it.isLetter() }) raw else results[uniq[t]!!] ?: raw
        }
    }

    // ===================== TTS =====================
    fun speakSource() { if (!tts.speak(inputText, sourceLang.token)) statusMessage = "Bu dil için TTS yok" }
    fun speakTarget() { if (!tts.speak(outputText, targetLang.token)) statusMessage = "Bu dil için TTS yok" }
    fun clearStatus() { statusMessage = null }

    // ===================== document =====================
    fun clearDocMessage() { docMessage = null }

    /**
     * Translate a .txt or .pdf and produce a **same-format** file:
     *   * .txt → translated .txt (line by line)
     *   * .pdf → each page rendered, OCR'd, translated, overlaid in place, and
     *            written back to a new .pdf (layout preserved).
     * Documents larger than [MAX_DOC_BYTES] are rejected.
     */
    fun translateDocument(uri: Uri, mime: String?, name: String?) {
        docName = name ?: "belge"
        docStatus = null
        docMessage = null
        docResultReady = false
        docResultFile = null
        docPreview = null
        docPreviewPages = emptyList()
        docPreviewText = null

        val size = querySize(uri)
        if (size != null && size > MAX_DOC_BYTES) {
            docStatus = "Belge 2 MB'den büyük — kabul edilmiyor."
            return
        }

        val isPdf = (mime?.contains("pdf") == true) || (name?.endsWith(".pdf", true) == true)
        docBusy = true
        docProgress = 0f
        docJob?.cancel()
        docJob = viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    if (isPdf) translatePdfInPlace(uri) else translateTxtFile(uri)
                }
                docResultFile = file
                docResultReady = true
            } catch (c: CancellationException) {
                // user cancelled — leave silently
            } catch (e: Exception) {
                docStatus = "⚠️ ${e.message}"
            } finally {
                docBusy = false
            }
        }
    }

    /** Cancel an in-progress document translation. */
    fun cancelDocument() {
        docJob?.cancel()
        docBusy = false
        docStatus = "İptal edildi"
    }

    private fun querySize(uri: Uri): Long? {
        var size: Long? = null
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) size = c.getLong(idx)
            }
        }
        if (size == null) {
            size = runCatching {
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { s -> s >= 0 } }
            }.getOrNull()
        }
        return size
    }

    /** Output file name: "<stem>.translated.<ext>" in the app cache. */
    private fun outFile(ext: String): File {
        val base = (docName ?: "belge").substringBeforeLast('.')
        val dir = File(ctx.cacheDir, "docs").apply { mkdirs() }
        return File(dir, "$base.translated.$ext")
    }

    private suspend fun translateTxtFile(uri: Uri): File {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { readCapped(it) }
            ?: throw RuntimeException("Belge açılamadı")
        val text = String(bytes, Charsets.UTF_8)
        val lines = text.split("\n")
        val translated = translateLines(lines) // batched offline / concurrent online
        docProgress = 1f
        val body = translated.joinToString("\n").trimEnd('\n')
        docPreviewText = body.take(4000) // in-app preview (capped)
        val file = outFile("txt")
        file.writeText(body)
        return file
    }

    /** Read an input stream but refuse anything above the 2 MB cap. */
    private fun readCapped(input: java.io.InputStream): ByteArray {
        val limit = MAX_DOC_BYTES.toInt()
        val buf = input.readBytes()
        if (buf.size > limit) throw RuntimeException("Belge 2 MB'den büyük — kabul edilmiyor.")
        return buf
    }

    private suspend fun translatePdfInPlace(uri: Uri): File {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
            ?: throw RuntimeException("PDF açılamadı")
        val rtl = isRtl(targetLang.token)
        val outDoc = android.graphics.pdf.PdfDocument()
        val previewPages = mutableListOf<Bitmap>()
        pfd.use {
            android.graphics.pdf.PdfRenderer(it).use { renderer ->
                val n = renderer.pageCount
                if (n == 0) throw RuntimeException("PDF boş")
                for (i in 0 until n) {
                    currentCoroutineContext().ensureActive() // allow cancel between pages
                    renderer.openPage(i).use { page ->
                        // Render at 2× for crisper OCR, then translate + overlay.
                        val scale = 2f
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                        val m = android.graphics.Matrix().apply { setScale(scale, scale) }
                        page.render(bmp, null, m, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val lines = ocr.recognizeLayout(bmp)
                        val translated = translateLines(lines.map { it.text })
                        val items = lines.mapIndexed { idx, line ->
                            ImageOverlay.Item(line.box, translated.getOrNull(idx)?.ifBlank { line.text } ?: line.text)
                        }
                        val overlaid = ImageOverlay.render(bmp, items, rtl)

                        val info = android.graphics.pdf.PdfDocument.PageInfo
                            .Builder(page.width, page.height, i + 1).create()
                        val outPage = outDoc.startPage(info)
                        outPage.canvas.drawBitmap(
                            overlaid, null, android.graphics.Rect(0, 0, page.width, page.height), null,
                        )
                        outDoc.finishPage(outPage)
                        // Keep every translated page for the in-app preview so the
                        // user can scroll through the whole document, not just page 1.
                        val previewBmp = overlaid.copy(Bitmap.Config.ARGB_8888, false)
                        previewPages.add(previewBmp)
                        if (i == 0) docPreview = previewBmp
                        if (overlaid != bmp) overlaid.recycle()
                        bmp.recycle()
                    }
                    docProgress = (i + 1f) / n
                    // Publish pages incrementally so preview fills in as it goes.
                    docPreviewPages = previewPages.toList()
                }
            }
        }
        val file = outFile("pdf")
        FileOutputStream(file).use { outDoc.writeTo(it) }
        outDoc.close()
        return file
    }

    fun docResultMime(): String =
        if (docResultFile?.name?.endsWith(".pdf") == true) "application/pdf" else "text/plain"

    /** Content URI for sharing the translated document. */
    fun shareDocUri(): Uri? {
        val f = docResultFile ?: return null
        return try {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        } catch (e: Exception) {
            docStatus = "⚠️ ${e.message}"
            null
        }
    }

    /** Save the translated document to the public Downloads/Wergêr folder. */
    fun saveDocResult() {
        val f = docResultFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mime = docResultMime()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/Wergêr")
                    }
                    val out = ctx.contentResolver
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw RuntimeException("MediaStore")
                    ctx.contentResolver.openOutputStream(out)?.use { os ->
                        f.inputStream().use { it.copyTo(os) }
                    } ?: throw RuntimeException("openOutputStream")
                } else {
                    val downloads = android.os.Environment
                        .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val dst = File(downloads, f.name)
                    f.inputStream().use { input -> FileOutputStream(dst).use { input.copyTo(it) } }
                }
                docMessage = "İndirilenler/Wergêr klasörüne kaydedildi"
            } catch (e: Exception) {
                docStatus = "⚠️ ${e.message}"
            }
        }
    }

    // ===================== photo =====================
    fun setPhoto(uri: Uri) {
        photoUri = uri; photoResult = null; photoOriginal = null
        showOriginalPhoto = false; photoStatus = null; photoMessage = null
    }

    fun clearPhotoMessage() { photoMessage = null }

    /** Toggle between the original photo and the translated overlay. */
    fun togglePhotoView() { showOriginalPhoto = !showOriginalPhoto }

    /**
     * OCR the photo, translate all lines at once (batched offline / concurrent
     * online), and paint the translations back onto a copy of the image.
     */
    fun runPhoto() {
        val uri = photoUri ?: return
        if (!ocr.isScriptSupported(sourceLang.token)) {
            photoStatus = "Fotoğraf çevirisi şu an yalnızca Latin alfabeli kaynak dilleri destekliyor."
            return
        }
        photoBusy = true
        photoStatus = null
        photoMessage = null
        photoResult = null
        photoOriginal = null
        showOriginalPhoto = false
        photoJob?.cancel()
        photoJob = viewModelScope.launch {
            try {
                val src = withContext(Dispatchers.IO) { Images.decodeUpright(ctx, uri) }
                val lines = withContext(Dispatchers.Default) { ocr.recognizeLayout(src) }
                if (lines.isEmpty()) { photoStatus = "Görüntüde metin bulunamadı"; photoBusy = false; return@launch }

                val rtl = isRtl(targetLang.token)
                // Keep an untouched copy so the user can toggle original ↔ translated.
                val original = withContext(Dispatchers.Default) { src.copy(Bitmap.Config.ARGB_8888, false) }
                val translated = translateLines(lines.map { it.text })
                currentCoroutineContext().ensureActive() // discard if cancelled
                val items = lines.mapIndexed { idx, line ->
                    ImageOverlay.Item(line.box, translated.getOrNull(idx)?.ifBlank { line.text } ?: line.text)
                }
                photoOriginal = original
                photoResult = withContext(Dispatchers.Default) { ImageOverlay.render(src, items, rtl) }
            } catch (c: CancellationException) {
                // user cancelled — leave silently
            } catch (e: Exception) {
                photoStatus = "⚠️ ${e.message}"
            } finally {
                photoBusy = false
            }
        }
    }

    /** Cancel an in-progress photo translation. */
    fun cancelPhoto() {
        photoJob?.cancel()
        photoBusy = false
        photoStatus = "İptal edildi"
    }

    /** Save the translated overlay image to the gallery (Pictures/Wergêr). */
    fun savePhotoResult() {
        val bmp = photoResult ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = "Werger_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wergêr")
                    }
                }
                val resolver = ctx.contentResolver
                val out = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw RuntimeException("MediaStore")
                resolver.openOutputStream(out)?.use { os ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                } ?: throw RuntimeException("openOutputStream")
                photoMessage = "Galeriye kaydedildi"
            } catch (e: Exception) {
                photoStatus = "⚠️ ${e.message}"
            }
        }
    }

    /** Write the overlay image to a cache file and return a shareable content URI. */
    fun sharePhotoUri(): Uri? {
        val bmp = photoResult ?: return null
        return try {
            val dir = File(ctx.cacheDir, "shares").apply { mkdirs() }
            val file = File(dir, "werger_share.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (e: Exception) {
            photoStatus = "⚠️ ${e.message}"
            null
        }
    }

    private fun isRtl(code: String) = Regex("_(Arab|Hebr|Thaa|Syrc|Nkoo|Adlm)$").containsMatchIn(code)

    override fun onCleared() {
        translator?.close()
        tts.shutdown()
        super.onCleared()
    }

    companion object {
        private val KURDISH = setOf("kmr_Latn", "ckb_Arab")
        private const val MAX_DOC_BYTES = 2L * 1024 * 1024 // 2 MB
    }
}
