package dev.wergir.translate.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the user-selected UI language by wrapping the base context. */
object LocaleManager {

    /** Maps our short tags to Android Locales. Sorani uses BCP-47 "ckb". */
    fun localeFor(tag: String): Locale = when (tag) {
        "ckb" -> Locale.forLanguageTag("ckb")
        "ku" -> Locale.forLanguageTag("ku")
        else -> Locale(tag)
    }

    fun wrap(context: Context, tag: String): Context {
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
