package dev.wergir.translate.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wergir.translate.R
import kotlinx.coroutines.delay

/**
 * Animated intro shown for a few seconds on launch: Wergêr logo, the Kurdistan
 * flag and the Cybergah Group credit fade + scale in, then the app opens.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(900), label = "alpha")
    val scale by animateFloatAsState(if (visible) 1f else 0.82f, tween(900), label = "scale")

    LaunchedEffect(Unit) {
        visible = true
        delay(2600)
        onDone()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.wlogo),
                contentDescription = "Wergêr",
                modifier = Modifier.size(148.dp).scale(scale).alpha(alpha),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Wergêr",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.about_made_by),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.alpha(alpha),
            )
            Text(
                "cybergah.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}
