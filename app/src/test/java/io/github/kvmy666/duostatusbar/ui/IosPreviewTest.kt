package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.hook.IosBatteryState
import io.github.kvmy666.duostatusbar.hook.IosCellMode
import io.github.kvmy666.duostatusbar.hook.IosCellRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live preview is data, not a screenshot: pin that it cycles every battery state, both SIM
 * layouts and both bar tints, and that the dual frame matches the owner's dark reference values.
 */
class IosPreviewTest {

    @Test
    fun `preview has twelve frames covering both tints and all battery states`() {
        val states = iosPreviewStates()
        assertEquals(12, states.size)
        assertTrue(states.any { it.dark })
        assertTrue(states.any { !it.dark })
        assertEquals(
            IosBatteryState.values().toSet(),
            states.map { it.battery.batteryState }.toSet()
        )
        val tints = states.map { it.battery.tint }.toSet()
        assertEquals(setOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), tints)
    }

    @Test
    fun `every frame carries the three glyphs on the frame tint`() {
        for (state in iosPreviewStates()) {
            assertTrue(state.cell.visible)
            assertTrue(state.wifi.visible)
            assertTrue(state.battery.visible)
            assertEquals(state.battery.tint, state.cell.tint)
            assertEquals(state.battery.tint, state.wifi.tint)
            assertEquals(3f, state.wifi.wifiLevel, 0.0001f)
        }
    }

    @Test
    fun `both SIM layouts appear and the dual frame matches the reference`() {
        val states = iosPreviewStates()
        assertTrue(states.any { it.cell.cellMode == IosCellMode.SINGLE })
        assertTrue(states.any { it.cell.cellMode == IosCellMode.DUAL })
        for (state in states) {
            when (state.cell.cellMode) {
                IosCellMode.SINGLE -> {
                    assertEquals(IosCellRole.DATA, state.cell.cellRole)
                    assertEquals(4f, state.cell.cellBars, 0.0001f)
                    assertEquals(0f, state.cell.cellDots, 0.0001f)
                }
                IosCellMode.DUAL -> {
                    assertEquals(IosCellRole.BOTH, state.cell.cellRole)
                    assertEquals(2f, state.cell.cellBars, 0.0001f)
                    assertEquals(3f, state.cell.cellDots, 0.0001f)
                }
            }
        }
    }

    @Test
    fun `charging frames carry green or yellow fills and a bolt`() {
        for (state in iosPreviewStates()) {
            when (state.battery.batteryState) {
                IosBatteryState.CHARGING -> {
                    assertEquals(0xFF34C759.toInt(), state.battery.batteryFillColor)
                    assertEquals(1f, state.battery.boltOpacity, 0.0001f)
                }
                IosBatteryState.CHARGING_SAVER -> {
                    assertEquals(0xFFFFCC00.toInt(), state.battery.batteryFillColor)
                    assertEquals(1f, state.battery.boltOpacity, 0.0001f)
                }
                IosBatteryState.PLUGGED_NOT_CHARGING -> {
                    assertEquals(0xFF8E8E93.toInt(), state.battery.batteryFillColor)
                    assertEquals(1f, state.battery.boltOpacity, 0.0001f)
                }
                IosBatteryState.LOW -> {
                    assertEquals(0xFFFF3B30.toInt(), state.battery.batteryFillColor)
                    assertEquals(0f, state.battery.boltOpacity, 0.0001f)
                }
                else -> Unit
            }
        }
    }
}
