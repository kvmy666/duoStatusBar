package io.github.kvmy666.duostatusbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Phase 0 screen: a placeholder that proves the app builds and shows what to look for on the
 * device. The real, animate-everything settings UI is Phase 5 (FR-03/09/10/11/17).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DuoTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Phase0Screen()
                }
            }
        }
    }
}

/** Seed of the Material 3 "Red Wine" scheme (FR-11); the full tonal set lands in Phase 5. */
private val DuoTheme = lightColorScheme(
    primary = Color(0xFF7B1E3A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5E4149)
)

@Composable
private fun Phase0Screen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "Duo Status Bar", style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "The element (live Rive)", style = MaterialTheme.typography.titleMedium)
                DuoPreview()
                Text(
                    text = "This is the real duo.riv: the same file the status bar will render. " +
                        "It plays the 500 ms reveal on load — battery ring with the percentage in " +
                        "the gap, Wi-Fi layers, and the four cellular spheres.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Live status-bar integration", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "The module installs but stays gated OFF, so System UI is untouched until " +
                        "you ask for it. There is no toggle in this screen yet (Phase 5) — for now:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "adb shell settings put global duo_statusbar_stage 1   # icons, no Rive\n" +
                        "adb shell settings put global duo_statusbar_stage 2   # icons + Rive\n" +
                        "adb shell settings put global duo_statusbar_stage 0   # off",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(text = "Then read: adb logcat -s DuoSB", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
