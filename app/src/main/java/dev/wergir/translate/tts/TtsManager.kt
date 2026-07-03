package dev.wergir.translate.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps Android's on-device TextToSpeech engine. Works offline once the device's
 * TTS voices are installed. Kurdish (Kurmancî/Soranî) is generally not provided by
 * stock Android TTS engines; [isLanguageAvailable] lets the UI reflect that.
 */
class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    /** Maps FLORES-200 codes to TTS locales (BCP-47 tags). */
    private fun localeFor(flores: String): Locale {
        val tag = when (flores) {
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
            "tgl_Latn" -> "fil"
            "hin_Deva" -> "hi"
            "urd_Arab" -> "ur"
            "ben_Beng" -> "bn"
            "jpn_Jpan" -> "ja"
            "kor_Hang" -> "ko"
            "zho_Hans" -> "zh-Hans"
            "zho_Hant" -> "zh-Hant"
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
        return Locale.forLanguageTag(tag)
    }

    fun isLanguageAvailable(flores: String): Boolean {
        val t = tts ?: return false
        if (!ready) return false
        val res = t.isLanguageAvailable(localeFor(flores))
        return res >= TextToSpeech.LANG_AVAILABLE
    }

    /** Returns true if speech started; false if the language is unavailable. */
    fun speak(text: String, flores: String): Boolean {
        val t = tts ?: return false
        if (!ready || text.isBlank()) return false
        val locale = localeFor(flores)
        if (t.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return false
        t.language = locale
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "werger")
        return true
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
