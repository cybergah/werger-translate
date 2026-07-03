package dev.wergir.translate.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.wergir.translate.ModelState
import dev.wergir.translate.R
import dev.wergir.translate.TranslateViewModel

private val UI_LANGS = listOf(
    "ku" to "Kurmancî",
    "ckb" to "سۆرانی",
    "tr" to "Türkçe",
    "en" to "English",
    "ar" to "العربية",
    "fa" to "فارسی",
)

@Composable
fun SettingsScreen(
    vm: TranslateViewModel,
    modifier: Modifier = Modifier,
    onChangeLanguage: (String) -> Unit,
) {
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.importModelFromTree(uri)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        // ----- Translation mode -----
        SectionTitle(stringResource(R.string.settings_mode))
        ModeRow("auto", stringResource(R.string.mode_auto), vm.mode) { vm.applyMode(it) }
        ModeRow("online", stringResource(R.string.mode_online), vm.mode) { vm.applyMode(it) }
        ModeRow("offline", stringResource(R.string.mode_offline), vm.mode) { vm.applyMode(it) }

        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        // ----- Offline model -----
        SectionTitle(stringResource(R.string.settings_model))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                val statusText = when (vm.modelState) {
                    ModelState.READY -> stringResource(R.string.model_ready)
                    ModelState.DOWNLOADING -> stringResource(R.string.model_downloading, vm.downloadedMb, vm.totalMb)
                    ModelState.IMPORTING -> stringResource(R.string.model_importing, vm.downloadedMb, vm.totalMb)
                    ModelState.LOADING -> stringResource(R.string.model_loading)
                    ModelState.ERROR -> "⚠️ ${vm.modelError ?: ""}"
                    ModelState.ABSENT -> stringResource(R.string.model_absent)
                }
                Text(statusText, fontWeight = FontWeight.Medium)
                if (vm.modelState == ModelState.DOWNLOADING || vm.modelState == ModelState.IMPORTING) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { vm.downloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.model_path, vm.modelsPath), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedButton(
                        onClick = { vm.downloadModel() },
                        enabled = vm.modelState != ModelState.DOWNLOADING && vm.modelState != ModelState.LOADING && vm.modelState != ModelState.IMPORTING,
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Text("  " + stringResource(R.string.model_download, vm.totalMb))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text("  " + stringResource(R.string.model_import))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.model_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        // ----- Online settings -----
        SectionTitle(stringResource(R.string.settings_online))
        OutlinedTextField(
            value = vm.libreUrl,
            onValueChange = { vm.applyLibreUrl(it) },
            label = { Text(stringResource(R.string.lt_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.libreApiKey,
            onValueChange = { vm.applyLibreApiKey(it) },
            label = { Text(stringResource(R.string.lt_key)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        // ----- App language -----
        SectionTitle(stringResource(R.string.settings_language))
        var expanded by remember { mutableStateOf(false) }
        val current = UI_LANGS.firstOrNull { it.first == vm.prefs.uiLang }?.second ?: "Kurmancî"
        OutlinedButton(onClick = { expanded = true }) { Text(current) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UI_LANGS.forEach { (tag, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onChangeLanguage(tag) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ModeRow(value: String, label: String, current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = current == value, onClick = { onSelect(value) }).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == value, onClick = { onSelect(value) })
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
