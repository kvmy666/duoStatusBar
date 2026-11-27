package io.github.kvmy666.duostatusbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.kvmy666.duostatusbar.settings.AutoReportManager
import io.github.kvmy666.duostatusbar.settings.AutoReportStore
import io.github.kvmy666.duostatusbar.settings.ConsentGate
import io.github.kvmy666.duostatusbar.ui.ConsentDialog
import io.github.kvmy666.duostatusbar.ui.DuoShell
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
                    val context = LocalContext.current
                    // Shown only while the question is unanswered; a stored yes/no is never re-asked.
                    var showConsent by remember {
                        mutableStateOf(ConsentGate.shouldShow(AutoReportStore.consent(context)))
                    }
                    // The one automatic-report state: both the Settings switch and the Report switch
                    // read it, and the consent dialog writes it too, so the answer is visible at once.
                    var autoReport by remember {
                        mutableStateOf(ConsentGate.enabled(AutoReportStore.consent(context)))
                    }
                    fun setAutoReport(on: Boolean) {
                        AutoReportManager.answerConsent(context, on)
                        autoReport = on
                    }
                    DuoShell(
                        autoReport = autoReport,
                        onToggleAutoReport = ::setAutoReport
                    )
                    if (showConsent) {
                        ConsentDialog { allow ->
                            setAutoReport(allow)
                            showConsent = false
                        }
                    }
                }
            }
        }
    }
}
