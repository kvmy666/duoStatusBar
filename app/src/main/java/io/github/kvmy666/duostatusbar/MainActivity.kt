package io.github.kvmy666.duostatusbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.kvmy666.duostatusbar.ui.DuoSettingsScreen
import io.github.kvmy666.duostatusbar.ui.DuoTheme

/**
 * The settings surface (Phase 5).
 *
 * Everything the user can change lives in [DuoSettingsScreen], and every change reaches the status bar over
 * the app ↔ module channel described in `settings/DuoPrefs.kt` — no restart, no root, no adb.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DuoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DuoSettingsScreen()
                }
            }
        }
    }
}
