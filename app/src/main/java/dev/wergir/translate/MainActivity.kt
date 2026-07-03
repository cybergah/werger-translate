package dev.wergir.translate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.wergir.translate.ui.AboutScreen
import dev.wergir.translate.ui.DocumentScreen
import dev.wergir.translate.ui.GuideScreen
import dev.wergir.translate.ui.OnboardingScreen
import dev.wergir.translate.ui.PhotoScreen
import dev.wergir.translate.ui.SettingsScreen
import dev.wergir.translate.ui.SplashScreen
import dev.wergir.translate.ui.TranslateScreen
import dev.wergir.translate.ui.theme.WergirTheme
import dev.wergir.translate.util.LocaleManager
import dev.wergir.translate.util.Prefs

class MainActivity : ComponentActivity() {

    private val vm: TranslateViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val tag = Prefs(newBase).uiLang
        super.attachBaseContext(LocaleManager.wrap(newBase, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleSharedText(intent)
        setContent {
            WergirTheme {
                var showSplash by remember { mutableStateOf(true) }
                // Show the step-by-step tutorial on the very first launch; it is
                // skippable and can be replayed later from the Guide screen.
                var showOnboarding by rememberSaveable { mutableStateOf(!vm.prefs.firstRunDone) }
                if (showSplash) {
                    SplashScreen(onDone = { showSplash = false })
                } else {
                    Box(Modifier.fillMaxSize()) {
                        MainScaffold(
                            vm = vm,
                            onChangeLanguage = { tag ->
                                vm.prefs.uiLang = tag
                                recreate()
                            },
                            onReplayTutorial = { showOnboarding = true },
                        )
                        if (showOnboarding) {
                            OnboardingScreen(onFinish = {
                                vm.prefs.firstRunDone = true
                                showOnboarding = false
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    /**
     * Handle text arriving from the system text-selection menu (PROCESS_TEXT)
     * or a Share sheet (SEND): drop it into the translator and translate.
     */
    private fun handleSharedText(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND ->
                if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
            else -> null
        }
        if (!text.isNullOrBlank()) {
            vm.inputText = text
            vm.translate()
        }
    }
}

private data class NavItem(val labelRes: Int, val icon: ImageVector)

@Composable
private fun MainScaffold(
    vm: TranslateViewModel,
    onChangeLanguage: (String) -> Unit,
    onReplayTutorial: () -> Unit,
) {
    val items = remember {
        listOf(
            NavItem(R.string.nav_translate, Icons.Filled.Translate),
            NavItem(R.string.nav_document, Icons.Filled.Description),
            NavItem(R.string.nav_photo, Icons.Filled.PhotoCamera),
            NavItem(R.string.nav_guide, Icons.Filled.MenuBook),
            NavItem(R.string.nav_settings, Icons.Filled.Settings),
            NavItem(R.string.nav_about, Icons.Filled.Info),
        )
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (selected) {
            0 -> TranslateScreen(vm, m)
            1 -> DocumentScreen(vm, m)
            2 -> PhotoScreen(vm, m)
            3 -> GuideScreen(onReplayTutorial = onReplayTutorial, modifier = m)
            4 -> SettingsScreen(vm, m, onChangeLanguage)
            else -> AboutScreen(m)
        }
    }
}
