package dev.wergir.translate.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wergir.translate.R

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(8.dp))
        Image(
            painter = painterResource(R.drawable.wlogo),
            contentDescription = "Wergêr",
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Wergêr", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.about_made_by), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybergah.com")))
            }
        }) {
            Text(stringResource(R.string.about_website), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.about_opensource), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.about_thirdparty), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.about_copyright), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(stringResource(R.string.version, "1.0.0"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

        Spacer(Modifier.height(20.dp))
        Image(
            painter = painterResource(R.drawable.kurdistan_flag),
            contentDescription = "Ala Kurdistanê",
            modifier = Modifier.width(140.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.height(24.dp))
    }
}
