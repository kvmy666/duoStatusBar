package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The automatic-report consent is one state owned by MainActivity. The Settings card and the Report
 * page each render the same switch against that one value, and the consent dialog answers through the
 * same setter. Toggling any writer must flip every visible switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class AutoReportSettingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `both switches and the dialog write the one automatic report state`() {
        compose.setContent {
            var autoReport by remember { mutableStateOf(false) }
            Column {
                // Two copies of the switch, as AboutSection and DuoReportScreen will show them.
                AutoReportSetting(autoReport = autoReport, onToggleAutoReport = { autoReport = it })
                AutoReportSetting(autoReport = autoReport, onToggleAutoReport = { autoReport = it })
                // A third writer standing in for the consent dialog's allow answer.
                Button(onClick = { autoReport = true }) { Text("dialog allow") }
            }
        }

        val switches = compose.onAllNodes(isToggleable())
        switches[0].assertIsOff()
        switches[1].assertIsOff()

        // Turning the first place on turns the second place on.
        switches[0].performClick()
        switches[0].assertIsOn()
        switches[1].assertIsOn()

        // Turning the second place off turns the first place off.
        switches[1].performClick()
        switches[0].assertIsOff()
        switches[1].assertIsOff()

        // A dialog-style answer flows to both switches through the shared state.
        compose.onNodeWithText("dialog allow").performClick()
        switches[0].assertIsOn()
        switches[1].assertIsOn()
    }
}
