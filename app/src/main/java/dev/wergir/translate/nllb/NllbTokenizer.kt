package dev.wergir.translate.nllb

import android.util.JsonReader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * Pure-Kotlin reimplementation of the NLLB-200 ("NllbTokenizer") fast tokenizer,
 * matching the pipeline declared in Xenova/nllb-200-distilled-600M's tokenizer.json:
 *
 *   normalizer:     NFKC (approximating SentencePiece's nmt_nfkc precompiled charsmap)
 *   pre_tokenizer:  Metaspace  (space -> '▁', add_prefix_space = true, split per word)
 *   model:          BPE        (fuse_unk = true, no byte_fallback, no subword prefixes)
 *   post_processor: [src_lang] + tokens + </s>
 *
 * Encoding produces ids ready for the encoder; decoding reverses the Metaspace step.
 *
 * No native libraries are required, so the tokenizer runs fully offline.
 */
class NllbTokenizer private constructor(
    private val vocab: HashMap<String, Int>,
    private val idToToken: Array<String?>,
    private val mergeRanks: HashMap<String, Int>,
) {
    companion object {
        private const val META = '▁' // ▁ (U+2581 LOWER ONE EIGHTH BLOCK)
        const val UNK_ID = 3
        const val EOS_ID = 2 // </s>
        const val PAD_ID = 1
        const val BOS_ID = 0 // <s>

        /** Loads vocab + merges from a tokenizer.json file using a streaming reader. */
        fun fromFile(tokenizerJson: File): NllbTokenizer {
            val vocab = HashMap<String, Int>(300_000)
            val merges = HashMap<String, Int>(550_000)
            var maxId = 0

            JsonReader(InputStreamReader(BufferedInputStream(FileInputStream(tokenizerJson)), Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "model" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "vocab" -> {
                                        r.beginObject()
                                        while (r.hasNext()) {
                                            val tok = r.nextName()
                                            val id = r.nextInt()
                                            vocab[tok] = id
                                            if (id > maxId) maxId = id
                                        }
                                        r.endObject()
                                    }
                                    "merges" -> {
                                        r.beginArray()
                                        var rank = 0
                                        while (r.hasNext()) {
                                            // Each merge is a "left right" string.
                                            merges[r.nextString()] = rank++
                                        }
                                        r.endArray()
                                    }
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }

            val idToToken = arrayOfNulls<String>(maxId + 1)
            for ((tok, id) in vocab) idToToken[id] = tok

            return NllbTokenizer(vocab, idToToken, merges)
        }
    }

    /** True if a language token exists in the vocabulary. */
    fun hasToken(token: String): Boolean = vocab.containsKey(token)

    fun tokenId(token: String): Int = vocab[token] ?: UNK_ID

    /**
     * Encodes [text] for the NLLB encoder with the given source language token,
     * producing: [srcLangId, ...subwordIds, EOS].
     */
    fun encode(text: String, srcLangToken: String): IntArray {
        val pieces = tokenizeToIds(text)
        val out = IntArray(pieces.size + 2)
        out[0] = tokenId(srcLangToken)
        for (i in pieces.indices) out[i + 1] = pieces[i]
        out[out.size - 1] = EOS_ID
        return out
    }

    /** Tokenizes raw text into subword ids (no special tokens). */
    private fun tokenizeToIds(text: String): IntArray {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return IntArray(0)

        val ids = ArrayList<Int>(normalized.length + 4)
        // Metaspace: split on spaces, each word prefixed with ▁.
        for (word in normalized.split(' ')) {
            if (word.isEmpty()) continue
            val piece = META + word
            for (sym in bpe(piece)) {
                val id = vocab[sym]
                if (id != null) {
                    ids.add(id)
                } else {
                    // fuse_unk = true: collapse consecutive unknowns into a single <unk>.
                    if (ids.isEmpty() || ids[ids.size - 1] != UNK_ID) ids.add(UNK_ID)
                }
            }
        }
        return ids.toIntArray()
    }

    /** Approximation of SentencePiece nmt_nfkc normalization. */
    private fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        // Collapse any whitespace run to a single ASCII space and trim.
        return nfkc.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Byte-pair encoding over a single Metaspace word. Repeatedly merges the
     * adjacent symbol pair with the lowest merge rank until none remain.
     */
    private fun bpe(word: String): List<String> {
        // Split into Unicode code points (so surrogate pairs stay intact).
        val symbols = ArrayList<String>(word.length + 1)
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            val n = Character.charCount(cp)
            symbols.add(word.substring(i, i + n))
            i += n
        }
        if (symbols.size < 2) return symbols

        while (true) {
            var bestRank = Int.MAX_VALUE
            var bestIdx = -1
            for (j in 0 until symbols.size - 1) {
                val rank = mergeRanks[symbols[j] + " " + symbols[j + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestIdx = j
                }
            }
            if (bestIdx < 0) break
            // Merge every adjacent occurrence of the chosen pair in one pass.
            val a = symbols[bestIdx]
            val b = symbols[bestIdx + 1]
            val merged = a + b
            val next = ArrayList<String>(symbols.size)
            var k = 0
            while (k < symbols.size) {
                if (k < symbols.size - 1 && symbols[k] == a && symbols[k + 1] == b) {
                    next.add(merged)
                    k += 2
                } else {
                    next.add(symbols[k])
                    k += 1
                }
            }
            if (next.size == symbols.size) break
            symbols.clear()
            symbols.addAll(next)
            if (symbols.size < 2) break
        }
        return symbols
    }

    /**
     * Decodes generated ids back to text. Special tokens (ids 0..3 and language
     * codes) are skipped; Metaspace markers become spaces.
     */
    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id < 0 || id >= idToToken.size) continue
            if (id == EOS_ID || id == BOS_ID || id == PAD_ID || id == UNK_ID) continue
            val tok = idToToken[id] ?: continue
            // Skip language / special added tokens like "tur_Latn", "kmr_Latn", "<mask>".
            if (isSpecialToken(tok)) continue
            sb.append(tok)
        }
        return sb.toString().replace(META, ' ').trim()
    }

    private fun isSpecialToken(tok: String): Boolean {
        // Language codes look like "xxx_Yyyy"; other specials are wrapped in <...>.
        if (tok.length >= 2 && tok[0] == '<' && tok[tok.length - 1] == '>') return true
        if (tok.length == 8 && tok[3] == '_') {
            // e.g. tur_Latn, ckb_Arab — 3 letters, underscore, 4 letters.
            return tok.substring(0, 3).all { it in 'a'..'z' } &&
                tok.substring(4).all { it in 'a'..'z' || it in 'A'..'Z' }
        }
        return false
    }
}
