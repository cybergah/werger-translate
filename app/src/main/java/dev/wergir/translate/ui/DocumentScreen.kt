package dev.wergir.translate.ui

import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.wergir.translate.R
import dev.wergir.translate.TranslateViewModel

@Composable
fun DocumentScreen(vm: TranslateViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var docFullscreen by remember { mutableStateOf(false) }
    var fullscreenPage by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var name: String? = null
            val mime: String? = context.contentResolver.getType(uri)
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
            }
            vm.translateDocument(uri, mime, name)
        }
    }

    LaunchedEffect(vm.docMessage) {
        vm.docMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearDocMessage()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        LanguageBar(vm)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.doc_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { picker.launch(arrayOf("text/plain", "application/pdf")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.docBusy,
        ) {
            Icon(Icons.Filled.FileOpen, contentDescription = null)
            Text("  " + stringResource(R.string.doc_pick))
        }

        vm.docName?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        if (vm.docBusy) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.doc_busy))
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { vm.docProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.cancelDocument() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel_btn))
            }
        }

        if (vm.docResultReady) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.doc_ready), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            // In-app preview: every translated PDF page (scrollable), or the text.
            if (vm.docPreviewPages.isNotEmpty()) {
                vm.docPreviewPages.forEachIndexed { i, bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clickable { fullscreenPage = i; docFullscreen = true },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            vm.docPreviewText?.let { txt ->
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        txt,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { openDoc(context, vm) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Text("  " + stringResource(R.string.doc_open), maxLines = 1)
                }
                OutlinedButton(onClick = { vm.saveDocResult() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("  " + stringResource(R.string.photo_save), maxLines = 1)
                }
                OutlinedButton(onClick = { shareDoc(context, vm) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  " + stringResource(R.string.doc_share), maxLines = 1)
                }
            }
        }

        if (docFullscreen && vm.docPreviewPages.isNotEmpty()) {
            Dialog(onDismissRequest = { docFullscreen = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(rememberScrollState())
                        .clickable { docFullscreen = false },
                ) {
                    // Start from the tapped page, then the rest, so the whole
                    // document is reviewable at full width.
                    val pages = vm.docPreviewPages
                    val ordered = pages.drop(fullscreenPage) + pages.take(fullscreenPage)
                    ordered.forEach { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        vm.docStatus?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun shareDoc(context: android.content.Context, vm: TranslateViewModel) {
    val uri = vm.shareDocUri() ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = vm.docResultMime()
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Open the translated file in an external viewer (PDF/text app). */
private fun openDoc(context: android.content.Context, vm: TranslateViewModel) {
    val uri = vm.shareDocUri() ?: return
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, vm.docResultMime())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(view) }
}
