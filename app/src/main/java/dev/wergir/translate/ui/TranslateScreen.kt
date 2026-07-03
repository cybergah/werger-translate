package dev.wergir.translate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.wergir.translate.R
import dev.wergir.translate.TranslateViewModel
import dev.wergir.translate.UsedEngine

@Composable
fun TranslateScreen(vm: TranslateViewModel, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        LanguageBar(vm)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.inputText,
            onValueChange = { vm.inputText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(stringResource(R.string.input_hint)) },
            trailingIcon = {
                Row {
                    if (vm.inputText.isNotEmpty()) {
                        IconButton(onClick = { vm.speakSource() }) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = stringResource(R.string.listen))
                        }
                        IconButton(onClick = { vm.inputText = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                }
            },
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.translate() },
            modifier = Modifier.fillMaxWidth(),
            enabled = vm.inputText.isNotBlank() && !vm.isTranslating,
        ) {
            if (vm.isTranslating) {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.translating))
            } else {
                Icon(Icons.Filled.Translate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.translate_btn))
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${vm.targetLang.emoji}  ${vm.targetLang.nativeName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row {
                        if (vm.outputText.isNotBlank()) {
                            IconButton(onClick = { vm.speakTarget() }) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = stringResource(R.string.listen))
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(vm.outputText)) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = vm.outputText.ifEmpty { stringResource(R.string.output_placeholder) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (vm.outputText.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                )
                if (vm.usedEngine != UsedEngine.NONE && vm.outputText.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    val tag = if (vm.usedEngine == UsedEngine.ONLINE)
                        stringResource(R.string.engine_online) else stringResource(R.string.engine_offline)
                    Text("• $tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        vm.statusMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
