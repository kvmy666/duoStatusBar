package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kvmy666.duostatusbar.hook.IosBatteryState
import io.github.kvmy666.duostatusbar.hook.IosCanvasView
import io.github.kvmy666.duostatusbar.hook.IosCellMode
import io.github.kvmy666.duostatusbar.hook.IosCellRole
import io.github.kvmy666.duostatusbar.hook.IosGeometry
import io.github.kvmy666.duostatusbar.hook.IosIconKind
import io.github.kvmy666.duostatusbar.hook.IosMapping
import io.github.kvmy666.duostatusbar.hook.IosPart
import io.github.kvmy666.duostatusbar.hook.IosVisual
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import kotlinx.coroutines.delay

/** One frame of the live preview: both bar tints, both SIM layouts and one battery state. */
internal data class IosPreviewState(
    val dark: Boolean,
    /** One CELL view: SINGLE bars, or the DUAL `BOTH` stacked block. */
    val cell: IosVisual,
    val wifi: IosVisual,
    val battery: IosVisual
)

/**
 * Twelve real states (6 battery states x light/dark, alternating 1 SIM / 2 SIM). Pure so the UI test
 * can pin coverage without Compose; the composable only animates the index.
 *
 * The 2-SIM state is one `BOTH` cell view with the reference values (data SIM 2/4 bars, other SIM
 * 3/4 dots); the 1-SIM state is one `DATA` cell view with all four bars.
 */
internal fun iosPreviewStates(): List<IosPreviewState> {
    val states = ArrayList<IosPreviewState>(12)
    for (batteryIndex in 0 until 6) {
        for (simIndex in 0 until 2) {
            val dark = (batteryIndex + simIndex) % 2 == 1
            val tint = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val level = when (batteryIndex) {
                0 -> 100
                1 -> 15
                2 -> 60
                3 -> 60
                4 -> 60
                else -> 90
            }
            val saver = batteryIndex == 2 || batteryIndex == 4
            val plugged = batteryIndex >= 3
            val status = when (batteryIndex) {
                3, 4 -> 2 // BatteryManager.BATTERY_STATUS_CHARGING
                5 -> 4    // BATTERY_STATUS_NOT_CHARGING
                else -> 3 // BATTERY_STATUS_DISCHARGING
            }
            val battery = IosMapping.battery(
                level = level,
                lowThreshold = 20,
                saver = saver,
                plugged = plugged,
                status = status,
                tint = tint
            )
            val dual = simIndex == 1
            val cell = IosVisual(
                part = IosPart.CELL,
                tint = tint,
                cellMode = if (dual) IosCellMode.DUAL else IosCellMode.SINGLE,
                cellRole = if (dual) IosCellRole.BOTH else IosCellRole.DATA,
                cellBars = if (dual) 2f else 4f,
                cellDots = if (dual) 3f else 0f
            )
            val wifi = IosVisual(part = IosPart.WIFI, tint = tint, wifiLevel = 3f)
            states.add(
                IosPreviewState(
                    dark = dark,
                    cell = cell,
                    wifi = wifi,
                    battery = battery
                )
            )
        }
    }
    return states
}

/**
 * The live preview in the iOS card.
 *
 * It uses the same [IosCanvasView] (and therefore [IosGeometry]) as the status-bar fallback, so the
 * card shows the real constants without a second native Rive surface in the settings process. One CELL
 * view draws either the single bars or the dual `BOTH` stacked block, exactly as the runtime cluster
 * does; the background alternates light/dark and stays readable in both (m7).
 */
@Composable
internal fun IosPreviewPane(settings: DuoSettings = DuoSettings(), modifier: Modifier = Modifier) {
    val states = remember { iosPreviewStates() }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_200)
            index = (index + 1) % states.size
        }
    }
    val state = states[index]
    // Fits a 320 dp card (the strip is 603 design units wide plus the two gaps).
    val scale = 0.38f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                if (state.dark) Color(0xFF101014) else Color(0xFFF2F2F7),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // One CELL view at the reference scale: single bars, or the dual BOTH stacked block.
            Box(
                Modifier.size(
                    (IosGeometry.Cell.ARTBOARD_W * scale).dp,
                    (IosGeometry.Cell.ARTBOARD_H * scale).dp
                )
            ) {
                glyph(
                    IosIconKind.CELL,
                    state.cell,
                    IosGeometry.Cell.ARTBOARD_W,
                    IosGeometry.Cell.ARTBOARD_H,
                    scale,
                    settings.iosCellOffset.toFloat(),
                    settings.iosCellScale.toFloat()
                )
            }
            Spacer(Modifier.width(((IosGeometry.Strip.CELL_WIFI_GAP - 2f) * scale).dp))
            glyph(
                IosIconKind.WIFI,
                state.wifi,
                IosGeometry.Wifi.ARTBOARD_W,
                IosGeometry.Wifi.ARTBOARD_H,
                scale,
                settings.iosWifiOffset.toFloat(),
                settings.iosWifiScale.toFloat()
            )
            Spacer(Modifier.width(((IosGeometry.Strip.WIFI_BATTERY_GAP - 2f) * scale).dp))
            glyph(
                IosIconKind.BATTERY,
                state.battery,
                IosGeometry.Battery.ARTBOARD_W,
                IosGeometry.Battery.ARTBOARD_H,
                scale,
                settings.iosBatteryOffset.toFloat(),
                settings.iosBatteryScale.toFloat()
            )
        }
    }
}

@Composable
private fun glyph(
    kind: IosIconKind,
    visual: IosVisual,
    w: Float,
    h: Float,
    scale: Float,
    offsetPercent: Float,
    userScale: Float
) {
    AndroidView(
        factory = { context ->
            IosCanvasView(context, kind).also { it.start() }
        },
        update = { it.render(visual) },
        modifier = Modifier
            .size((w * scale).dp, (h * scale).dp)
            .offset(x = ((w * scale) * offsetPercent / 100f).dp)
            .graphicsLayer {
                scaleX = userScale / 100f
                scaleY = userScale / 100f
            }
    )
}
