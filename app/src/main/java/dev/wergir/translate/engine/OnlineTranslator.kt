package dev.wergir.translate.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online translation backends.
 *
 * Out of the box the app uses **key-free** providers so translation works with
 * no setup: Google Translate's free `gtx` endpoint (best Kurdish coverage), with
 * MyMemory as a fallback. If [baseUrl] is set (a self-hosted/paid LibreTranslate
 * server) that is used instead. Internal codes are FLORES-200.
 */
class OnlineTranslator(
    private val baseUrl: String,
    private val apiKey: String,
) {
    fun translate(text: String, srcFlores: String, tgtFlores: String): String {
        return if (baseUrl.isBlank()) {
            // No server configured → free, no-key providers.
            try {
                translateGoogle(text, srcFlores, tgtFlores)
            } catch (e: Exception) {
                translateMyMemory(text, srcFlores, tgtFlores)
            }
        } else {
            translateLibre(text, srcFlores, tgtFlores)
        }
    }

    // ---------------- Google (free gtx endpoint, no key) ----------------

    private fun translateGoogle(text: String, srcFlores: String, tgtFlores: String): String {
        val sl = toGoogle(srcFlores)
        val tl = toGoogle(tgtFlores)
        val q = URLEncoder.encode(text, "UTF-8")
        val url = URL(
            "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=$sl&tl=$tl&dt=t&q=$q"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Werger/1.0")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            if (code !in 200..299) throw RuntimeException("Google HTTP $code")
            // Response: [[["translated","orig",...], ...], ...]
            val segments = JSONArray(resp).getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i) ?: continue
                sb.append(seg.optString(0, ""))
            }
            val out = sb.toString()
            if (out.isBlank()) throw RuntimeException("Google returned no text")
            return out
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- MyMemory (free, no key) ----------------

    private fun translateMyMemory(text: String, srcFlores: String, tgtFlores: String): String {
        val sl = toGoogle(srcFlores)
        val tl = toGoogle(tgtFlores)
        val q = URLEncoder.encode(text, "UTF-8")
        val pair = URLEncoder.encode("$sl|$tl", "UTF-8")
        val url = URL("https://api.mymemory.translated.net/get?q=$q&langpair=$pair")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            val obj = JSONObject(resp)
            val out = obj.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            if (out.isBlank()) throw RuntimeException("MyMemory could not translate this pair")
            return out
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- LibreTranslate (self-hosted / paid) ----------------

    private fun translateLibre(text: String, srcFlores: String, tgtFlores: String): String {
        val url = URL(baseUrl.trimEnd('/') + "/translate")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        val body = buildString {
            append("q=").append(URLEncoder.encode(text, "UTF-8"))
            append("&source=").append(toLt(srcFlores))
            append("&target=").append(toLt(tgtFlores))
            append("&format=text")
            if (apiKey.isNotBlank()) append("&api_key=").append(URLEncoder.encode(apiKey, "UTF-8"))
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(resp).optString("error") }.getOrNull()
                throw RuntimeException(msg?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            return JSONObject(resp).getString("translatedText")
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- Code maps ----------------

    /** Maps FLORES-200 codes to Google Translate codes (best Kurdish coverage). */
    private fun toGoogle(flores: String): String = when (flores) {
        "kmr_Latn" -> "ku"
        "ckb_Arab" -> "ckb"
        "tur_Latn" -> "tr"
        "eng_Latn" -> "en"
        "arb_Arab" -> "ar"
        "pes_Arab" -> "fa"
        "azj_Latn" -> "az"
        "deu_Latn" -> "de"
        "fra_Latn" -> "fr"
        "rus_Cyrl" -> "ru"
        "spa_Latn" -> "es"
        "ita_Latn" -> "it"
        "por_Latn" -> "pt"
        "nld_Latn" -> "nl"
        "pol_Latn" -> "pl"
        "ukr_Cyrl" -> "uk"
        "ron_Latn" -> "ro"
        "ell_Grek" -> "el"
        "swe_Latn" -> "sv"
        "dan_Latn" -> "da"
        "nob_Latn" -> "no"
        "fin_Latn" -> "fi"
        "ces_Latn" -> "cs"
        "slk_Latn" -> "sk"
        "hun_Latn" -> "hu"
        "bul_Cyrl" -> "bg"
        "srp_Cyrl" -> "sr"
        "hrv_Latn" -> "hr"
        "heb_Hebr" -> "he"
        "tha_Thai" -> "th"
        "vie_Latn" -> "vi"
        "ind_Latn" -> "id"
        "zsm_Latn" -> "ms"
        "tgl_Latn" -> "tl"
        "hin_Deva" -> "hi"
        "urd_Arab" -> "ur"
        "ben_Beng" -> "bn"
        "jpn_Jpan" -> "ja"
        "kor_Hang" -> "ko"
        "zho_Hans" -> "zh-CN"
        "zho_Hant" -> "zh-TW"
        "kaz_Cyrl" -> "kk"
        "uzn_Latn" -> "uz"
        "tat_Cyrl" -> "tt"
        "tgk_Cyrl" -> "tg"
        "uig_Arab" -> "ug"
        "hye_Armn" -> "hy"
        "kat_Geor" -> "ka"
        "swh_Latn" -> "sw"
        "afr_Latn" -> "af"
        else -> flores.substringBefore('_').take(2)
    }

    /** Maps our FLORES-200 codes to LibreTranslate ISO codes. */
    private fun toLt(flores: String): String = when (flores) {
        "kmr_Latn" -> "ku"
        "ckb_Arab" -> "ckb"
        "tur_Latn" -> "tr"
        "eng_Latn" -> "en"
        "arb_Arab" -> "ar"
        "pes_Arab" -> "fa"
        "azj_Latn" -> "az"
        "deu_Latn" -> "de"
        "fra_Latn" -> "fr"
        "rus_Cyrl" -> "ru"
        "spa_Latn" -> "es"
        "ita_Latn" -> "it"
        "por_Latn" -> "pt"
        "nld_Latn" -> "nl"
        "pol_Latn" -> "pl"
        "ukr_Cyrl" -> "uk"
        "ron_Latn" -> "ro"
        "ell_Grek" -> "el"
        "swe_Latn" -> "sv"
        "dan_Latn" -> "da"
        "nob_Latn" -> "nb"
        "fin_Latn" -> "fi"
        "ces_Latn" -> "cs"
        "slk_Latn" -> "sk"
        "hun_Latn" -> "hu"
        "bul_Cyrl" -> "bg"
        "srp_Cyrl" -> "sr"
        "hrv_Latn" -> "hr"
        "heb_Hebr" -> "he"
        "tha_Thai" -> "th"
        "vie_Latn" -> "vi"
        "ind_Latn" -> "id"
        "zsm_Latn" -> "ms"
        "tgl_Latn" -> "tl"
        "hin_Deva" -> "hi"
        "urd_Arab" -> "ur"
        "ben_Beng" -> "bn"
        "jpn_Jpan" -> "ja"
        "kor_Hang" -> "ko"
        "zho_Hans" -> "zh"
        "zho_Hant" -> "zt"
        "kaz_Cyrl" -> "kk"
        "uzn_Latn" -> "uz"
        "tat_Cyrl" -> "tt"
        "tgk_Cyrl" -> "tg"
        "uig_Arab" -> "ug"
        "hye_Armn" -> "hy"
        "kat_Geor" -> "ka"
        "swh_Latn" -> "sw"
        "afr_Latn" -> "af"
        else -> flores.substringBefore('_').take(2)
    }
}
