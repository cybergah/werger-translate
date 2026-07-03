package dev.wergir.translate.nllb

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-device NLLB-200 translation engine built on ONNX Runtime.
 *
 * Pipeline: tokenize -> encoder -> autoregressive greedy decode over the merged
 * KV-cached decoder -> detokenize. Matches the I/O contract of the ONNX graphs
 * exported by Optimum for facebook/nllb-200-distilled-600M (m2m_100):
 *
 *   encoder inputs:  input_ids, attention_mask        -> last_hidden_state
 *   decoder inputs:  input_ids, encoder_attention_mask, encoder_hidden_states,
 *                    past_key_values.{i}.{decoder,encoder}.{key,value}, use_cache_branch
 *   decoder outputs: logits, present.{i}.{decoder,encoder}.{key,value}
 *
 * The decoder is primed with [</s>, <tgt_lang>] (NLLB's decoder_start + forced BOS),
 * then tokens are generated greedily until </s>.
 */
class Translator private constructor(
    private val env: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val tokenizer: NllbTokenizer,
) {
    companion object {
        private const val NUM_LAYERS = 12
        private const val NUM_HEADS = 16
        private const val HEAD_DIM = 64 // d_model(1024) / heads(16)
        private const val EOS = NllbTokenizer.EOS_ID // 2
        private const val PAD = NllbTokenizer.PAD_ID // 1
        private const val DECODER_START = 2 // </s>
        private const val MAX_BATCH = 8 // sequences per batched inference call

        fun create(
            encoderFile: File,
            decoderFile: File,
            tokenizer: NllbTokenizer,
        ): Translator {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // Use more cores for faster inference (helps photo/PDF batches).
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                setIntraOpNumThreads(threads)
            }
            val enc = env.createSession(encoderFile.absolutePath, opts)
            val dec = env.createSession(decoderFile.absolutePath, opts)
            return Translator(env, enc, dec, tokenizer)
        }
    }

    @Volatile
    var maxNewTokens: Int = 200
    var noRepeatNgram: Int = 3

    /**
     * Translates [text] from [srcToken] to [tgtToken] (FLORES-200 language codes).
     * Blocking; call from a background dispatcher. [onToken] is invoked with the
     * partial translation as it is produced (for streaming UI updates).
     */
    @Synchronized
    fun translate(
        text: String,
        srcToken: String,
        tgtToken: String,
        onToken: ((String) -> Unit)? = null,
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val inputIds = tokenizer.encode(trimmed, srcToken)
        val srcLen = inputIds.size
        val tgtLangId = tokenizer.tokenId(tgtToken)

        // ---- Encoder ----
        val encHidden: OnnxTensor
        run {
            val idsLong = LongArray(srcLen) { inputIds[it].toLong() }
            val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(idsLong), longArrayOf(1, srcLen.toLong()))
            val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(srcLen) { 1L }), longArrayOf(1, srcLen.toLong()))
            ids.use { i ->
                mask.use { m ->
                    val res = encoder.run(mapOf("input_ids" to i, "attention_mask" to m))
                    encHidden = copyTensor(res.get("last_hidden_state").get() as OnnxTensor)
                    res.close()
                }
            }
        }

        val encMask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(srcLen) { 1L }), longArrayOf(1, srcLen.toLong()))

        // KV cache: per-layer decoder + encoder key/value, stored as host arrays.
        var pastDecKey = Array(NUM_LAYERS) { FloatArray(0) }
        var pastDecVal = Array(NUM_LAYERS) { FloatArray(0) }
        var pastEncKey = Array(NUM_LAYERS) { FloatArray(0) }
        var pastEncVal = Array(NUM_LAYERS) { FloatArray(0) }
        var pastDecLen = 0

        val generated = ArrayList<Int>(64)
        val maxOut = maxNewTokens.coerceAtMost(20 + srcLen * 3)

        try {
            // ---- Prefill: input_ids = [</s>, <tgt_lang>], use_cache_branch = false ----
            var nextId: Int
            run {
                val prime = longArrayOf(DECODER_START.toLong(), tgtLangId.toLong())
                val res = runDecoder(
                    inputIds = prime,
                    encMask = encMask,
                    encHidden = encHidden,
                    useCache = false,
                    pastDecKey = pastDecKey, pastDecVal = pastDecVal,
                    pastEncKey = pastEncKey, pastEncVal = pastEncVal,
                    pastDecLen = 0, srcLen = srcLen,
                )
                nextId = argmaxRow(res.logits, res.seqLen - 1, res.vocab, generated)
                pastDecKey = res.decKey; pastDecVal = res.decVal
                pastEncKey = res.encKey; pastEncVal = res.encVal
                pastDecLen = res.seqLen
            }

            var step = 0
            while (nextId != EOS && step < maxOut) {
                generated.add(nextId)
                onToken?.invoke(tokenizer.decode(generated.toIntArray()))
                step++

                val res = runDecoder(
                    inputIds = longArrayOf(nextId.toLong()),
                    encMask = encMask,
                    encHidden = encHidden,
                    useCache = true,
                    pastDecKey = pastDecKey, pastDecVal = pastDecVal,
                    pastEncKey = pastEncKey, pastEncVal = pastEncVal,
                    pastDecLen = pastDecLen, srcLen = srcLen,
                )
                nextId = argmaxRow(res.logits, 0, res.vocab, generated)
                pastDecKey = res.decKey; pastDecVal = res.decVal
                // encoder KV stays constant; keep the first values.
                pastDecLen += 1
            }
        } finally {
            encHidden.close()
            encMask.close()
        }

        return tokenizer.decode(generated.toIntArray())
    }

    /**
     * Translate many texts at once via batched inference — far faster than one
     * call per line for the photo/PDF overlay (many short lines). Identical texts
     * are deduped and pure number/symbol lines are passed through untouched.
     * On any batched-decode error it falls back to per-line [translate], and any
     * empty result for a non-empty input is retried individually.
     */
    @Synchronized
    fun translateBatch(texts: List<String>, srcToken: String, tgtToken: String): List<String> {
        val out = Array(texts.size) { "" }
        val tgtLangId = tokenizer.tokenId(tgtToken)

        // Dedupe + skip non-linguistic lines.
        val uniq = ArrayList<String>()
        val uniqIndex = HashMap<String, Int>()
        val slot = IntArray(texts.size) { -1 }
        for (i in texts.indices) {
            val trimmed = texts[i].trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.none { it.isLetter() }) { out[i] = texts[i]; continue }
            slot[i] = uniqIndex.getOrPut(trimmed) { uniq.add(trimmed); uniq.size - 1 }
        }
        if (uniq.isEmpty()) return out.toList()

        val translated = ArrayList<String>(uniq.size)
        var start = 0
        while (start < uniq.size) {
            val chunk = uniq.subList(start, minOf(start + MAX_BATCH, uniq.size))
            val res = try {
                translateManyMerged(chunk, srcToken, tgtLangId)
            } catch (e: Exception) {
                chunk.map { try { translate(it, srcToken, tgtToken) } catch (e2: Exception) { it } }
            }
            translated.addAll(res)
            start += MAX_BATCH
        }

        // Safety net: retry any empty result individually.
        for (u in uniq.indices) {
            if (translated[u].isBlank()) {
                translated[u] = try { translate(uniq[u], srcToken, tgtToken) } catch (e: Exception) { uniq[u] }
            }
        }

        for (i in texts.indices) if (slot[i] >= 0) out[i] = translated[slot[i]]
        return out.toList()
    }

    /** Batched encoder + greedy decode on the merged decoder for a set of texts. */
    private fun translateManyMerged(texts: List<String>, srcToken: String, tgtLangId: Int): List<String> {
        val b = texts.size
        val enc = texts.map { tokenizer.encode(it, srcToken) }
        val maxS = (enc.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)

        val idsFlat = LongArray(b * maxS) { PAD.toLong() }
        val maskFlat = LongArray(b * maxS) { 0L }
        for (r in 0 until b) {
            val row = enc[r]
            for (c in row.indices) {
                idsFlat[r * maxS + c] = row[c].toLong()
                maskFlat[r * maxS + c] = 1L
            }
        }

        val encMask = OnnxTensor.createTensor(env, LongBuffer.wrap(maskFlat), longArrayOf(b.toLong(), maxS.toLong()))
        val encHidden: OnnxTensor
        run {
            val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(idsFlat), longArrayOf(b.toLong(), maxS.toLong()))
            ids.use { i ->
                val res = encoder.run(mapOf("input_ids" to i, "attention_mask" to encMask))
                encHidden = copyTensor(res.get("last_hidden_state").get() as OnnxTensor)
                res.close()
            }
        }

        var pastDecKey = Array(NUM_LAYERS) { FloatArray(0) }
        var pastDecVal = Array(NUM_LAYERS) { FloatArray(0) }
        var pastEncKey = Array(NUM_LAYERS) { FloatArray(0) }
        var pastEncVal = Array(NUM_LAYERS) { FloatArray(0) }
        var pastDecLen = 0

        val generated = Array(b) { ArrayList<Int>(48) }
        val finished = BooleanArray(b)
        val maxOut = maxNewTokens.coerceAtMost(20 + maxS * 3)

        try {
            val prime = LongArray(b * 2)
            for (r in 0 until b) { prime[r * 2] = DECODER_START.toLong(); prime[r * 2 + 1] = tgtLangId.toLong() }
            var res = runDecoderBatch(prime, 2, b, encMask, encHidden, false, pastDecKey, pastDecVal, pastEncKey, pastEncVal, 0, maxS)
            val next = IntArray(b) { r -> argmaxRowBatch(res.logits, r, res.seqLen, res.vocab, generated[r]) }
            pastDecKey = res.decKey; pastDecVal = res.decVal
            pastEncKey = res.encKey; pastEncVal = res.encVal
            pastDecLen = res.seqLen

            var step = 0
            while (true) {
                for (r in 0 until b) {
                    if (finished[r]) continue
                    if (next[r] == EOS) finished[r] = true else generated[r].add(next[r])
                }
                if (finished.all { it } || step >= maxOut) break
                step++
                val feed = LongArray(b) { r -> if (finished[r]) PAD.toLong() else next[r].toLong() }
                res = runDecoderBatch(feed, 1, b, encMask, encHidden, true, pastDecKey, pastDecVal, pastEncKey, pastEncVal, pastDecLen, maxS)
                for (r in 0 until b) if (!finished[r]) next[r] = argmaxRowBatch(res.logits, r, res.seqLen, res.vocab, generated[r])
                pastDecKey = res.decKey; pastDecVal = res.decVal
                pastDecLen += 1
            }
        } finally {
            encHidden.close()
            encMask.close()
        }

        return (0 until b).map { tokenizer.decode(generated[it].toIntArray()) }
    }

    private fun runDecoderBatch(
        inputIds: LongArray,
        decLen: Int,
        b: Int,
        encMask: OnnxTensor,
        encHidden: OnnxTensor,
        useCache: Boolean,
        pastDecKey: Array<FloatArray>,
        pastDecVal: Array<FloatArray>,
        pastEncKey: Array<FloatArray>,
        pastEncVal: Array<FloatArray>,
        pastDecLen: Int,
        srcLen: Int,
    ): DecodeResult {
        val inputs = HashMap<String, OnnxTensor>()
        val toClose = ArrayList<OnnxTensor>()
        try {
            val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(b.toLong(), decLen.toLong()))
            toClose.add(ids)
            inputs["input_ids"] = ids
            inputs["encoder_attention_mask"] = encMask
            inputs["encoder_hidden_states"] = encHidden

            val decPastLen = if (useCache) pastDecLen else 0
            val encPastLen = if (useCache) srcLen else 0
            for (i in 0 until NUM_LAYERS) {
                val dk = tensor4dBatch(if (useCache) pastDecKey[i] else FloatArray(0), b, decPastLen)
                val dv = tensor4dBatch(if (useCache) pastDecVal[i] else FloatArray(0), b, decPastLen)
                val ek = tensor4dBatch(if (useCache) pastEncKey[i] else FloatArray(0), b, encPastLen)
                val ev = tensor4dBatch(if (useCache) pastEncVal[i] else FloatArray(0), b, encPastLen)
                toClose.add(dk); toClose.add(dv); toClose.add(ek); toClose.add(ev)
                inputs["past_key_values.$i.decoder.key"] = dk
                inputs["past_key_values.$i.decoder.value"] = dv
                inputs["past_key_values.$i.encoder.key"] = ek
                inputs["past_key_values.$i.encoder.value"] = ev
            }
            val flag = OnnxTensor.createTensor(env, booleanArrayOf(useCache))
            toClose.add(flag)
            inputs["use_cache_branch"] = flag

            val res = decoder.run(inputs)
            try {
                val logitsT = res.get("logits").get() as OnnxTensor
                val shape = logitsT.info.shape // [b, decLen, vocab]
                val seqLen = shape[1].toInt()
                val vocab = shape[2].toInt()
                val logits = floatArrayFrom(logitsT)

                val decKey = Array(NUM_LAYERS) { FloatArray(0) }
                val decVal = Array(NUM_LAYERS) { FloatArray(0) }
                val encKey = Array(NUM_LAYERS) { FloatArray(0) }
                val encVal = Array(NUM_LAYERS) { FloatArray(0) }
                for (i in 0 until NUM_LAYERS) {
                    decKey[i] = floatArrayFrom(res.get("present.$i.decoder.key").get() as OnnxTensor)
                    decVal[i] = floatArrayFrom(res.get("present.$i.decoder.value").get() as OnnxTensor)
                    if (!useCache) {
                        encKey[i] = floatArrayFrom(res.get("present.$i.encoder.key").get() as OnnxTensor)
                        encVal[i] = floatArrayFrom(res.get("present.$i.encoder.value").get() as OnnxTensor)
                    }
                }
                return DecodeResult(
                    logits, seqLen, vocab,
                    decKey, decVal,
                    if (useCache) pastEncKey else encKey,
                    if (useCache) pastEncVal else encVal,
                )
            } finally {
                res.close()
            }
        } finally {
            for (t in toClose) t.close()
        }
    }

    /** Builds a [b, heads, seqLen, headDim] float tensor from flat data. */
    private fun tensor4dBatch(data: FloatArray, b: Int, seqLen: Int): OnnxTensor {
        val shape = longArrayOf(b.toLong(), NUM_HEADS.toLong(), seqLen.toLong(), HEAD_DIM.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    /** Greedy argmax for batch row [r] at its last position. */
    private fun argmaxRowBatch(logits: FloatArray, r: Int, seqLen: Int, vocab: Int, generated: List<Int>): Int {
        val base = (r * seqLen + (seqLen - 1)) * vocab
        val banned = HashSet<Int>()
        banned.add(PAD)
        if (noRepeatNgram > 0 && generated.size >= noRepeatNgram - 1) {
            val n = noRepeatNgram
            val prefix = generated.subList(generated.size - (n - 1), generated.size)
            var i = 0
            while (i + n <= generated.size) {
                var match = true
                for (k in 0 until n - 1) if (generated[i + k] != prefix[k]) { match = false; break }
                if (match) banned.add(generated[i + n - 1])
                i++
            }
        }
        var best = -1
        var bestVal = Float.NEGATIVE_INFINITY
        var t = 0
        while (t < vocab) {
            if (t !in banned) {
                val v = logits[base + t]
                if (v > bestVal) { bestVal = v; best = t }
            }
            t++
        }
        return if (best >= 0) best else EOS
    }

    private class DecodeResult(
        val logits: FloatArray,
        val seqLen: Int,
        val vocab: Int,
        val decKey: Array<FloatArray>,
        val decVal: Array<FloatArray>,
        val encKey: Array<FloatArray>,
        val encVal: Array<FloatArray>,
    )

    private fun runDecoder(
        inputIds: LongArray,
        encMask: OnnxTensor,
        encHidden: OnnxTensor,
        useCache: Boolean,
        pastDecKey: Array<FloatArray>,
        pastDecVal: Array<FloatArray>,
        pastEncKey: Array<FloatArray>,
        pastEncVal: Array<FloatArray>,
        pastDecLen: Int,
        srcLen: Int,
    ): DecodeResult {
        val decLen = inputIds.size
        val inputs = HashMap<String, OnnxTensor>()
        val toClose = ArrayList<OnnxTensor>()
        try {
            val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, decLen.toLong()))
            toClose.add(ids)
            inputs["input_ids"] = ids
            inputs["encoder_attention_mask"] = encMask
            inputs["encoder_hidden_states"] = encHidden

            val decPastLen = if (useCache) pastDecLen else 0
            val encPastLen = if (useCache) srcLen else 0
            for (i in 0 until NUM_LAYERS) {
                val dk = tensor4d(if (useCache) pastDecKey[i] else FloatArray(0), decPastLen)
                val dv = tensor4d(if (useCache) pastDecVal[i] else FloatArray(0), decPastLen)
                val ek = tensor4d(if (useCache) pastEncKey[i] else FloatArray(0), encPastLen)
                val ev = tensor4d(if (useCache) pastEncVal[i] else FloatArray(0), encPastLen)
                toClose.add(dk); toClose.add(dv); toClose.add(ek); toClose.add(ev)
                inputs["past_key_values.$i.decoder.key"] = dk
                inputs["past_key_values.$i.decoder.value"] = dv
                inputs["past_key_values.$i.encoder.key"] = ek
                inputs["past_key_values.$i.encoder.value"] = ev
            }
            val flag = OnnxTensor.createTensor(env, booleanArrayOf(useCache))
            toClose.add(flag)
            inputs["use_cache_branch"] = flag

            val res = decoder.run(inputs)
            try {
                val logitsT = res.get("logits").get() as OnnxTensor
                val shape = logitsT.info.shape // [1, decLen, vocab]
                val seqLen = shape[1].toInt()
                val vocab = shape[2].toInt()
                val logits = floatArrayFrom(logitsT)

                val decKey = Array(NUM_LAYERS) { FloatArray(0) }
                val decVal = Array(NUM_LAYERS) { FloatArray(0) }
                val encKey = Array(NUM_LAYERS) { FloatArray(0) }
                val encVal = Array(NUM_LAYERS) { FloatArray(0) }
                for (i in 0 until NUM_LAYERS) {
                    decKey[i] = floatArrayFrom(res.get("present.$i.decoder.key").get() as OnnxTensor)
                    decVal[i] = floatArrayFrom(res.get("present.$i.decoder.value").get() as OnnxTensor)
                    if (!useCache) {
                        encKey[i] = floatArrayFrom(res.get("present.$i.encoder.key").get() as OnnxTensor)
                        encVal[i] = floatArrayFrom(res.get("present.$i.encoder.value").get() as OnnxTensor)
                    }
                }
                return DecodeResult(
                    logits, seqLen, vocab,
                    decKey, decVal,
                    if (useCache) pastEncKey else encKey,
                    if (useCache) pastEncVal else encVal,
                )
            } finally {
                res.close()
            }
        } finally {
            for (t in toClose) t.close()
        }
    }

    /** Builds a [1, heads, seqLen, headDim] float tensor from flat data. */
    private fun tensor4d(data: FloatArray, seqLen: Int): OnnxTensor {
        val shape = longArrayOf(1, NUM_HEADS.toLong(), seqLen.toLong(), HEAD_DIM.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    /** Greedy argmax over the vocab row at [posIndex], honouring no-repeat-ngram and bans. */
    private fun argmaxRow(logits: FloatArray, posIndex: Int, vocab: Int, generated: List<Int>): Int {
        val base = posIndex * vocab
        // Block illegal tokens.
        val banned = HashSet<Int>()
        banned.add(PAD)
        if (noRepeatNgram > 0 && generated.size >= noRepeatNgram - 1) {
            val n = noRepeatNgram
            val prefix = generated.subList(generated.size - (n - 1), generated.size)
            var i = 0
            while (i + n <= generated.size) {
                var match = true
                for (k in 0 until n - 1) if (generated[i + k] != prefix[k]) { match = false; break }
                if (match) banned.add(generated[i + n - 1])
                i++
            }
        }
        var best = -1
        var bestVal = Float.NEGATIVE_INFINITY
        var idx = base
        var t = 0
        while (t < vocab) {
            if (t !in banned) {
                val v = logits[idx]
                if (v > bestVal) { bestVal = v; best = t }
            }
            idx++; t++
        }
        return if (best >= 0) best else EOS
    }

    private fun floatArrayFrom(t: OnnxTensor): FloatArray {
        val fb = t.floatBuffer
        val arr = FloatArray(fb.remaining())
        fb.get(arr)
        return arr
    }

    private fun copyTensor(t: OnnxTensor): OnnxTensor {
        val shape = t.info.shape
        val arr = floatArrayFrom(t)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(arr), shape)
    }

    fun close() {
        try { encoder.close() } catch (_: Exception) {}
        try { decoder.close() } catch (_: Exception) {}
    }
}
