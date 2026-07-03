package dev.wergir.translate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.wergir.translate.R

/**
 * First-run step-by-step tutorial shown over the app. Fully skippable at any
 * step; also replayable later from the Guide screen. Reuses [GUIDE_STEPS].
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val step = GUIDE_STEPS[index]
    val isLast = index == GUIDE_STEPS.lastIndex

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(step.icon, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))

                // Progress dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GUIDE_STEPS.forEachIndexed { i, _ ->
                        Box(
                            Modifier
                                .size(if (i == index) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(step.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(step.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.onboard_skip))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) {
                            TextButton(onClick = { index-- }) {
                                Text(stringResource(R.string.onboard_back))
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Button(onClick = { if (isLast) onFinish() else index++ }) {
                            Text(
                                stringResource(
                                    if (isLast) R.string.onboard_start else R.string.onboard_next,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
