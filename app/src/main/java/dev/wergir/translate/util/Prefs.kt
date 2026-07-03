package dev.wergir.translate.util

import android.content.Context

/** Simple SharedPreferences wrapper for app settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("werger_prefs", Context.MODE_PRIVATE)

    /** UI language tag: ku (Kurmancî, default), ckb, fa, ar, tr, en. */
    var uiLang: String
        get() = sp.getString(KEY_UI_LANG, "ku") ?: "ku"
        set(v) = sp.edit().putString(KEY_UI_LANG, v).apply()

    /** Translation mode: "auto", "online", "offline". */
    var mode: String
        get() = sp.getString(KEY_MODE, "auto") ?: "auto"
        set(v) = sp.edit().putString(KEY_MODE, v).apply()

    var libreUrl: String
        get() = sp.getString(KEY_LT_URL, DEFAULT_LT_URL) ?: DEFAULT_LT_URL
        set(v) = sp.edit().putString(KEY_LT_URL, v).apply()

    var libreApiKey: String
        get() = sp.getString(KEY_LT_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_LT_KEY, v).apply()

    var lastSource: String
        get() = sp.getString(KEY_SRC, "tur_Latn") ?: "tur_Latn"
        set(v) = sp.edit().putString(KEY_SRC, v).apply()

    var lastTarget: String
        get() = sp.getString(KEY_TGT, "kmr_Latn") ?: "kmr_Latn"
        set(v) = sp.edit().putString(KEY_TGT, v).apply()

    var firstRunDone: Boolean
        get() = sp.getBoolean(KEY_FIRST_RUN, false)
        set(v) = sp.edit().putBoolean(KEY_FIRST_RUN, v).apply()

    companion object {
        // Empty by default → the app uses key-free providers (Google/MyMemory).
        // Set a URL only to use your own LibreTranslate server.
        const val DEFAULT_LT_URL = ""
        private const val KEY_UI_LANG = "ui_lang"
        private const val KEY_MODE = "mode"
        private const val KEY_LT_URL = "lt_url"
        private const val KEY_LT_KEY = "lt_key"
        private const val KEY_SRC = "src"
        private const val KEY_TGT = "tgt"
        private const val KEY_FIRST_RUN = "first_run"
    }
}
