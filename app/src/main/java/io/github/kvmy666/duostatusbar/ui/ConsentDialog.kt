package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.ConsentGate
import kotlinx.coroutines.delay

/**
 * The first-launch consent question for automatic problem reports.
 *
 * Default is off and nothing is stored until the user answers. Both buttons stay disabled for a visible
 * five-second countdown, because a dialog that can be dismissed in one tap is not consent.
 */
@Composable
internal fun ConsentDialog(onAnswer: (Boolean) -> Unit) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (elapsed < ConsentGate.COUNTDOWN_SECONDS) {
            delay(1_000)
            elapsed += 1
        }
    }
    val remaining = ConsentGate.remaining(elapsed)
    val enabled = ConsentGate.buttonsEnabled(elapsed)
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.consent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.consent_question))
                if (remaining > 0) {
                    Text(
                        stringResource(R.string.consent_countdown, remaining),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAnswer(true) },
                enabled = enabled,
                modifier = Modifier.testTag("consent_yes")
            ) {
                Text(stringResource(R.string.consent_yes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAnswer(false) },
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("consent_no")
            ) {
                Text(stringResource(R.string.consent_no))
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    )
}
