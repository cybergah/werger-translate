package dev.wergir.translate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.wergir.translate.R
import dev.wergir.translate.TranslateViewModel
import dev.wergir.translate.nllb.Language

/** Kurmancî + Soranî show the Kurdistan flag instead of an emoji. */
private fun hasFlag(token: String) = token == "kmr_Latn" || token == "ckb_Arab"

@Composable
private fun LangLabel(lang: Language) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasFlag(lang.token)) {
            Image(
                painter = painterResource(R.drawable.kurdistan_flag),
                contentDescription = null,
                modifier = Modifier.height(14.dp).clip(RoundedCornerShape(2.dp)),
            )
            Text("  ${lang.nativeName}", maxLines = 1)
        } else {
            Text("${lang.emoji}  ${lang.nativeName}", maxLines = 1)
        }
    }
}

@Composable
fun LanguageBar(vm: TranslateViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LanguagePicker(vm.sourceLang, vm.languages, { vm.setSource(it) }, Modifier.weight(1f))
        IconButton(onClick = { vm.swapLanguages() }) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "swap")
        }
        LanguagePicker(vm.targetLang, vm.languages, { vm.setTarget(it) }, Modifier.weight(1f))
    }
}

@Composable
fun LanguagePicker(
    label: Language,
    options: List<Language>,
    onSelected: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true; query = "" },
            modifier = Modifier.fillMaxWidth(),
        ) {
            LangLabel(label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 440.dp),
        ) {
            // Search box — there are ~200 languages.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Ara / Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            val q = query.trim().lowercase()
            val shown = if (q.isEmpty()) {
                options
            } else {
                options.filter {
                    it.nativeName.lowercase().contains(q) ||
                        it.trName.lowercase().contains(q) ||
                        it.token.lowercase().contains(q)
                }
            }
            shown.forEach { lang ->
                DropdownMenuItem(
                    text = { LangLabel(lang) },
                    onClick = { onSelected(lang); expanded = false },
                )
            }
        }
    }
}
