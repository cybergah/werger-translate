package dev.wergir.translate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.wergir.translate.R

/** One guide step — reused by both the Guide screen and the onboarding tutorial. */
data class GuideStep(val icon: String, val titleRes: Int, val bodyRes: Int)

val GUIDE_STEPS = listOf(
    GuideStep("👋", R.string.guide_welcome_title, R.string.guide_welcome_body),
    GuideStep("✍️", R.string.guide_text_title, R.string.guide_text_body),
    GuideStep("📷", R.string.guide_photo_title, R.string.guide_photo_body),
    GuideStep("📄", R.string.guide_doc_title, R.string.guide_doc_body),
    GuideStep("🌐", R.string.guide_modes_title, R.string.guide_modes_body),
    GuideStep("🔒", R.string.guide_privacy_title, R.string.guide_privacy_body),
)

/** Full user manual: every feature explained, plus a button to replay the tutorial. */
@Composable
fun GuideScreen(onReplayTutorial: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        GUIDE_STEPS.forEach { step ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(16.dp)) {
                    Text(step.icon, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            stringResource(step.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(step.bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(onClick = onReplayTutorial, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.guide_replay))
        }
        Spacer(Modifier.height(24.dp))
    }
}
