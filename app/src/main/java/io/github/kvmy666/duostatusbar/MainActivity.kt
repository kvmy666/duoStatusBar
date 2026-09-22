package io.github.kvmy666.duostatusbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        // Issue #3: without this the app's own status bar keeps white icons on a light background. The
        // default SystemBarStyle.auto makes the bar icons light/dark to match the theme (which follows the
        // system dark-mode setting), so they are black in light mode like the system's own bar.
        enableEdgeToEdge()
        setContent {
            DuoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DuoSettingsScreen()
                }
            }
        }
    }
}
