package dev.wergir.translate

import android.app.Application
import dev.wergir.translate.nllb.ModelManager
import dev.wergir.translate.util.Prefs

class WergirApp : Application() {
    val modelManager: ModelManager by lazy { ModelManager(this) }
    val prefs: Prefs by lazy { Prefs(this) }
}
