package dev.wergir.translate.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import dev.wergir.translate.R
import dev.wergir.translate.TranslateViewModel
import java.io.File

@Composable
fun PhotoScreen(vm: TranslateViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreen by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.setPhoto(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) pendingCameraUri?.let { vm.setPhoto(it) } }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = newCaptureUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Show save/other one-off messages as a toast, then clear.
    LaunchedEffect(vm.photoMessage) {
        vm.photoMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearPhotoMessage()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        LanguageBar(vm)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.photo_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { galleryPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Text("  " + stringResource(R.string.photo_gallery))
            }
            OutlinedButton(
                onClick = { permLauncher.launch(android.Manifest.permission.CAMERA) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("  " + stringResource(R.string.photo_camera))
            }
        }

        // The result overlay (if produced) replaces the source preview; otherwise
        // show the chosen source image.
        val result = vm.photoResult
        val shown = if (vm.showOriginalPhoto) vm.photoOriginal else result
        if (result != null && shown != null) {
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clickable { fullscreen = true },
            )
        } else {
            vm.photoUri?.let { uri ->
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.4f),
                )
            }
        }

        if (vm.photoUri != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.runPhoto() }, modifier = Modifier.fillMaxWidth(), enabled = !vm.photoBusy) {
                if (vm.photoBusy) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text("  " + stringResource(R.string.photo_busy))
                } else {
                    Icon(Icons.Filled.Translate, contentDescription = null)
                    Text("  " + stringResource(R.string.photo_run))
                }
            }
            if (vm.photoBusy) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.cancelPhoto() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        }

        if (result != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.togglePhotoView() }, modifier = Modifier.weight(1f)) {
                    Icon(if (vm.showOriginalPhoto) Icons.Filled.Translate else Icons.Filled.Photo, contentDescription = null)
                    Text(
                        "  " + stringResource(
                            if (vm.showOriginalPhoto) R.string.photo_show_translated else R.string.photo_show_original,
                        ),
                        maxLines = 1,
                    )
                }
                OutlinedButton(onClick = { fullscreen = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = null)
                    Text("  " + stringResource(R.string.fullscreen), maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.savePhotoResult() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("  " + stringResource(R.string.photo_save))
                }
                OutlinedButton(
                    onClick = { sharePhoto(context, vm) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  " + stringResource(R.string.photo_share))
                }
            }
        }

        // Fullscreen viewer for the shown image.
        if (fullscreen && shown != null) {
            Dialog(onDismissRequest = { fullscreen = false }) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black).clickable { fullscreen = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        vm.photoStatus?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun sharePhoto(context: android.content.Context, vm: TranslateViewModel) {
    val uri = vm.sharePhotoUri() ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private fun newCaptureUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
